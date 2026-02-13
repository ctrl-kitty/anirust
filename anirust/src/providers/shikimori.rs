use async_trait::async_trait;
use serde::{Deserialize, Serialize};

use crate::domain::{Anime, AnimeId, ProviderError, ProviderId, ProviderResult};
use crate::providers::utils::{map_reqwest_error, normalized_text, parse_url};
use crate::providers::MetadataProvider;
use crate::registry::MetadataProviderFactory;

const SHIKIMORI_GRAPHQL_URL: &str = "https://shiki.one/api/graphql";
const DEFAULT_LIMIT: i32 = 20;
const SEARCH_QUERY: &str = r#"
query SearchAnime($search: String!, $limit: Int!) {
  animes(search: $search, limit: $limit) {
    id
    malId
    name
    russian
    english
    synonyms
    description
    poster {
      originalUrl
      mainUrl
      previewUrl
    }
  }
}
"#;

pub struct ShikimoriProvider {
    client: reqwest::Client,
}

impl ShikimoriProvider {
    pub fn new() -> Self {
        Self {
            client: reqwest::Client::new(),
        }
    }

    async fn search_anime(&self, query: &str, limit: i32) -> ProviderResult<Vec<Anime>> {
        if query.trim().is_empty() {
            return ProviderResult::not_found();
        }

        let request = GraphQlRequest {
            query: SEARCH_QUERY,
            variables: GraphQlVariables { search: query, limit },
        };

        let response = match self
            .client
            .post(SHIKIMORI_GRAPHQL_URL)
            .json(&request)
            .send()
            .await
        {
            Ok(response) => response,
            Err(err) => return ProviderResult::error(map_reqwest_error(err)),
        };

        let status = response.status();
        if status == reqwest::StatusCode::TOO_MANY_REQUESTS {
            return ProviderResult::rate_limited(ProviderError::new(
                "rate limited by shikimori",
                true,
            ));
        }

        if status == reqwest::StatusCode::UNAUTHORIZED || status == reqwest::StatusCode::FORBIDDEN
        {
            return ProviderResult::unauthorized(ProviderError::new(
                "unauthorized by shikimori",
                false,
            ));
        }

        if !status.is_success() {
            let retryable = status.is_server_error();
            return ProviderResult::error(ProviderError::new(
                format!("http error from shikimori: {}", status),
                retryable,
            ));
        }

        let payload: GraphQlResponse<SearchData> = match response.json().await {
            Ok(payload) => payload,
            Err(err) => return ProviderResult::error(map_reqwest_error(err)),
        };

        let errors = payload.errors.unwrap_or_default();
        let data = match payload.data {
            Some(data) => data,
            None => {
                if errors.is_empty() {
                    return ProviderResult::error(ProviderError::new(
                        "missing data in graphql response",
                        false,
                    ));
                }

                return ProviderResult::error(graphql_error(errors));
            }
        };

        let items: Vec<Anime> = data
            .animes
            .into_iter()
            .map(|anime| anime.into_anime())
            .collect();

        if !errors.is_empty() {
            return ProviderResult::partial(items, graphql_error(errors));
        }

        if items.is_empty() {
            return ProviderResult::not_found();
        }

        ProviderResult::ok(items)
    }
}

#[async_trait]
impl MetadataProvider for ShikimoriProvider {
    fn id(&self) -> ProviderId {
        ProviderId::from("shikimori")
    }

    async fn search(&self, query: &str) -> ProviderResult<Vec<Anime>> {
        self.search_anime(query, DEFAULT_LIMIT).await
    }
}

#[derive(Debug, Serialize)]
struct GraphQlRequest<'a> {
    query: &'a str,
    variables: GraphQlVariables<'a>,
}

#[derive(Debug, Serialize)]
struct GraphQlVariables<'a> {
    search: &'a str,
    limit: i32,
}

#[derive(Debug, Deserialize)]
struct GraphQlResponse<T> {
    data: Option<T>,
    #[serde(default)]
    errors: Option<Vec<GraphQlError>>,
}

#[derive(Debug, Deserialize)]
struct GraphQlError {
    message: String,
}

#[derive(Debug, Deserialize)]
struct SearchData {
    #[serde(default)]
    animes: Vec<AnimeNode>,
}

