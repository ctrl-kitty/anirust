use super::CatalogService;
use crate::domain::{
    Anime, AnimeId, Episode, PlayerKind, ProviderCapabilities, ProviderError, ProviderId,
    ProviderResult, ProviderStatus, SeriesEntry,
};
use crate::providers::{AnimeProvider, MetadataProvider};
use async_trait::async_trait;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Arc;

struct FakeProvider {
    id: ProviderId,
    search_result: ProviderResult<Vec<Anime>>,
    series_result: ProviderResult<Vec<SeriesEntry>>,
    episodes_result: ProviderResult<Vec<Episode>>,
}

struct CountingMetadataProvider {
    id: ProviderId,
    search_calls: Arc<AtomicUsize>,
}

impl FakeProvider {
    fn new(id: &str) -> Self {
        Self {
            id: ProviderId::from(id),
            search_result: ProviderResult::not_found(),
            series_result: ProviderResult::not_found(),
            episodes_result: ProviderResult::not_found(),
        }
    }

    fn with_search(mut self, result: ProviderResult<Vec<Anime>>) -> Self {
        self.search_result = result;
        self
    }

    fn with_series(mut self, result: ProviderResult<Vec<SeriesEntry>>) -> Self {
        self.series_result = result;
        self
    }

    fn with_episodes(mut self, result: ProviderResult<Vec<Episode>>) -> Self {
        self.episodes_result = result;
        self
    }
}

impl CountingMetadataProvider {
    fn new(search_calls: Arc<AtomicUsize>) -> Self {
        Self {
            id: ProviderId::from("metadata"),
            search_calls,
        }
    }
}

#[async_trait]
impl AnimeProvider for FakeProvider {
    fn id(&self) -> ProviderId {
        self.id.clone()
    }

    fn capabilities(&self) -> ProviderCapabilities {
        ProviderCapabilities::new(true, true, true)
    }

    async fn search(&self, _query: &str) -> ProviderResult<Vec<Anime>> {
        clone_result(&self.search_result)
    }

    async fn series(&self, _anime_id: &AnimeId) -> ProviderResult<Vec<SeriesEntry>> {
        clone_result(&self.series_result)
    }

    async fn episodes(&self, _series_id: &str) -> ProviderResult<Vec<Episode>> {
        clone_result(&self.episodes_result)
    }
}

#[async_trait]
impl MetadataProvider for FakeProvider {
    fn id(&self) -> ProviderId {
        self.id.clone()
    }

    async fn search(&self, _query: &str) -> ProviderResult<Vec<Anime>> {
        clone_result(&self.search_result)
    }
}

#[async_trait]
impl MetadataProvider for CountingMetadataProvider {
    fn id(&self) -> ProviderId {
        self.id.clone()
    }

    async fn search(&self, _query: &str) -> ProviderResult<Vec<Anime>> {
        self.search_calls.fetch_add(1, Ordering::SeqCst);
        ProviderResult::ok(Vec::new())
    }
}

fn clone_result<T: Clone>(result: &ProviderResult<T>) -> ProviderResult<T> {
    ProviderResult {
        status: result.status,
        data: result.data.clone(),
        error: result.error.clone(),
    }
}

fn sample_anime(id: AnimeId, title: &str, source: &str) -> Anime {
    Anime {
        id,
        title: title.to_string(),
        alt_titles: Vec::new(),
        synopsis: None,
        poster_url: None,
        source: Some(ProviderId::from(source)),
    }
}

fn sample_series(id: &str, order: Option<u32>) -> SeriesEntry {
    SeriesEntry {
        id: id.to_string(),
        title: format!("Series {id}"),
        order,
        provider: ProviderId::from("yummy"),
    }
}

fn sample_episode(id: &str, number: Option<u32>, kind: PlayerKind) -> Episode {
    Episode {
        id: id.to_string(),
        number,
        title: None,
        iframe_url: None,
        voice_variants: Vec::new(),
        subtitle_variants: Vec::new(),
        player_kind: kind,
        provider: ProviderId::from("yummy"),
    }
}

#[tokio::test]
async fn search_merges_metadata_by_id() {
    let yummy_anime = sample_anime(
        AnimeId {
            shikimori_id: Some(10),
            mal_id: Some(20),
            yummy_id: Some(30),
        },
        "Yummy Title",
        "yummy",
    );
    let mut shiki_anime = sample_anime(
        AnimeId {
            shikimori_id: Some(10),
            mal_id: Some(20),
            yummy_id: None,
        },
        "Metadata Title",
        "shikimori",
    );
    shiki_anime.synopsis = Some("Metadata Synopsis".to_string());

    let anime_provider = FakeProvider::new("yummy")
        .with_search(ProviderResult::ok(vec![yummy_anime]));
    let metadata_provider = FakeProvider::new("shikimori")
        .with_search(ProviderResult::ok(vec![shiki_anime]));
    let service = CatalogService::new(&anime_provider, Some(&metadata_provider));

    let result = service.search("naruto").await;
    assert_eq!(result.status, ProviderStatus::Ok);
    let data = result.data.expect("search data");
    assert_eq!(data.len(), 1);

    let merged = &data[0];
    assert_eq!(merged.id.shikimori_id, Some(10));
    assert_eq!(merged.id.mal_id, Some(20));
    assert_eq!(merged.id.yummy_id, Some(30));
    assert_eq!(merged.title, "Metadata Title");
    assert_eq!(merged.synopsis.as_deref(), Some("Metadata Synopsis"));
}

