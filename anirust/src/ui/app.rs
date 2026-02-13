use anyhow::Result;
use ratatui::widgets::ListState;
use std::collections::BTreeSet;

use crate::domain::{Anime, AnimeId, Episode, PlayerKind, ProviderId, SeriesEntry};
use crate::player;
use crate::providers::AnimeProvider;
use crate::registry::ProviderRegistry;
use crate::settings;

use super::selection::select_first;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum View {
    Search,
    Series,
    Dubbing,
    SaveDubbing,
    Player,
    Episodes,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum Focus {
    Input,
    List,
    Filter,
}

#[derive(Debug, Clone)]
pub(crate) struct PlayRequest {
    pub(crate) url: String,
    pub(crate) episode_id: String,
    pub(crate) episode_number: Option<u32>,
    pub(crate) player_kind: PlayerKind,
    pub(crate) media_title: String,
}

pub(crate) const SAVE_OPTIONS: [&str; 2] = ["Yes, save as default", "No, just this time"];

pub(crate) struct App {
    pub(crate) view: View,
    pub(crate) focus: Focus,
    pub(crate) search_input: String,
    pub(crate) search_cursor: usize,
    pub(crate) filter_input: String,
    pub(crate) filter_cursor: usize,
    pub(crate) status: String,
    pub(crate) results: Vec<Anime>,
    pub(crate) series: Vec<SeriesEntry>,
    pub(crate) episodes_all: Vec<Episode>,
    pub(crate) episodes: Vec<Episode>,
    pub(crate) results_state: ListState,
    pub(crate) series_state: ListState,
    pub(crate) episodes_state: ListState,
    pub(crate) dubbing_options: Vec<String>,
    pub(crate) dubbing_state: ListState,
    pub(crate) save_state: ListState,
    pub(crate) selected_dubbing: Option<String>,
    pub(crate) player_options: Vec<PlayerKind>,
    pub(crate) player_state: ListState,
    pub(crate) selected_player: Option<PlayerKind>,
    pub(crate) registry: ProviderRegistry,
    pub(crate) provider_id: ProviderId,
    pub(crate) settings: settings::Settings,
    pub(crate) series_title: Option<String>,
    pub(crate) episodes_title: Option<String>,
    pub(crate) current_series_id: Option<String>,
    pub(crate) current_anime_id: Option<AnimeId>,
    play_request: Option<PlayRequest>,
}

impl App {
    pub(crate) fn new(
        registry: ProviderRegistry,
        provider_id: ProviderId,
        settings: settings::Settings,
    ) -> Self {
        let mut app = Self {
            view: View::Search,
            focus: Focus::Input,
            search_input: String::new(),
            search_cursor: 0,
            filter_input: String::new(),
            filter_cursor: 0,
            status: String::new(),
            results: Vec::new(),
            series: Vec::new(),
            episodes_all: Vec::new(),
            episodes: Vec::new(),
            results_state: ListState::default(),
            series_state: ListState::default(),
            episodes_state: ListState::default(),
            dubbing_options: Vec::new(),
            dubbing_state: ListState::default(),
            save_state: ListState::default(),
            selected_dubbing: None,
            player_options: Vec::new(),
            player_state: ListState::default(),
            selected_player: None,
            registry,
            provider_id,
            settings,
            series_title: None,
            episodes_title: None,
            current_series_id: None,
            current_anime_id: None,
            play_request: None,
        };

        if app.provider().is_none() {
            app.set_status(format!("Provider not found: {}", app.provider_id));
        }

        app
    }

    pub(crate) fn provider(&self) -> Option<&dyn AnimeProvider> {
        self.registry.get(&self.provider_id)
    }

    pub(crate) fn anime_key(&self) -> Option<String> {
        let id = self.current_anime_id.as_ref()?;
        anime_key_from_id(id)
    }

    pub(crate) fn provider_anime_key(&self) -> Option<String> {
        let anime_key = self.anime_key()?;
        Some(format!("{}:{}", self.provider_id, anime_key))
    }

    pub(crate) fn saved_dubbing(&self) -> Option<String> {
        let key = self.provider_anime_key()?;
        self.settings
            .anime
            .defaults
            .get(&key)
            .and_then(|entry| entry.dubbing.clone())
    }

    pub(crate) fn is_saved_default(&self, dubbing: &str) -> bool {
        self.saved_dubbing()
            .map(|saved| saved == dubbing)
            .unwrap_or(false)
    }

    pub(crate) fn save_default_dubbing(&mut self, dubbing: String) -> Result<()> {
        let key = match self.provider_anime_key() {
            Some(key) => key,
            None => return Ok(()),
        };
        let entry = self.settings.anime.defaults.entry(key).or_default();
        entry.dubbing = Some(dubbing);
        self.settings.save()
    }

    pub(crate) fn update_history(&mut self, request: &PlayRequest) -> Result<()> {
        let key = match self.anime_key() {
            Some(key) => key,
            None => return Ok(()),
        };
        let entry = self.settings.anime.history.entry(key).or_default();
        entry.last_episode_id = Some(request.episode_id.clone());
        entry.last_episode_number = request.episode_number;
        self.settings.save()
    }

    pub(crate) fn last_watched_label(&self) -> Option<String> {
        let key = self.anime_key()?;
        let history = self.settings.anime.history.get(&key)?;
        history
            .last_episode_number
            .map(|value| format!("Last watched: ep {}", value))
            .or_else(|| {
                history
                    .last_episode_id
                    .as_ref()
                    .map(|id| format!("Last watched id: {}", id))
            })
    }

    pub(crate) fn request_play(
        &mut self,
        episode_id: String,
        episode_number: Option<u32>,
        player_kind: PlayerKind,
        media_title: String,
        url: &str,
    ) {
        self.play_request = Some(PlayRequest {
            url: url.to_string(),
            episode_id,
            episode_number,
            player_kind,
            media_title,
        });
    }

    pub(crate) fn take_play_request(&mut self) -> Option<PlayRequest> {
        self.play_request.take()
    }

    pub(crate) fn set_status(&mut self, message: impl Into<String>) {
        self.status = message.into();
    }

    pub(crate) fn update_results(&mut self, results: Vec<Anime>) {
        self.results = results;
        self.results_state.select(select_first(self.results.len()));
    }

    pub(crate) fn update_series(&mut self, series: Vec<SeriesEntry>) {
        self.series = series;
        self.series_state.select(select_first(self.series.len()));
    }

    pub(crate) fn apply_episode_filter(&mut self) {
        let filter = self.filter_input.trim().to_lowercase();
        let selected = self.selected_dubbing.as_deref();
        let selected_player = self.selected_player;

        self.episodes = self
            .episodes_all
            .iter()
            .filter(|episode| match selected {
                Some(label) => episode
                    .voice_variants
                    .iter()
                    .any(|voice| voice.label == label),
                None => true,
            })
            .filter(|episode| match selected_player {
                Some(player_kind) => episode.player_kind == player_kind,
                None => true,
            })
            .filter(|episode| episode_matches_filter(episode, &filter))
            .cloned()
            .collect();
        if let Some(index) = self.last_watched_index(&self.episodes) {
            self.episodes_state.select(Some(index));
        } else {
            self.episodes_state
                .select(select_first(self.episodes.len()));
        }
    }

    pub(crate) fn prefill_dubbing(&mut self) {
        if self.dubbing_options.is_empty() {
            return;
        }

        if let Some(saved) = self.saved_dubbing() {
            if let Some(index) = self
                .dubbing_options
                .iter()
                .position(|value| value == &saved)
            {
                self.dubbing_state.select(Some(index));
                return;
            }
        }

        for preferred in &self.settings.audio.preferred_dubbings {
            if let Some(index) = self
                .dubbing_options
                .iter()
                .position(|value| value == preferred)
            {
                self.dubbing_state.select(Some(index));
                return;
            }
        }

        self.dubbing_state.select(Some(0));
    }

    pub(crate) fn prefill_player(&mut self) {
        if self.player_options.is_empty() {
            self.selected_player = None;
            return;
        }

        let preferred = if self.player_options.contains(&PlayerKind::Kodik) {
            PlayerKind::Kodik
        } else {
            self.player_options[0]
        };

        self.selected_player = Some(preferred);
        if let Some(index) = self
            .player_options
            .iter()
            .position(|player| *player == preferred)
        {
            self.player_state.select(Some(index));
        }
    }

    pub(crate) fn selected_result(&self) -> Option<&Anime> {
        self.results_state
            .selected()
            .and_then(|index| self.results.get(index))
    }

    pub(crate) fn selected_series(&self) -> Option<&SeriesEntry> {
        self.series_state
            .selected()
            .and_then(|index| self.series.get(index))
    }

    pub(crate) fn selected_dubbing_option(&self) -> Option<&String> {
        self.dubbing_state
            .selected()
            .and_then(|index| self.dubbing_options.get(index))
    }

    pub(crate) fn selected_player_option(&self) -> Option<PlayerKind> {
        self.player_state
            .selected()
            .and_then(|index| self.player_options.get(index))
            .copied()
    }

    pub(crate) fn selected_episode(&self) -> Option<&Episode> {
        self.episodes_state
            .selected()
            .and_then(|index| self.episodes.get(index))
    }

    fn last_watched_index(&self, episodes: &[Episode]) -> Option<usize> {
        let key = self.anime_key()?;
        let history = self.settings.anime.history.get(&key)?;

        if let Some(last) = history.last_episode_number {
            if let Some(index) = episodes
                .iter()
                .position(|episode| episode.number == Some(last))
            {
                return Some(index);
            }
        }

        if let Some(last_id) = history.last_episode_id.as_ref() {
            return episodes.iter().position(|episode| &episode.id == last_id);
        }

        None
    }
}

pub(crate) fn collect_dubbings(episodes: &[Episode]) -> Vec<String> {
    let mut set = BTreeSet::new();
    for episode in episodes {
        for voice in &episode.voice_variants {
            if !voice.label.trim().is_empty() {
                set.insert(voice.label.clone());
            }
        }
    }

    set.into_iter().collect()
}

pub(crate) fn collect_players(episodes: &[Episode]) -> Vec<PlayerKind> {
    let mut set = BTreeSet::new();
    for episode in episodes {
        set.insert(episode.player_kind);
    }

    let mut players: Vec<PlayerKind> = set.into_iter().collect();
    players.sort_by_key(|player| player::player_order(*player));
    players
}

fn episode_matches_filter(episode: &Episode, filter: &str) -> bool {
    if filter.is_empty() {
        return true;
    }

    let number = episode
        .number
        .map(|value| value.to_string())
        .unwrap_or_default();
    if number.contains(filter) {
        return true;
    }

    if let Some(title) = episode.title.as_ref() {
        if title.to_lowercase().contains(filter) {
            return true;
        }
    }

    episode.id.to_lowercase().contains(filter)
}

fn anime_key_from_id(id: &AnimeId) -> Option<String> {
    let shiki = id.shikimori_id;
    let mal = id.mal_id;
    match (shiki, mal) {
        (Some(shiki), Some(mal)) => Some(format!("shiki:{}:mal:{}", shiki, mal)),
        (Some(shiki), None) => Some(format!("shiki:{}", shiki)),
        (None, Some(mal)) => Some(format!("mal:{}", mal)),
        (None, None) => None,
    }
}
