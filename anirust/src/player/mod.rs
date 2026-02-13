mod kodik;
mod play;
mod resolver;
mod utils;

#[cfg(test)]
mod tests;

#[derive(Debug, Clone)]
pub struct ResolvedMedia {
    pub url: String,
    pub headers: Vec<(String, String)>,
}

pub use play::{play, play_with_kind, resolve_playback, resolve_playback_with_kind};
pub use resolver::{is_supported_kind, player_label, player_order, PlayerResolver};
