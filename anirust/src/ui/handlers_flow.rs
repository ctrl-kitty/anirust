use crate::domain::{Anime, Episode, ProviderError, ProviderStatus, SeriesEntry};
use crate::player;
use crate::services::unify;

use super::app::{collect_dubbings, collect_players, App, Focus, View};
use super::selection::select_first;

pub(crate) async fn perform_search(app: &mut App) {
    let query = app.search_input.trim().to_string();
    if query.is_empty() {
        app.set_status("Type a query and press Enter");
        return;
    }

    let caps = match app.provider() {
        Some(provider) => provider.capabilities(),
        None => {
            app.set_status(format!("Provider not found: {}", app.provider_id));
            return;
        }
    };

    if !caps.search {
        app.set_status(format!("Provider {} does not support search", app.provider_id));
        return;
    }

    app.set_status("Searching...");

    let result = if app.provider_id.0 == "yummy" {
        let yummy = match app.provider() {
            Some(provider) => provider,
            None => {
                app.set_status(format!("Provider not found: {}", app.provider_id));
                return;
            }
        };

        let yummy_result = yummy.search(&query).await;
        let shiki = app
            .registry
            .get(&crate::domain::ProviderId::from("shikimori"));
        unify::unify_search(&query, yummy_result, shiki).await
    } else {
        match app.provider() {
            Some(provider) => provider.search(&query).await,
            None => {
                app.set_status(format!("Provider not found: {}", app.provider_id));
                return;
            }
        }
    };

    let status = result.status;
    let mut data = result.data.unwrap_or_default();
    sort_search_results(&query, &mut data);
    let error = result.error;

    match status {
        ProviderStatus::Ok | ProviderStatus::Partial => {
            app.update_results(data);
            app.focus = Focus::List;
            app.view = View::Search;
            if status == ProviderStatus::Partial {
                app.set_status(format!(
                    "Results: {} (partial: {})",
                    app.results.len(),
                    error
                        .map(|err| err.message)
                        .unwrap_or_else(|| "unknown error".to_string())
                ));
            } else {
                app.set_status(format!("Results: {}", app.results.len()));
            }
        }
        ProviderStatus::NotFound => {
            app.update_results(Vec::new());
            app.focus = Focus::Input;
            app.set_status("No results found");
        }
        ProviderStatus::RateLimited | ProviderStatus::Unauthorized | ProviderStatus::Error => {
            app.set_status(provider_error_message(status, error));
        }
    }
}

pub(crate) async fn open_series(app: &mut App) {
    let (anime_id, anime_title) = match app.selected_result() {
        Some(anime) => (anime.id.clone(), anime.title.clone()),
        None => {
            app.set_status("Select a title first");
            return;
        }
    };

    app.current_anime_id = Some(anime_id.clone());

    let caps = match app.provider() {
        Some(provider) => provider.capabilities(),
        None => {
            app.set_status(format!("Provider not found: {}", app.provider_id));
            return;
        }
    };

    if !caps.series_list {
        app.set_status(format!(
            "Provider {} does not support series list",
            app.provider_id
        ));
        return;
    }

    app.set_status("Loading series...");
    let result = match app.provider() {
        Some(provider) => provider.series(&anime_id).await,
        None => {
            app.set_status(format!("Provider not found: {}", app.provider_id));
            return;
        }
    };

    let status = result.status;
    let data = result.data.unwrap_or_default();
    let error = result.error;

    match status {
        ProviderStatus::Ok | ProviderStatus::Partial => {
            if let Some(index) = select_series_index(&anime_id, &data) {
                app.update_series(data);
                app.series_title = Some(anime_title);
                app.series_state.select(Some(index));
                open_episodes(app).await;
                return;
            }

            app.update_series(data);
            app.series_title = Some(anime_title);
            app.view = View::Series;
            app.focus = Focus::List;
            if status == ProviderStatus::Partial {
                app.set_status(format!(
                    "Series: {} (partial: {})",
                    app.series.len(),
                    error
                        .map(|err| err.message)
                        .unwrap_or_else(|| "unknown error".to_string())
                ));
            } else {
                app.set_status(format!("Series: {}", app.series.len()));
            }
        }
        ProviderStatus::NotFound => {
            app.set_status("No series entries found");
        }
        ProviderStatus::RateLimited | ProviderStatus::Unauthorized | ProviderStatus::Error => {
            app.set_status(provider_error_message(status, error));
        }
    }
}

