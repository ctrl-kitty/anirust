use std::path::PathBuf;
use std::sync::{Mutex, MutexGuard, OnceLock};
use std::time::{SystemTime, UNIX_EPOCH};

use async_trait::async_trait;

use crate::domain::{
    Anime, AnimeId, Episode, PlayerKind, ProviderCapabilities, ProviderId, ProviderResult,
    SeriesEntry,
};
use crate::player::{PlayerResolver, ResolvedMedia};
use crate::providers::{AnimeProvider, MetadataProvider};

pub(crate) fn env_lock() -> MutexGuard<'static, ()> {
    static LOCK: OnceLock<Mutex<()>> = OnceLock::new();
    LOCK.get_or_init(|| Mutex::new(()))
        .lock()
        .unwrap_or_else(|err| err.into_inner())
}

pub(crate) fn setup_temp_config() -> PathBuf {
    let mut dir = std::env::temp_dir();
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_nanos();
    dir.push(format!("anirust-test-{}-{}", std::process::id(), now));
    std::fs::create_dir_all(&dir).expect("create temp config dir");
    std::env::set_var("XDG_CONFIG_HOME", &dir);
    dir
}

pub(crate) struct FakeAnimeProvider {
    id: ProviderId,
    search_result: ProviderResult<Vec<Anime>>,
    series_result: ProviderResult<Vec<SeriesEntry>>,
    episodes_result: ProviderResult<Vec<Episode>>,
}

impl FakeAnimeProvider {
    pub(crate) fn new(id: &str) -> Self {
        Self {
            id: ProviderId::from(id),
            search_result: ProviderResult::not_found(),
            series_result: ProviderResult::not_found(),
            episodes_result: ProviderResult::not_found(),
        }
    }

    pub(crate) fn with_search(mut self, result: ProviderResult<Vec<Anime>>) -> Self {
        self.search_result = result;
        self
    }

    pub(crate) fn with_series(mut self, result: ProviderResult<Vec<SeriesEntry>>) -> Self {
        self.series_result = result;
        self
    }

    pub(crate) fn with_episodes(mut self, result: ProviderResult<Vec<Episode>>) -> Self {
        self.episodes_result = result;
        self
    }
}

#[async_trait]
impl AnimeProvider for FakeAnimeProvider {
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

pub(crate) struct FakeMetadataProvider {
    id: ProviderId,
    search_result: ProviderResult<Vec<Anime>>,
}

impl FakeMetadataProvider {
    pub(crate) fn new(id: &str) -> Self {
        Self {
            id: ProviderId::from(id),
            search_result: ProviderResult::not_found(),
        }
    }

    pub(crate) fn with_search(mut self, result: ProviderResult<Vec<Anime>>) -> Self {
        self.search_result = result;
        self
    }
}

#[async_trait]
impl MetadataProvider for FakeMetadataProvider {
    fn id(&self) -> ProviderId {
        self.id.clone()
    }

    async fn search(&self, _query: &str) -> ProviderResult<Vec<Anime>> {
        clone_result(&self.search_result)
    }
}

pub(crate) struct FakePlayerResolver {
    kind: PlayerKind,
    label: &'static str,
    supported: bool,
    result: Result<ResolvedMedia, String>,
}

impl FakePlayerResolver {
    pub(crate) fn ok(kind: PlayerKind, label: &'static str, url: &str) -> Self {
        Self {
            kind,
            label,
            supported: true,
            result: Ok(ResolvedMedia {
                url: url.to_string(),
                headers: Vec::new(),
            }),
        }
    }

    pub(crate) fn err(kind: PlayerKind, label: &'static str, message: &str) -> Self {
        Self {
            kind,
            label,
            supported: false,
            result: Err(message.to_string()),
        }
    }
}

#[async_trait]
impl PlayerResolver for FakePlayerResolver {
    fn kind(&self) -> PlayerKind {
        self.kind
    }

    fn label(&self) -> &'static str {
        self.label
    }

    fn supported(&self) -> bool {
        self.supported
    }

    async fn resolve(&self, _url: &url::Url) -> anyhow::Result<ResolvedMedia> {
        match &self.result {
            Ok(media) => Ok(media.clone()),
            Err(message) => Err(anyhow::anyhow!(message.clone())),
        }
    }
}

pub(crate) fn stable_sort_by_key<T, K: Ord>(
    items: &mut Vec<T>,
    mut key: impl FnMut(&T) -> K,
) {
    let mut indexed: Vec<(K, usize, T)> = items
        .drain(..)
        .enumerate()
        .map(|(idx, item)| (key(&item), idx, item))
        .collect();
    indexed.sort_by(|a, b| a.0.cmp(&b.0).then_with(|| a.1.cmp(&b.1)));
    items.extend(indexed.into_iter().map(|(_, _, item)| item));
}

fn clone_result<T: Clone>(result: &ProviderResult<T>) -> ProviderResult<T> {
    ProviderResult {
        status: result.status,
        data: result.data.clone(),
        error: result.error.clone(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domain::ProviderStatus;

    #[tokio::test]
    async fn fake_anime_provider_returns_preloaded_results() {
        let anime = Anime {
            id: AnimeId::default(),
            title: "Title".to_string(),
            alt_titles: Vec::new(),
            synopsis: None,
            poster_url: None,
            source: None,
        };
        let provider = FakeAnimeProvider::new("fake")
            .with_search(ProviderResult::ok(vec![anime.clone()]))
            .with_series(ProviderResult::ok(vec![SeriesEntry {
                id: "1".to_string(),
                title: "Series".to_string(),
                order: Some(1),
                provider: ProviderId::from("fake"),
            }]))
            .with_episodes(ProviderResult::ok(vec![Episode {
                id: "e1".to_string(),
                number: Some(1),
                title: None,
                iframe_url: None,
                voice_variants: Vec::new(),
                subtitle_variants: Vec::new(),
                player_kind: PlayerKind::Direct,
                provider: ProviderId::from("fake"),
            }]));

        let result = provider.search("query").await;
        assert_eq!(result.status, ProviderStatus::Ok);
        assert_eq!(result.data.unwrap().len(), 1);

        let result = provider.series(&AnimeId::default()).await;
        assert_eq!(result.status, ProviderStatus::Ok);
        assert_eq!(result.data.unwrap().len(), 1);

        let result = provider.episodes("1").await;
        assert_eq!(result.status, ProviderStatus::Ok);
        assert_eq!(result.data.unwrap().len(), 1);
    }

    #[tokio::test]
    async fn fake_metadata_provider_returns_preloaded_results() {
        let provider = FakeMetadataProvider::new("meta").with_search(ProviderResult::ok(Vec::new()));
        let result = provider.search("query").await;
        assert_eq!(result.status, ProviderStatus::Ok);
    }

    #[tokio::test]
    async fn fake_player_resolver_returns_expected_result() {
        let ok = FakePlayerResolver::ok(PlayerKind::Direct, "Direct", "https://example.com");
        let parsed = url::Url::parse("https://example.com").expect("url");
        let resolved = ok.resolve(&parsed).await.expect("resolved");
        assert_eq!(resolved.url, "https://example.com");

        let err = FakePlayerResolver::err(PlayerKind::Unknown, "Unknown", "nope");
        let err = err.resolve(&parsed).await.expect_err("err");
        assert!(err.to_string().contains("nope"));
    }

    #[test]
    fn stable_sort_by_key_is_deterministic() {
        let mut values = vec!["b", "a", "a", "c"];
        stable_sort_by_key(&mut values, |value| value.chars().next().unwrap());
        assert_eq!(values, vec!["a", "a", "b", "c"]);
    }
}
