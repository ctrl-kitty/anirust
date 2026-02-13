use async_trait::async_trait;
use serde::de::DeserializeOwned;
use serde::Deserialize;
use url::Url;

use crate::domain::{
    Anime, AnimeId, Episode, ProviderCapabilities, ProviderError, ProviderId, ProviderResult,
    PlayerKind, SeriesEntry, VoiceVariant,
};
use crate::providers::AnimeProvider;
use crate::registry::ProviderFactory;
use crate::settings;

const YUMMY_BASE_URL: &str = "https://api.yani.tv";

pub struct YummyProvider {
    client: reqwest::Client,
}

impl YummyProvider {
    pub fn new() -> Self {
        Self {
            client: reqwest::Client::new(),
        }
    }

    async fn search_anime(&self, query: &str) -> ProviderResult<Vec<Anime>> {
        if query.trim().is_empty() {
            return ProviderResult::not_found();
        }

        let config = match load_yummy_config() {
            Ok(config) => config,
            Err(ConfigError::LoadFailed(error)) => return ProviderResult::error(error),
        };

        let request = self
            .client
            .get(format!("{}/anime", YUMMY_BASE_URL))
            .query(&[("q", query)]);
        let request = apply_headers(request, &config);

        let payload = match self.send_request::<Vec<YummyAnime>>(request).await {
            Ok(payload) => payload,
            Err(err) => return err.into_result(),
        };

        let items: Vec<Anime> = payload
            .response
            .into_iter()
            .map(|anime| anime.into_anime())
            .collect();

        if items.is_empty() {
            return ProviderResult::not_found();
        }

        ProviderResult::ok(items)
    }

    async fn series_for_anime(&self, anime_id: u64) -> ProviderResult<Vec<SeriesEntry>> {
        let config = match load_yummy_config() {
            Ok(config) => config,
            Err(ConfigError::LoadFailed(error)) => return ProviderResult::error(error),
        };

        let request = self
            .client
            .get(format!("{}/anime/{}", YUMMY_BASE_URL, anime_id));
        let request = apply_headers(request, &config);

        let payload = match self.send_request::<YummyAnimeDetail>(request).await {
            Ok(payload) => payload,
            Err(err) => return err.into_result(),
        };

        let entries = payload.response.into_series_entries();
        if entries.is_empty() {
            return ProviderResult::not_found();
        }

        ProviderResult::ok(entries)
    }

    async fn episodes_for_anime(&self, anime_id: u64) -> ProviderResult<Vec<Episode>> {
        let config = match load_yummy_config() {
            Ok(config) => config,
            Err(ConfigError::LoadFailed(error)) => return ProviderResult::error(error),
        };

        let request = self
            .client
            .get(format!("{}/anime/{}/videos", YUMMY_BASE_URL, anime_id));
        let request = apply_headers(request, &config);

        let payload = match self.send_request::<Vec<YummyVideo>>(request).await {
            Ok(payload) => payload,
            Err(err) => return err.into_result(),
        };

        let episodes: Vec<Episode> = payload
            .response
            .into_iter()
            .map(|video| video.into_episode())
            .collect();

        if episodes.is_empty() {
            return ProviderResult::not_found();
        }

        ProviderResult::ok(episodes)
    }

    async fn send_request<T: DeserializeOwned>(
        &self,
        request: reqwest::RequestBuilder,
    ) -> Result<YummyResponse<T>, RequestError> {
        let response = match request.send().await {
            Ok(response) => response,
            Err(err) => return Err(RequestError::Other(map_reqwest_error(err))),
        };

        let status = response.status();
        if status == reqwest::StatusCode::TOO_MANY_REQUESTS {
            return Err(RequestError::RateLimited(ProviderError::new(
                "rate limited by yummy",
                true,
            )));
        }

        if status == reqwest::StatusCode::UNAUTHORIZED || status == reqwest::StatusCode::FORBIDDEN
        {
            return Err(RequestError::Unauthorized(ProviderError::new(
                "unauthorized by yummy",
                false,
            )));
        }

        if !status.is_success() {
            let retryable = status.is_server_error();
            return Err(RequestError::Other(ProviderError::new(
                format!("http error from yummy: {}", status),
                retryable,
            )));
        }

        let payload = match response.json::<YummyResponse<T>>().await {
            Ok(payload) => payload,
            Err(err) => return Err(RequestError::Other(map_reqwest_error(err))),
        };

        Ok(payload)
    }
}

