use anyhow::{Context, Result};
use url::Url;

use crate::domain::PlayerKind;

pub(crate) fn normalize_url(value: &str) -> String {
    let trimmed = value.trim();
    if trimmed.starts_with("http://") || trimmed.starts_with("https://") {
        trimmed.to_string()
    } else if trimmed.starts_with("//") {
        format!("https:{}", trimmed)
    } else {
        format!("https://{}", trimmed)
    }
}

pub(crate) fn detect_player_kind(value: &str) -> Result<PlayerKind> {
    let normalized = normalize_url(value);
    let parsed = Url::parse(&normalized).context("parse playback url")?;
    Ok(PlayerKind::from_url(&parsed))
}
