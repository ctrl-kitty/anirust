use serde::{Deserialize, Serialize};
use std::fmt;
use url::Url;

#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize, Default)]
pub struct AnimeId {
    pub shikimori_id: Option<u64>,
    pub mal_id: Option<u64>,
    pub yummy_id: Option<u64>,
}

impl AnimeId {
    pub fn is_empty(&self) -> bool {
        self.shikimori_id.is_none() && self.mal_id.is_none() && self.yummy_id.is_none()
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Anime {
    pub id: AnimeId,
    pub title: String,
    pub alt_titles: Vec<String>,
    pub synopsis: Option<String>,
    pub poster_url: Option<Url>,
    pub source: Option<ProviderId>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SeriesEntry {
    pub id: String,
    pub title: String,
    pub order: Option<u32>,
    pub provider: ProviderId,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Episode {
    pub id: String,
    pub number: Option<u32>,
    pub title: Option<String>,
    pub iframe_url: Option<Url>,
    pub voice_variants: Vec<VoiceVariant>,
    pub subtitle_variants: Vec<SubtitleVariant>,
    pub player_kind: PlayerKind,
    pub provider: ProviderId,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct VoiceVariant {
    pub id: String,
    pub label: String,
    pub lang: Option<String>,
    pub provider: ProviderId,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SubtitleVariant {
    pub id: String,
    pub label: String,
    pub lang: Option<String>,
    pub provider: ProviderId,
}

#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct ProviderId(pub String);

impl ProviderId {
    pub fn new(value: impl Into<String>) -> Self {
        Self(value.into())
    }
}

impl fmt::Display for ProviderId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        self.0.fmt(f)
    }
}

impl From<&str> for ProviderId {
    fn from(value: &str) -> Self {
        Self::new(value)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum ProviderStatus {
    Ok,
    Partial,
    NotFound,
    RateLimited,
    Unauthorized,
    Error,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
pub enum PlayerKind {
    Kodik,
    Alloha,
    Direct,
    Unknown,
}

impl PlayerKind {
    pub fn from_url(url: &Url) -> Self {
        let host = url
            .host_str()
            .map(|value| value.to_lowercase())
            .unwrap_or_default();
        if host.contains("kodik") {
            return PlayerKind::Kodik;
        }
        if host.contains("alloha") {
            return PlayerKind::Alloha;
        }

        let path = url.path().to_lowercase();
        if path.ends_with(".m3u8")
            || path.ends_with(".mp4")
            || path.ends_with(".webm")
            || path.ends_with(".mkv")
        {
            return PlayerKind::Direct;
        }

        PlayerKind::Unknown
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ProviderError {
    pub message: String,
    pub source: Option<String>,
    pub retryable: bool,
}

impl ProviderError {
    pub fn new(message: impl Into<String>, retryable: bool) -> Self {
        Self {
            message: message.into(),
            source: None,
            retryable,
        }
    }

    pub fn with_source(mut self, source: impl Into<String>) -> Self {
        self.source = Some(source.into());
        self
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ProviderResult<T> {
    pub status: ProviderStatus,
    pub data: Option<T>,
    pub error: Option<ProviderError>,
}

impl<T> ProviderResult<T> {
    pub fn ok(data: T) -> Self {
        Self {
            status: ProviderStatus::Ok,
            data: Some(data),
            error: None,
        }
    }

    pub fn partial(data: T, error: ProviderError) -> Self {
        Self {
            status: ProviderStatus::Partial,
            data: Some(data),
            error: Some(error),
        }
    }

    pub fn not_found() -> Self {
        Self {
            status: ProviderStatus::NotFound,
            data: None,
            error: None,
        }
    }

    pub fn rate_limited(error: ProviderError) -> Self {
        Self {
            status: ProviderStatus::RateLimited,
            data: None,
            error: Some(error),
        }
    }

    pub fn unauthorized(error: ProviderError) -> Self {
        Self {
            status: ProviderStatus::Unauthorized,
            data: None,
            error: Some(error),
        }
    }

    pub fn error(error: ProviderError) -> Self {
        Self {
            status: ProviderStatus::Error,
            data: None,
            error: Some(error),
        }
    }

    pub fn is_ok(&self) -> bool {
        matches!(self.status, ProviderStatus::Ok | ProviderStatus::Partial)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
pub struct ProviderCapabilities {
    pub search: bool,
    pub series_list: bool,
    pub episodes: bool,
}

impl ProviderCapabilities {
    pub const fn new(search: bool, series_list: bool, episodes: bool) -> Self {
        Self {
            search,
            series_list,
            episodes,
        }
    }
}