#[derive(Debug, Deserialize)]
struct AnimeNode {
    id: IdValue,
    #[serde(rename = "malId")]
    mal_id: Option<IdValue>,
    name: Option<String>,
    russian: Option<String>,
    english: Option<StringOrVec>,
    #[serde(default)]
    synonyms: Vec<String>,
    description: Option<String>,
    poster: Option<Poster>,
}

impl AnimeNode {
    fn into_anime(self) -> Anime {
        let shikimori_id = self.id.to_u64();
        let mal_id = self.mal_id.as_ref().and_then(|id| id.to_u64());
        let title = pick_primary_title(&self);
        let alt_titles = collect_alt_titles(&self, &title);
        let poster_url = self
            .poster
            .as_ref()
            .and_then(|poster| poster.best_url())
            .and_then(parse_url);
        let synopsis = self
            .description
            .and_then(|value| normalized_text(value));

        Anime {
            id: AnimeId {
                shikimori_id,
                mal_id,
                yummy_id: None,
            },
            title,
            alt_titles,
            synopsis,
            poster_url,
            source: Some(ProviderId::from("shikimori")),
        }
    }
}

#[derive(Debug, Deserialize)]
struct Poster {
    #[serde(rename = "originalUrl")]
    original_url: Option<String>,
    #[serde(rename = "mainUrl")]
    main_url: Option<String>,
    #[serde(rename = "previewUrl")]
    preview_url: Option<String>,
}

impl Poster {
    fn best_url(&self) -> Option<&str> {
        self.original_url
            .as_deref()
            .or(self.main_url.as_deref())
            .or(self.preview_url.as_deref())
    }
}

#[derive(Debug, Deserialize)]
#[serde(untagged)]
enum IdValue {
    String(String),
    Number(u64),
}

impl IdValue {
    fn to_u64(&self) -> Option<u64> {
        match self {
            Self::String(value) => value.parse::<u64>().ok(),
            Self::Number(value) => Some(*value),
        }
    }
}

#[derive(Debug, Deserialize, Clone)]
#[serde(untagged)]
enum StringOrVec {
    String(String),
    Vec(Vec<String>),
}

impl StringOrVec {
    fn into_vec(self) -> Vec<String> {
        match self {
            Self::String(value) => vec![value],
            Self::Vec(values) => values,
        }
    }
}

fn pick_primary_title(anime: &AnimeNode) -> String {
    if let Some(value) = anime
        .russian
        .as_ref()
        .and_then(|value| normalized_text(value.clone()))
    {
        return value;
    }

    if let Some(value) = anime
        .name
        .as_ref()
        .and_then(|value| normalized_text(value.clone()))
    {
        return value;
    }

    if let Some(english) = anime.english.as_ref() {
        for value in english.clone().into_vec() {
            if let Some(value) = normalized_text(value) {
                return value;
            }
        }
    }

    "Unknown title".to_string()
}

fn collect_alt_titles(anime: &AnimeNode, primary: &str) -> Vec<String> {
    let mut titles = Vec::new();

    if let Some(value) = anime.name.as_ref().cloned().and_then(normalized_text) {
        push_unique(&mut titles, primary, value);
    }

    if let Some(value) = anime.russian.as_ref().cloned().and_then(normalized_text) {
        push_unique(&mut titles, primary, value);
    }

    if let Some(english) = anime.english.as_ref() {
        for value in english.clone().into_vec() {
            if let Some(value) = normalized_text(value) {
                push_unique(&mut titles, primary, value);
            }
        }
    }

    for value in &anime.synonyms {
        if let Some(value) = normalized_text(value.clone()) {
            push_unique(&mut titles, primary, value);
        }
    }

    titles
}

fn push_unique(titles: &mut Vec<String>, primary: &str, value: String) {
    if value == primary {
        return;
    }

    if !titles.iter().any(|existing| existing == &value) {
        titles.push(value);
    }
}

fn graphql_error(errors: Vec<GraphQlError>) -> ProviderError {
    let message = errors
        .into_iter()
        .map(|error| error.message)
        .collect::<Vec<_>>()
        .join("; ");
    ProviderError::new(message, false)
}

inventory::submit! {
    MetadataProviderFactory {
        id: "shikimori",
        build: || Box::new(ShikimoriProvider::new()),
    }
}
