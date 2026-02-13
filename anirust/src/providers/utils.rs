use url::Url;

use crate::domain::ProviderError;

pub fn normalize_id(value: Option<u64>) -> Option<u64> {
    match value {
        Some(0) | None => None,
        Some(id) => Some(id),
    }
}

pub fn normalized_text(value: String) -> Option<String> {
    let trimmed = value.trim();
    if trimmed.is_empty() {
        None
    } else {
        Some(trimmed.to_string())
    }
}

pub fn parse_url(value: &str) -> Option<Url> {
    Url::parse(value)
        .ok()
        .or_else(|| Url::parse(&format!("https:{}", value)).ok())
}

pub fn map_reqwest_error(error: reqwest::Error) -> ProviderError {
    ProviderError::new(error.to_string(), error.is_timeout() || error.is_connect())
}