enum RequestError {
    RateLimited(ProviderError),
    Unauthorized(ProviderError),
    Other(ProviderError),
}

impl RequestError {
    fn into_result<T>(self) -> ProviderResult<T> {
        match self {
            RequestError::RateLimited(error) => ProviderResult::rate_limited(error),
            RequestError::Unauthorized(error) => ProviderResult::unauthorized(error),
            RequestError::Other(error) => ProviderResult::error(error),
        }
    }
}

#[async_trait]
impl AnimeProvider for YummyProvider {
    fn id(&self) -> ProviderId {
        ProviderId::from("yummy")
    }

    fn capabilities(&self) -> ProviderCapabilities {
        ProviderCapabilities::new(true, true, true)
    }

    async fn search(&self, query: &str) -> ProviderResult<Vec<Anime>> {
        self.search_anime(query).await
    }

    async fn series(&self, anime_id: &AnimeId) -> ProviderResult<Vec<SeriesEntry>> {
        let yummy_id = match anime_id.yummy_id {
            Some(id) => id,
            None => {
                return ProviderResult::not_found();
            }
        };

        self.series_for_anime(yummy_id).await
    }

    async fn episodes(&self, series_id: &str) -> ProviderResult<Vec<Episode>> {
        let anime_id = match series_id.parse::<u64>() {
            Ok(id) => id,
            Err(_) => {
                return ProviderResult::error(ProviderError::new(
                    "invalid series id for yummy",
                    false,
                ))
            }
        };

        self.episodes_for_anime(anime_id).await
    }
}

#[derive(Debug, Deserialize)]
struct YummyResponse<T> {
    response: T,
}

#[derive(Debug, Deserialize)]
struct YummyAnime {
    title: String,
    description: Option<String>,
    #[serde(rename = "anime_id")]
    anime_id: u64,
    poster: Option<Poster>,
    #[serde(rename = "remote_ids")]
    remote_ids: Option<RemoteIds>,
}

impl YummyAnime {
    fn into_anime(self) -> Anime {
        let (shikimori_id, mal_id) = match self.remote_ids {
            Some(remote) => (
                normalize_id(remote.shikimori_id),
                normalize_id(remote.myanimelist_id),
            ),
            None => (None, None),
        };

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
                yummy_id: normalize_id(Some(self.anime_id)),
            },
            title: self.title,
            alt_titles: Vec::new(),
            synopsis,
            poster_url,
            source: Some(ProviderId::from("yummy")),
        }
    }
}

#[derive(Debug, Deserialize)]
struct YummyAnimeDetail {
    title: String,
    #[serde(rename = "anime_id")]
    anime_id: u64,
    #[serde(default)]
    viewing_order: Option<Vec<YummySeriesEntry>>,
}

impl YummyAnimeDetail {
    fn into_series_entries(self) -> Vec<SeriesEntry> {
        let provider = ProviderId::from("yummy");

        if let Some(order) = self.viewing_order {
            let entries: Vec<SeriesEntry> = order
                .into_iter()
                .filter_map(|entry| entry.into_series_entry())
                .collect();
            if !entries.is_empty() {
                return entries;
            }
        }

        let id = match normalize_id(Some(self.anime_id)) {
            Some(id) => id.to_string(),
            None => return Vec::new(),
        };

        vec![SeriesEntry {
            id,
            title: self.title,
            order: None,
            provider,
        }]
    }
}

#[derive(Debug, Deserialize)]
struct YummySeriesEntry {
    #[serde(rename = "anime_id")]
    anime_id: u64,
    title: String,
    data: Option<YummySeriesData>,
}

impl YummySeriesEntry {
    fn into_series_entry(self) -> Option<SeriesEntry> {
        let id = normalize_id(Some(self.anime_id))?.to_string();
        let order = self.data.and_then(|data| data.index);
        Some(SeriesEntry {
            id,
            title: self.title,
            order,
            provider: ProviderId::from("yummy"),
        })
    }
}

#[derive(Debug, Deserialize)]
struct YummySeriesData {
    index: Option<u32>,
}

#[derive(Debug, Deserialize)]
struct YummyVideo {
    #[serde(rename = "video_id")]
    video_id: u64,
    number: Option<String>,
    #[serde(alias = "name", alias = "episode_name", alias = "episode_title", alias = "episodeTitle")]
    title: Option<String>,
    #[serde(rename = "iframe_url")]
    iframe_url: Option<String>,
    data: Option<YummyVideoData>,
}

