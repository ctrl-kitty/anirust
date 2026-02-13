use anyhow::{Context, Result};
use std::process::Command;
use url::Url;

use crate::domain::PlayerKind;
use crate::settings::Settings;

use super::resolver::resolve_with_kind;
use super::utils::{detect_player_kind, normalize_url};
use super::ResolvedMedia;

pub async fn play(settings: &Settings, url: &str) -> Result<()> {
    let kind = detect_player_kind(url)?;
    play_with_kind(settings, kind, url, None).await
}

pub async fn play_with_kind(
    settings: &Settings,
    kind: PlayerKind,
    url: &str,
    media_title: Option<&str>,
) -> Result<()> {
    let resolved = resolve_playback_with_kind(kind, url).await?;
    let mut command = Command::new(&settings.player.command);
    command.args(&settings.player.args);
    if let Some(title) = media_title {
        command.arg(format!("--force-media-title={}", title));
    }
    if let Some(header_arg) = mpv_header_arg(&resolved.headers) {
        command.arg(format!("--http-header-fields={}", header_arg));
    }
    command.arg(&resolved.url);

    let status = command
        .status()
        .with_context(|| format!("spawn player command {}", settings.player.command))?;

    if status.success() {
        Ok(())
    } else {
        Err(anyhow::anyhow!("player exited with status {}", status))
    }
}

pub async fn resolve_playback(url: &str) -> Result<ResolvedMedia> {
    let kind = detect_player_kind(url)?;
    resolve_playback_with_kind(kind, url).await
}

pub async fn resolve_playback_with_kind(
    kind: PlayerKind,
    url: &str,
) -> Result<ResolvedMedia> {
    let normalized = normalize_url(url);
    let parsed = Url::parse(&normalized).context("parse playback url")?;
    resolve_with_kind(kind, &parsed).await
}

fn mpv_header_arg(headers: &[(String, String)]) -> Option<String> {
    if headers.is_empty() {
        return None;
    }

    let value = headers
        .iter()
        .map(|(key, value)| format!("{}: {}", key, value))
        .collect::<Vec<_>>()
        .join(",");
    Some(value)
}
