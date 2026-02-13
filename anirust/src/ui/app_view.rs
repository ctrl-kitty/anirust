use ratatui::style::{Color, Style};
use ratatui::widgets::ListItem;

use crate::domain::Episode;
use crate::player;

use super::app::{App, Focus, View, SAVE_OPTIONS};
use super::input::visible_input;

impl App {
    pub(crate) fn header(&self, width: u16) -> (&'static str, String, Option<u16>, Style) {
        let inner_width = width.saturating_sub(2);
        match self.view {
            View::Search => {
                let (visible, offset) =
                    visible_input(&self.search_input, self.search_cursor, inner_width);
                let cursor = if self.focus == Focus::Input {
                    Some(offset)
                } else {
                    None
                };
                (
                    "Search",
                    visible,
                    cursor,
                    if self.focus == Focus::Input {
                        Style::default().fg(Color::Yellow)
                    } else {
                        Style::default()
                    },
                )
            }
            View::Series => (
                "Series",
                format!(
                    "Anime: {} | Provider: {}",
                    self.series_title.as_deref().unwrap_or("<unknown>"),
                    self.provider_id
                ),
                None,
                Style::default(),
            ),
            View::Dubbing => (
                "Dubbing",
                format!(
                    "Anime: {}",
                    self.episodes_title.as_deref().unwrap_or("<unknown>")
                ),
                None,
                Style::default(),
            ),
            View::SaveDubbing => (
                "Save Default",
                format!(
                    "Use '{}' ?",
                    self.selected_dubbing.as_deref().unwrap_or("-")
                ),
                None,
                Style::default(),
            ),
            View::Player => (
                "Player",
                format!(
                    "Series: {}",
                    self.episodes_title.as_deref().unwrap_or("<unknown>")
                ),
                None,
                Style::default(),
            ),
            View::Episodes => {
                if self.focus == Focus::Filter || !self.filter_input.is_empty() {
                    let prefix = "Filter: ";
                    let available = inner_width.saturating_sub(prefix.len() as u16);
                    let (visible, offset) =
                        visible_input(&self.filter_input, self.filter_cursor, available);
                    let text = format!("{}{}", prefix, visible);
                    let cursor = if self.focus == Focus::Filter {
                        Some(prefix.len() as u16 + offset)
                    } else {
                        None
                    };
                    (
                        "Episodes",
                        text,
                        cursor,
                        if self.focus == Focus::Filter {
                            Style::default().fg(Color::Yellow)
                        } else {
                            Style::default()
                        },
                    )
                } else {
                    (
                        "Episodes",
                        format!(
                            "Series: {} | Dubbing: {} | Player: {}",
                            self.episodes_title.as_deref().unwrap_or("<unknown>"),
                            self.selected_dubbing.as_deref().unwrap_or("-"),
                            self.selected_player
                                .map(player::player_label)
                                .unwrap_or("-")
                        ),
                        None,
                        Style::default(),
                    )
                }
            }
        }
    }