impl YummyVideo {
    fn into_episode(self) -> Episode {
        let number = self
            .number
            .as_deref()
            .and_then(|value| value.parse::<u32>().ok());
        let title = self.title.and_then(normalized_text);
        let iframe_url = self
            .iframe_url
            .as_deref()
            .and_then(parse_url);
        let player_kind = iframe_url
            .as_ref()
            .map(PlayerKind::from_url)
            .unwrap_or(PlayerKind::Unknown);
        let data = self.data;
        let title = title.or_else(|| {
            data.as_ref()
                .and_then(|value| value.title.as_ref())
                .cloned()
                .and_then(normalized_text)
        });
        let voice_variants = data
            .and_then(|data| normalized_text(data.dubbing))
            .map(|label| {
                vec![VoiceVariant {
                    id: label.clone(),
                    label,
                    lang: None,
                    provider: ProviderId::from("yummy"),
                }]
            })
            .unwrap_or_default();

        Episode {
            id: self.video_id.to_string(),
            number,
            title,
            iframe_url,
            voice_variants,
            subtitle_variants: Vec::new(),
            player_kind,
            provider: ProviderId::from("yummy"),
        }
    }
}

#[derive(Debug, Deserialize)]
struct YummyVideoData {
    dubbing: String,
    #[serde(alias = "name", alias = "episode_name", alias = "episode_title", alias = "episodeTitle")]
    title: Option<String>,
}

#[derive(Debug, Deserialize)]
struct Poster {
    fullsize: Option<String>,
    big: Option<String>,
    medium: Option<String>,
    small: Option<String>,
    huge: Option<String>,
}

impl Poster {
    fn best_url(&self) -> Option<&str> {
        self.fullsize
            .as_deref()
            .or(self.huge.as_deref())
            .or(self.big.as_deref())
            .or(self.medium.as_deref())
            .or(self.small.as_deref())
    }
}

#[derive(Debug, Deserialize)]
struct RemoteIds {
    #[serde(rename = "shikimori_id")]
    shikimori_id: Option<u64>,
    #[serde(rename = "myanimelist_id")]
    myanimelist_id: Option<u64>,
}

#[derive(Debug)]
struct YummyConfig {
    app_token: Option<String>,
    lang: String,
}

#[derive(Debug)]
enum ConfigError {
    LoadFailed(ProviderError),
}

fn load_yummy_config() -> Result<YummyConfig, ConfigError> {
    if let Err(err) = settings::ensure_config() {
        return Err(ConfigError::LoadFailed(ProviderError::new(
            err.to_string(),
            false,
        )));
    }

    let settings = settings::Settings::load().map_err(|err| {
        ConfigError::LoadFailed(ProviderError::new(err.to_string(), false))
    })?;

    let app_token = settings.yummy.app_token.trim();
    let app_token = if app_token.is_empty() {
        None
    } else {
        Some(app_token.to_string())
    };

    let lang = settings.yummy.lang.trim();
    let lang = if lang.is_empty() {
        "ru".to_string()
    } else {
        lang.to_string()
    };

    Ok(YummyConfig { app_token, lang })
}

fn apply_headers(
    builder: reqwest::RequestBuilder,
    config: &YummyConfig,
) -> reqwest::RequestBuilder {
    let builder = builder.header(reqwest::header::ACCEPT_LANGUAGE, config.lang.as_str());
    if let Some(token) = config.app_token.as_ref() {
        builder.header("X-Application", token.as_str())
    } else {
        builder
    }
}

fn normalize_id(value: Option<u64>) -> Option<u64> {
    match value {
        Some(0) | None => None,
        Some(id) => Some(id),
    }
}

fn normalized_text(value: String) -> Option<String> {
    let trimmed = value.trim();
    if trimmed.is_empty() {
        None
    } else {
        Some(trimmed.to_string())
    }
}

fn parse_url(value: &str) -> Option<Url> {
    Url::parse(value)
        .ok()
        .or_else(|| Url::parse(&format!("https:{}", value)).ok())
}

fn map_reqwest_error(error: reqwest::Error) -> ProviderError {
    ProviderError::new(error.to_string(), error.is_timeout() || error.is_connect())
}

inventory::submit! {
    ProviderFactory {
        id: "yummy",
        build: || Box::new(YummyProvider::new()),
    }
}
