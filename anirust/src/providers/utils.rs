use url::Url;

use crate::domain::ProviderError;
use tracing::error;

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

pub fn log_decode_error(
    provider: &str,
    url: &str,
    status: reqwest::StatusCode,
    body: &[u8],
    error: &dyn std::error::Error,
) {
    const BODY_LIMIT: usize = 4096;
    let mut body_text = String::from_utf8_lossy(body).to_string();
    if body_text.len() > BODY_LIMIT {
        body_text.truncate(BODY_LIMIT);
        body_text.push_str("... [truncated]");
    }

    error!(
        provider,
        url,
        status = %status,
        error = %error,
        body = %body_text,
        "error decoding response body"
    );
}