pub(crate) async fn open_episodes(app: &mut App) {
    let (series_id, series_title) = match app.selected_series() {
        Some(series) => (series.id.clone(), series.title.clone()),
        None => {
            app.set_status("Select a series first");
            return;
        }
    };

    let caps = match app.provider() {
        Some(provider) => provider.capabilities(),
        None => {
            app.set_status(format!("Provider not found: {}", app.provider_id));
            return;
        }
    };

    if !caps.episodes {
        app.set_status(format!(
            "Provider {} does not support episodes",
            app.provider_id
        ));
        return;
    }

    app.set_status("Loading episodes...");
    let result = match app.provider() {
        Some(provider) => provider.episodes(&series_id).await,
        None => {
            app.set_status(format!("Provider not found: {}", app.provider_id));
            return;
        }
    };

    let status = result.status;
    let data = result.data.unwrap_or_default();
    let (filtered, dropped) = filter_supported_episodes(data);
    if filtered.is_empty() {
        app.set_status("No supported episodes found (Alloha only)");
        return;
    }
    let error = result.error;

    match status {
        ProviderStatus::Ok | ProviderStatus::Partial => {
            app.episodes_all = filtered;
            app.episodes_title = Some(series_title);
            app.current_series_id = Some(series_id);
            app.filter_input.clear();
            app.filter_cursor = 0;
            app.selected_dubbing = None;
            app.selected_player = None;
            app.dubbing_options = collect_dubbings(&app.episodes_all);
            app.player_options = collect_players(&app.episodes_all);
            app.player_state.select(select_first(app.player_options.len()));
            app.prefill_player();
            if app.dubbing_options.is_empty() {
                app.apply_episode_filter();
                app.view = View::Episodes;
                app.focus = Focus::List;
                app.set_status("No dubbing variants found; showing all episodes");
            } else {
                app.dubbing_state
                    .select(select_first(app.dubbing_options.len()));
                app.prefill_dubbing();

                if let Some(saved) = app.saved_dubbing() {
                    if app.dubbing_options.iter().any(|value| value == &saved) {
                        app.selected_dubbing = Some(saved.clone());
                        app.apply_episode_filter();
                        app.view = View::Episodes;
                        app.focus = Focus::List;
                        app.set_status(format!("Using saved dubbing: {}", saved));
                        return;
                    }
                }

                app.view = View::Dubbing;
                app.focus = Focus::List;
                let mut status_message = if status == ProviderStatus::Partial {
                    format!(
                        "Dubbing variants (partial: {})",
                        error
                            .map(|err| err.message)
                            .unwrap_or_else(|| "unknown error".to_string())
                    )
                } else {
                    "Select a dubbing variant".to_string()
                };

                if dropped > 0 {
                    status_message = format!(
                        "{} | filtered {} alloha episodes",
                        status_message, dropped
                    );
                }

                app.set_status(status_message);
            }
        }
        ProviderStatus::NotFound => {
            app.set_status("No episodes found");
        }
        ProviderStatus::RateLimited | ProviderStatus::Unauthorized | ProviderStatus::Error => {
            app.set_status(provider_error_message(status, error));
        }
    }
}

fn provider_error_message(status: ProviderStatus, error: Option<ProviderError>) -> String {
    let message = error
        .map(|err| err.message)
        .unwrap_or_else(|| "unknown error".to_string());
    format!("{:?}: {}", status, message)
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

pub(crate) fn select_series_index(
    anime_id: &crate::domain::AnimeId,
    series: &[SeriesEntry],
) -> Option<usize> {
    let yummy_id = anime_id.yummy_id?;
    let target = yummy_id.to_string();
    series.iter().position(|entry| entry.id == target)
}

pub(crate) fn sort_search_results(query: &str, items: &mut Vec<Anime>) {
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
