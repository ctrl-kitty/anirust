use anyhow::Result;
use crossterm::event::{KeyCode, KeyEvent, KeyModifiers};

use crate::player;

use super::app::{App, Focus, View, SAVE_OPTIONS};
use super::handlers_flow::{open_episodes, open_series, perform_search};
use super::input::{
    delete_char, input_len, insert_char, move_cursor_left, move_cursor_right,
};
use super::selection::{select_next, select_prev};

pub(crate) async fn handle_key(app: &mut App, key: KeyEvent) -> Result<bool> {
    if key.modifiers.contains(KeyModifiers::CONTROL) && key.code == KeyCode::Char('c') {
        return Ok(true);
    }

    if key.code == KeyCode::Char('q') {
        return Ok(true);
    }

    match app.view {
        View::Search => handle_search_key(app, key).await?,
        View::Series => handle_series_key(app, key).await?,
        View::Dubbing => handle_dubbing_key(app, key).await?,
        View::SaveDubbing => handle_save_dubbing_key(app, key).await?,
        View::Player => handle_player_key(app, key).await?,
        View::Episodes => handle_episodes_key(app, key).await?,
    }

    Ok(false)
}

async fn handle_search_key(app: &mut App, key: KeyEvent) -> Result<()> {
    match app.focus {
        Focus::Input => match key.code {
            KeyCode::Tab => {
                if !app.results.is_empty() {
                    app.focus = Focus::List;
                }
            }
            KeyCode::Enter => {
                perform_search(app).await;
            }
            KeyCode::Backspace => {
                delete_char(&mut app.search_input, &mut app.search_cursor);
            }
            KeyCode::Left => move_cursor_left(&mut app.search_cursor),
            KeyCode::Right => move_cursor_right(&mut app.search_cursor, &app.search_input),
            KeyCode::Home => app.search_cursor = 0,
            KeyCode::End => app.search_cursor = input_len(&app.search_input),
            KeyCode::Char(ch) => {
                if !key.modifiers.contains(KeyModifiers::CONTROL) {
                    insert_char(&mut app.search_input, &mut app.search_cursor, ch);
                }
            }
            _ => {}
        },
        Focus::List => match key.code {
            KeyCode::Tab | KeyCode::BackTab => {
                app.focus = Focus::Input;
            }
            KeyCode::Up => select_prev(&mut app.results_state, app.results.len()),
            KeyCode::Down => select_next(&mut app.results_state, app.results.len()),
            KeyCode::Enter => {
                open_series(app).await;
            }
            KeyCode::Backspace => {
                app.focus = Focus::Input;
            }
            _ => {}
        },
        Focus::Filter => {
            app.focus = Focus::Input;
        }
    }

    Ok(())
}

async fn handle_series_key(app: &mut App, key: KeyEvent) -> Result<()> {
    match key.code {
        KeyCode::Up => select_prev(&mut app.series_state, app.series.len()),
        KeyCode::Down => select_next(&mut app.series_state, app.series.len()),
        KeyCode::Enter => open_episodes(app).await,
        KeyCode::Backspace => {
            app.view = View::Search;
            app.focus = Focus::List;
            app.set_status("Back to results");
        }
        _ => {}
    }

    Ok(())
}

async fn handle_dubbing_key(app: &mut App, key: KeyEvent) -> Result<()> {
    match key.code {
        KeyCode::Up => select_prev(&mut app.dubbing_state, app.dubbing_options.len()),
        KeyCode::Down => select_next(&mut app.dubbing_state, app.dubbing_options.len()),
        KeyCode::Enter => {
            let selected = match app.selected_dubbing_option() {
                Some(value) => value.clone(),
                None => {
                    app.set_status("Select a dubbing first");
                    return Ok(());
                }
            };

            app.selected_dubbing = Some(selected.clone());
            if app.is_saved_default(&selected) {
                app.apply_episode_filter();
                app.view = View::Episodes;
                app.focus = Focus::List;
                app.set_status(format!("Using default dubbing: {}", selected));
            } else {
                app.view = View::SaveDubbing;
                app.save_state.select(Some(0));
                app.set_status("Save this dubbing as default?");
            }
        }
        KeyCode::Backspace => {
            app.view = View::Series;
            app.focus = Focus::List;
            app.set_status("Back to series");
        }
        _ => {}
    }

    Ok(())
}