    pub(crate) fn list_items(&self) -> Vec<ListItem<'static>> {
        match self.view {
            View::Search => self
                .results
                .iter()
                .map(|anime| {
                    ListItem::new(format!(
                        "{} (yummy: {}, shiki: {}, mal: {})",
                        anime.title,
                        format_id(anime.id.yummy_id),
                        format_id(anime.id.shikimori_id),
                        format_id(anime.id.mal_id)
                    ))
                })
                .collect(),
            View::Series => self
                .series
                .iter()
                .map(|entry| {
                    let order = entry
                        .order
                        .map(|value| value.to_string())
                        .unwrap_or_else(|| "-".to_string());
                    ListItem::new(format!("[{}] {} (id: {})", order, entry.title, entry.id))
                })
                .collect(),
            View::Dubbing => self
                .dubbing_options
                .iter()
                .map(|dubbing| ListItem::new(dubbing.clone()))
                .collect(),
            View::SaveDubbing => SAVE_OPTIONS
                .iter()
                .map(|option| ListItem::new(option.to_string()))
                .collect(),
            View::Player => self
                .player_options
                .iter()
                .map(|player_kind| ListItem::new(player::player_label(*player_kind).to_string()))
                .collect(),
            View::Episodes => {
                let last_watched = self
                    .anime_key()
                    .and_then(|key| self.settings.anime.history.get(&key))
                    .and_then(|history| history.last_episode_number);
                self.episodes
                    .iter()
                    .map(|episode| {
                        let number = episode
                            .number
                            .map(|value| value.to_string())
                            .unwrap_or_else(|| "-".to_string());
                        let ep_title = episode
                            .title
                            .as_deref()
                            .map(|value| value.trim())
                            .filter(|value| !value.is_empty());
                        let ep_label = match ep_title {
                            Some(title) => format!("ep {} - {}", number, title),
                            None => format!("ep {}", number),
                        };
                        let dubbing = episode
                            .voice_variants
                            .iter()
                            .map(|voice| voice.label.as_str())
                            .collect::<Vec<_>>()
                            .join(", ");
                        let dubbing = if dubbing.is_empty() { "-" } else { &dubbing };
                        let prefix = match (last_watched, episode.number) {
                            (Some(last), Some(number)) if last == number => "* ",
                            _ => "  ",
                        };
                        ListItem::new(format!("{}{} | dubbing: {}", prefix, ep_label, dubbing))
                    })
                    .collect()
            }
        }
    }

    pub(crate) fn footer_text(&self) -> String {
        let mut lines = Vec::new();
        if !self.status.is_empty() {
            lines.push(self.status.clone());
        }

        if let Some(label) = self.last_watched_label() {
            lines.push(label);
        }

        lines.push(
            match self.view {
                View::Search => {
                    "Enter: search | Tab: focus | Up/Down: select | Enter (list): series | q: quit"
                }
                View::Series => "Enter: episodes | Backspace: back | Up/Down: select | q: quit",
                View::Dubbing => "Enter: choose | Backspace: back | Up/Down: select | q: quit",
                View::SaveDubbing => "Enter: confirm | Backspace: skip | Up/Down: select | q: quit",
                View::Player => "Enter: choose | Backspace: back | Up/Down: select | q: quit",
                View::Episodes => {
                    if self.focus == Focus::Filter {
                        "Type to filter | Enter/Tab: back | d: dubbing | p: player | q: quit"
                    } else {
                        "Enter: play | f: filter | d: dubbing | p: player | Backspace: back | q: quit"
                    }
                }
            }
            .to_string(),
        );

        if let View::Episodes = self.view {
            if let Some(url) = self
                .selected_episode()
                .and_then(|episode| episode.iframe_url.as_ref())
                .map(|url| url.as_str())
            {
                lines.push(format!("URL: {}", url));
            }
        }

        lines.join("\n")
    }

    pub(crate) fn media_title(&self, episode: &Episode) -> String {
        let mut parts = Vec::new();
        if let Some(anime_title) = self.series_title.as_deref() {
            if !anime_title.is_empty() {
                parts.push(anime_title.to_string());
            }
        }

        if let Some(series_title) = self.episodes_title.as_deref() {
            if !series_title.is_empty() {
                if parts
                    .last()
                    .map(|value| value != series_title)
                    .unwrap_or(true)
                {
                    parts.push(series_title.to_string());
                }
            }
        }

        let ep_label = episode
            .number
            .map(|value| format!("Ep {}", value))
            .unwrap_or_else(|| format!("Ep {}", episode.id));
        let ep_label = match episode.title.as_deref().map(str::trim) {
            Some(title) if !title.is_empty() => format!("{} - {}", ep_label, title),
            _ => ep_label,
        };
        parts.push(ep_label);

        if let Some(dubbing) = self.selected_dubbing.as_deref() {
            if !dubbing.is_empty() {
                parts.push(dubbing.to_string());
            }
        }

        parts.join(" - ")
    }
}

fn format_id(value: Option<u64>) -> String {
    value
        .map(|value| value.to_string())
        .unwrap_or_else(|| "-".to_string())
}
