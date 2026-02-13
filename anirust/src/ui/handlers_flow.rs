use crate::domain::{ProviderError, ProviderStatus, SeriesEntry};
use crate::services::catalog::CatalogService;

use super::app::{collect_dubbings, collect_players, App, Focus, View};
use super::selection::select_first;

pub(crate) async fn perform_search(app: &mut App) {
    let query = app.search_input.value().trim().to_string();
    if query.is_empty() {
        app.set_status("Type a query and press Enter");
        return;
    }

    app.set_status("Searching...");

    let provider = match app.provider() {
        Some(provider) => provider,
        None => {
            app.set_status(format!("Provider not found: {}", app.provider_id));
            return;
        }
    };
    let metadata = if app.provider_id.0 == "yummy" {
        app.registry
            .get_metadata(&crate::domain::ProviderId::from("shikimori"))
    } else {
        None
    };
    let catalog = CatalogService::new(provider, metadata);
    let result = catalog.search(&query).await;

    let status = result.status;
    let data = result.data.unwrap_or_default();
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

    app.set_status("Loading series...");
    let provider = match app.provider() {
        Some(provider) => provider,
        None => {
            app.set_status(format!("Provider not found: {}", app.provider_id));
            return;
        }
    };
    let metadata = if app.provider_id.0 == "yummy" {
        app.registry
            .get_metadata(&crate::domain::ProviderId::from("shikimori"))
    } else {
        None
    };
    let catalog = CatalogService::new(provider, metadata);
    let result = catalog.series(&anime_id).await;

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

    app.set_status("Loading episodes...");
    let provider = match app.provider() {
        Some(provider) => provider,
        None => {
            app.set_status(format!("Provider not found: {}", app.provider_id));
            return;
        }
    };
    let metadata = if app.provider_id.0 == "yummy" {
        app.registry
            .get_metadata(&crate::domain::ProviderId::from("shikimori"))
    } else {
        None
    };
    let catalog = CatalogService::new(provider, metadata);
    let result = catalog.episodes_with_stats(&series_id).await;

    let status = result.status;
    let error = result.error;
    let (filtered, dropped) = match result.data {
        Some(data) => (data.episodes, data.dropped),
        None => (Vec::new(), 0),
    };

    match status {
        ProviderStatus::Ok | ProviderStatus::Partial => {
            if filtered.is_empty() {
                app.set_status("No supported episodes found (Alloha only)");
                return;
            }
            app.episodes_all = filtered;
            app.episodes_title = Some(series_title);
            app.current_series_id = Some(series_id);
            app.filter_input.clear();
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

pub(crate) fn select_series_index(
    anime_id: &crate::domain::AnimeId,
    series: &[SeriesEntry],
) -> Option<usize> {
    let yummy_id = anime_id.yummy_id?;
    let target = yummy_id.to_string();
    series.iter().position(|entry| entry.id == target)
}
