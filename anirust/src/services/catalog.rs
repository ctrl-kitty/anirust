use crate::domain::{Anime, AnimeId, Episode, ProviderResult, ProviderStatus, SeriesEntry};
use crate::player;
use crate::providers::{AnimeProvider, MetadataProvider};
use crate::services::unify;

pub struct CatalogService<'a> {
    anime: &'a dyn AnimeProvider,
    metadata: Option<&'a dyn MetadataProvider>,
}

pub struct EpisodeFilterResult {
    pub episodes: Vec<Episode>,
    pub dropped: usize,
}

impl<'a> CatalogService<'a> {
    pub fn new(
        anime: &'a dyn AnimeProvider,
        metadata: Option<&'a dyn MetadataProvider>,
    ) -> Self {
        Self { anime, metadata }
    }

    pub async fn search(&self, query: &str) -> ProviderResult<Vec<Anime>> {
        let result = self.anime.search(query).await;
        let mut merged = unify::unify_search(query, result, self.metadata).await;
        if matches!(merged.status, ProviderStatus::Ok | ProviderStatus::Partial) {
            if let Some(mut data) = merged.data.take() {
                sort_search_results(query, &mut data);
                merged.data = Some(data);
            }
        }

        merged
    }

    pub async fn series(&self, anime_id: &AnimeId) -> ProviderResult<Vec<SeriesEntry>> {
        let mut result = self.anime.series(anime_id).await;
        if matches!(result.status, ProviderStatus::Ok | ProviderStatus::Partial) {
            let mut data = result.data.take().unwrap_or_default();
            data.sort_by_key(|entry| entry.order.unwrap_or(u32::MAX));
            result.data = Some(data);
        }

        result
    }

    pub async fn episodes(&self, series_id: &str) -> ProviderResult<Vec<Episode>> {
        let result = self.episodes_with_stats(series_id).await;
        ProviderResult {
            status: result.status,
            data: result.data.map(|data| data.episodes),
            error: result.error,
        }
    }

    pub async fn episodes_with_stats(
        &self,
        series_id: &str,
    ) -> ProviderResult<EpisodeFilterResult> {
        let result = self.anime.episodes(series_id).await;
        let status = result.status;
        let error = result.error;
        let data = if matches!(status, ProviderStatus::Ok | ProviderStatus::Partial) {
            let data = result.data.unwrap_or_default();
            let (mut filtered, dropped) = filter_supported_episodes(data);
            filtered.sort_by_key(|episode| episode.number.unwrap_or(u32::MAX));
            Some(EpisodeFilterResult {
                episodes: filtered,
                dropped,
            })
        } else {
            None
        };

        ProviderResult { status, data, error }
    }
}

fn filter_supported_episodes(episodes: Vec<Episode>) -> (Vec<Episode>, usize) {
    let mut filtered = Vec::new();
    let mut dropped = 0;
    for episode in episodes {
        if !player::is_supported_kind(episode.player_kind) {
            dropped += 1;
            continue;
        }

        filtered.push(episode);
    }

    (filtered, dropped)
}

fn sort_search_results(query: &str, items: &mut Vec<Anime>) {
    if items.is_empty() {
        return;
    }

    let query_norm = normalize_text(query);
    let mut scored: Vec<(i64, usize, Anime)> = items
        .drain(..)
        .enumerate()
        .map(|(idx, anime)| {
            let score = similarity_score(&query_norm, &anime.title);
            (score, idx, anime)
        })
        .collect();

    scored.sort_by(|a, b| b.0.cmp(&a.0).then_with(|| a.1.cmp(&b.1)));
    items.extend(scored.into_iter().map(|(_, _, anime)| anime));
}

fn similarity_score(query_norm: &str, title: &str) -> i64 {
    let title_norm = normalize_text(title);
    if query_norm.is_empty() || title_norm.is_empty() {
        return 0;
    }

    let mut score = 0i64;
    if title_norm == query_norm {
        score += 1000;
    }
    if title_norm.starts_with(query_norm) {
        score += 600;
    }
    if title_norm.contains(query_norm) {
        score += 400;
    }

    let query_tokens: Vec<&str> = query_norm.split_whitespace().collect();
    let title_tokens: Vec<&str> = title_norm.split_whitespace().collect();
    let mut token_hits = 0i64;
    for token in query_tokens {
        if title_tokens.iter().any(|value| *value == token) {
            token_hits += 1;
        }
    }
    score += token_hits * 100;

    let diff = (title_norm.len() as i64 - query_norm.len() as i64).abs();
    score -= diff;

    score
}

fn normalize_text(value: &str) -> String {
    value
        .chars()
        .flat_map(|ch| {
            if ch.is_alphanumeric() {
                ch.to_lowercase().collect::<Vec<_>>()
            } else {
                vec![' ']
            }
        })
        .collect::<String>()
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ")
}

#[cfg(test)]
mod tests;