async fn handle_save_dubbing_key(app: &mut App, key: KeyEvent) -> Result<()> {
    match key.code {
        KeyCode::Up | KeyCode::Down => {
            let len = SAVE_OPTIONS.len();
            if key.code == KeyCode::Up {
                select_prev(&mut app.save_state, len);
            } else {
                select_next(&mut app.save_state, len);
            }
        }
        KeyCode::Enter => {
            let selected = app.save_state.selected().unwrap_or(0);
            if selected == 0 {
                if let Some(dubbing) = app.selected_dubbing.clone() {
                    if let Err(err) = app.save_default_dubbing(dubbing.clone()) {
                        app.set_status(format!("Failed to save default: {}", err));
                    } else {
                        app.set_status(format!("Saved default dubbing: {}", dubbing));
                    }
                }
            }

            app.apply_episode_filter();
            app.view = View::Episodes;
            app.focus = Focus::List;
        }
        KeyCode::Backspace => {
            app.apply_episode_filter();
            app.view = View::Episodes;
            app.focus = Focus::List;
        }
        _ => {}
    }

    Ok(())
}

async fn handle_player_key(app: &mut App, key: KeyEvent) -> Result<()> {
    match key.code {
        KeyCode::Up => select_prev(&mut app.player_state, app.player_options.len()),
        KeyCode::Down => select_next(&mut app.player_state, app.player_options.len()),
        KeyCode::Enter => {
            let selected = match app.selected_player_option() {
                Some(value) => value,
                None => {
                    app.set_status("Select a player first");
                    return Ok(());
                }
            };

            app.selected_player = Some(selected);
            app.apply_episode_filter();
            app.view = View::Episodes;
            app.focus = Focus::List;
            app.set_status(format!("Player set to {}", player::player_label(selected)));
        }
        KeyCode::Backspace => {
            app.view = View::Episodes;
            app.focus = Focus::List;
        }
        _ => {}
    }

    Ok(())
}

async fn handle_episodes_key(app: &mut App, key: KeyEvent) -> Result<()> {
    match app.focus {
        Focus::Filter => match key.code {
            KeyCode::Tab | KeyCode::Enter => {
                app.focus = Focus::List;
            }
            KeyCode::Backspace => {
                delete_char(&mut app.filter_input, &mut app.filter_cursor);
                app.apply_episode_filter();
            }
            KeyCode::Left => move_cursor_left(&mut app.filter_cursor),
            KeyCode::Right => move_cursor_right(&mut app.filter_cursor, &app.filter_input),
            KeyCode::Home => app.filter_cursor = 0,
            KeyCode::End => app.filter_cursor = input_len(&app.filter_input),
            KeyCode::Char(ch) => {
                if !key.modifiers.contains(KeyModifiers::CONTROL) {
                    insert_char(&mut app.filter_input, &mut app.filter_cursor, ch);
                    app.apply_episode_filter();
                }
            }
            _ => {}
        },
        Focus::List | Focus::Input => match key.code {
            KeyCode::Up => select_prev(&mut app.episodes_state, app.episodes.len()),
            KeyCode::Down => select_next(&mut app.episodes_state, app.episodes.len()),
            KeyCode::Enter => {
                if let Some(episode) = app.selected_episode().cloned() {
                    if let Some(url) = episode.iframe_url.as_ref() {
                        let title = app.media_title(&episode);
                        app.request_play(
                            episode.id.clone(),
                            episode.number,
                            episode.player_kind,
                            title,
                            url.as_str(),
                        );
                    } else {
                        app.set_status("Episode has no URL");
                    }
                }
            }
            KeyCode::Char('f') | KeyCode::Char('/') => {
                app.focus = Focus::Filter;
            }
            KeyCode::Char('d') => {
                app.view = View::Dubbing;
                app.focus = Focus::List;
                app.set_status("Select a dubbing variant");
            }
            KeyCode::Char('p') => {
                if app.player_options.is_empty() {
                    app.set_status("No alternate players available");
                } else {
                    app.view = View::Player;
                    app.focus = Focus::List;
                    app.set_status("Select a player");
                }
            }
            KeyCode::Backspace => {
                app.view = View::Series;
                app.focus = Focus::List;
                app.set_status("Back to series");
            }
            _ => {}
        },
    }

    Ok(())
}
