use async_trait::async_trait;

use crate::domain::{
    Anime, AnimeId, Episode, ProviderCapabilities, ProviderId, ProviderResult, SeriesEntry,
};

pub mod shikimori;
pub mod yummy;

#[async_trait]
pub trait AnimeProvider: Send + Sync {
    fn id(&self) -> ProviderId;
    fn capabilities(&self) -> ProviderCapabilities;

    async fn search(&self, query: &str) -> ProviderResult<Vec<Anime>>;
    async fn series(&self, anime_id: &AnimeId) -> ProviderResult<Vec<SeriesEntry>>;
    async fn episodes(&self, series_id: &str) -> ProviderResult<Vec<Episode>>;
}