#[tokio::test]
async fn search_without_metadata_provider_works() {
    let anime_provider = FakeProvider::new("yummy").with_search(ProviderResult::ok(vec![
        sample_anime(
            AnimeId {
                shikimori_id: Some(99),
                mal_id: None,
                yummy_id: Some(77),
            },
            "Solo Result",
            "yummy",
        ),
    ]));
    let service = CatalogService::new(&anime_provider, None);

    let result = service.search("solo").await;
    assert_eq!(result.status, ProviderStatus::Ok);
    let data = result.data.expect("search data");
    assert_eq!(data.len(), 1);
    assert_eq!(data[0].title, "Solo Result");
}

#[tokio::test]
async fn search_returns_partial_when_metadata_fails() {
    let yummy_anime = sample_anime(
        AnimeId {
            shikimori_id: Some(1),
            mal_id: None,
            yummy_id: Some(2),
        },
        "Yummy Title",
        "yummy",
    );
    let anime_provider = FakeProvider::new("yummy")
        .with_search(ProviderResult::ok(vec![yummy_anime]));
    let metadata_provider = FakeProvider::new("shikimori").with_search(ProviderResult::error(
        ProviderError::new("metadata down", true),
    ));
    let service = CatalogService::new(&anime_provider, Some(&metadata_provider));

    let result = service.search("naruto").await;
    assert_eq!(result.status, ProviderStatus::Partial);
    let data = result.data.expect("search data");
    assert_eq!(data.len(), 1);
    assert_eq!(
        result.error.as_ref().map(|error| error.message.as_str()),
        Some("metadata down")
    );
}

#[tokio::test]
async fn search_sort_prefers_exact_match() {
    let items = vec![
        sample_anime(
            AnimeId::default(),
            "Наруто: Ураганные хроники",
            "yummy",
        ),
        sample_anime(AnimeId::default(), "Наруто", "yummy"),
        sample_anime(AnimeId::default(), "Боруто", "yummy"),
    ];
    let anime_provider = FakeProvider::new("yummy").with_search(ProviderResult::ok(items));
    let service = CatalogService::new(&anime_provider, None);

    let result = service.search("Наруто").await;
    let data = result.data.expect("search data");
    assert_eq!(data.first().map(|anime| anime.title.as_str()), Some("Наруто"));
}

#[tokio::test]
async fn series_sorts_by_order_when_present() {
    let series = vec![sample_series("b", Some(2)), sample_series("c", None), sample_series("a", Some(1))];
    let anime_provider = FakeProvider::new("yummy")
        .with_series(ProviderResult::ok(series));
    let service = CatalogService::new(&anime_provider, None);

    let result = service.series(&AnimeId::default()).await;
    let data = result.data.expect("series data");
    let ids: Vec<&str> = data.iter().map(|entry| entry.id.as_str()).collect();
    assert_eq!(ids, vec!["a", "b", "c"]);
}

#[tokio::test]
async fn episodes_filters_unsupported_players() {
    let episodes = vec![
        sample_episode("k", Some(1), PlayerKind::Kodik),
        sample_episode("a", Some(2), PlayerKind::Alloha),
        sample_episode("d", Some(3), PlayerKind::Direct),
        sample_episode("u", Some(4), PlayerKind::Unknown),
    ];
    let anime_provider = FakeProvider::new("yummy")
        .with_episodes(ProviderResult::ok(episodes));
    let service = CatalogService::new(&anime_provider, None);

    let result = service.episodes("series-id").await;
    let data = result.data.expect("episodes data");
    let ids: Vec<&str> = data.iter().map(|episode| episode.id.as_str()).collect();
    assert_eq!(ids, vec!["k", "d"]);
}

#[tokio::test]
async fn episodes_sort_by_number_when_present() {
    let episodes = vec![
        sample_episode("e3", Some(3), PlayerKind::Kodik),
        sample_episode("e1", Some(1), PlayerKind::Kodik),
        sample_episode("eX", None, PlayerKind::Kodik),
        sample_episode("e2", Some(2), PlayerKind::Kodik),
    ];
    let anime_provider = FakeProvider::new("yummy")
        .with_episodes(ProviderResult::ok(episodes));
    let service = CatalogService::new(&anime_provider, None);

    let result = service.episodes("series-id").await;
    let data = result.data.expect("episodes data");
    let ids: Vec<&str> = data.iter().map(|episode| episode.id.as_str()).collect();
    assert_eq!(ids, vec!["e1", "e2", "e3", "eX"]);
}

#[tokio::test]
async fn metadata_provider_not_called_for_series_or_episodes() {
    let search_calls = Arc::new(AtomicUsize::new(0));
    let metadata_provider = CountingMetadataProvider::new(search_calls.clone());
    let anime_provider = FakeProvider::new("yummy")
        .with_series(ProviderResult::ok(vec![sample_series("a", Some(1))]))
        .with_episodes(ProviderResult::ok(vec![sample_episode(
            "e1",
            Some(1),
            PlayerKind::Kodik,
        )]));
    let service = CatalogService::new(&anime_provider, Some(&metadata_provider));

    let _ = service.series(&AnimeId::default()).await;
    let _ = service.episodes("series-id").await;

    assert_eq!(search_calls.load(Ordering::SeqCst), 0);
}
