use anyhow::{Context, Result};
use base64::engine::general_purpose::STANDARD as BASE64_ENGINE;
use base64::Engine;
use regex::Regex;
use reqwest::header::{HeaderMap, HeaderValue, ACCEPT, ORIGIN, REFERER};
use serde::Deserialize;
use serde_json::Value;
use std::collections::HashMap;
use url::Url;

use super::ResolvedMedia;

pub(super) async fn resolve_kodik(url: &Url) -> Result<ResolvedMedia> {
    let client = reqwest::Client::builder()
        .user_agent("anirust/0.1")
        .build()
        .context("build kodik http client")?;
    let netloc = netloc_from_url(url)?;

    let response = client
        .get(url.as_str())
        .send()
        .await
        .context("fetch kodik iframe")?;
    let status = response.status();
    let html = response.text().await.context("read kodik html")?;

    if status.as_u16() == 500 && html.contains("An unhandled lowlevel error occurred") {
        return Err(anyhow::anyhow!("kodik returned unhandled error"));
    }

    if html.contains("<div class=\"message\">Видео не найдено</div>") {
        return Err(anyhow::anyhow!("kodik video not found"));
    }

    let player_js_path = extract_player_js_path(&html)
        .context("extract kodik player js path")?;
    let mut api_path = fetch_kodik_api_path(&client, &netloc, &player_js_path).await?;

    let mut payload = extract_url_params(&html)?;
    let vinfo = extract_vinfo(&html, url)?;
    payload.extend(vinfo);
    payload.insert("bad_user".to_string(), "false".to_string());
    payload.insert("cdn_is_working".to_string(), "true".to_string());
    payload.insert("info".to_string(), "{}".to_string());

    let api_headers = create_kodik_headers(url, &netloc)?;
    let play_headers = create_play_headers(url, &netloc)?;
    let api_url = format!("https://{}{}", netloc, api_path);
    let mut response_api = client
        .post(&api_url)
        .headers(api_headers.clone())
        .form(&payload)
        .send()
        .await
        .context("request kodik api")?;

    if !response_api.status().is_success() {
        api_path = fetch_kodik_api_path(&client, &netloc, &player_js_path).await?;
        let retry_url = format!("https://{}{}", netloc, api_path);
        response_api = client
            .post(&retry_url)
            .headers(api_headers.clone())
            .form(&payload)
            .send()
            .await
            .context("retry kodik api")?;
        if !response_api.status().is_success() {
            return Err(anyhow::anyhow!("kodik api returned {}", response_api.status()));
        }
    }

    let response_body = response_api
        .text()
        .await
        .context("read kodik api response")?;
    let mut parsed: KodikResponse = serde_json::from_str(&response_body)
        .context("parse kodik api response")?;
    let mut resolved_url = pick_playable_kodik_link(&client, &play_headers, &parsed.links)
        .await
        .ok();

    if resolved_url.is_none() {
        api_path = fetch_kodik_api_path(&client, &netloc, &player_js_path).await?;
        let retry_url = format!("https://{}{}", netloc, api_path);
        let retry_response = client
            .post(&retry_url)
            .headers(api_headers.clone())
            .form(&payload)
            .send()
            .await
            .context("retry kodik api (no links)")?;
        let retry_body = retry_response
            .text()
            .await
            .context("read retry kodik api response")?;
        parsed = serde_json::from_str(&retry_body).context("parse retry kodik response")?;
        resolved_url = pick_playable_kodik_link(&client, &play_headers, &parsed.links)
            .await
            .ok();
    }

    let resolved_url = match resolved_url {
        Some(url) => url,
        None => {
            let keys = parsed
                .links
                .keys()
                .cloned()
                .collect::<Vec<_>>()
                .join(", ");
            return Err(anyhow::anyhow!(
                "kodik response had no playable links (keys: {})",
                keys
            ));
        }
    };
    let origin = format!("{}://{}", url.scheme(), netloc);
    let mut headers = Vec::new();
    headers.push(("Referer".to_string(), url.as_str().to_string()));
    headers.push(("Origin".to_string(), origin));

    Ok(ResolvedMedia {
        url: resolved_url,
        headers,
    })
}

fn netloc_from_url(url: &Url) -> Result<String> {
    let host = url
        .host_str()
        .ok_or_else(|| anyhow::anyhow!("missing host"))?;
    let netloc = match url.port() {
        Some(port) => format!("{}:{}", host, port),
        None => host.to_string(),
    };
    Ok(netloc)
}

fn create_kodik_headers(url: &Url, netloc: &str) -> Result<HeaderMap> {
    let mut headers = HeaderMap::new();
    let origin = format!("{}://{}", url.scheme(), netloc);
    headers.insert(
        ORIGIN,
        HeaderValue::from_str(&origin).context("set origin header")?,
    );
    headers.insert(
        REFERER,
        HeaderValue::from_str(url.as_str()).context("set referer header")?,
    );
    headers.insert(
        ACCEPT,
        HeaderValue::from_static("application/json, text/javascript, */*; q=0.01"),
    );
    Ok(headers)
}

fn create_play_headers(url: &Url, netloc: &str) -> Result<HeaderMap> {
    let mut headers = HeaderMap::new();
    let origin = format!("{}://{}", url.scheme(), netloc);
    headers.insert(
        ORIGIN,
        HeaderValue::from_str(&origin).context("set origin header")?,
    );
    headers.insert(
        REFERER,
        HeaderValue::from_str(url.as_str()).context("set referer header")?,
    );
    headers.insert(ACCEPT, HeaderValue::from_static("*/*"));
    Ok(headers)
}

fn extract_player_js_path(html: &str) -> Result<String> {
    let regex_src = Regex::new(r#"src=\"(/assets/js/app\.player[^\"]+\.js)\""#)
        .context("compile player js regex")?;
    if let Some(caps) = regex_src.captures(html) {
        return Ok(caps
            .get(1)
            .map(|m| m.as_str().to_string())
            .unwrap_or_default());
    }

    let regex_link = Regex::new(r#"playerLink\s*=\s*\[\"([^\"]+)\"\]"#)
        .context("compile player link regex")?;
    if let Some(caps) = regex_link.captures(html) {
        return Ok(caps
            .get(1)
            .map(|m| m.as_str().to_string())
            .unwrap_or_default());
    }

    Err(anyhow::anyhow!("player js path not found"))
}

async fn fetch_kodik_api_path(
    client: &reqwest::Client,
    netloc: &str,
    js_path: &str,
) -> Result<String> {
    let js_url = if js_path.starts_with("http://") || js_path.starts_with("https://") {
        js_path.to_string()
    } else {
        format!("https://{}{}", netloc, js_path)
    };
    let js = client
        .get(js_url)
        .send()
        .await
        .context("fetch kodik player js")?
        .text()
        .await
        .context("read kodik player js")?;
    let regex = Regex::new(r#"\$\.ajax[^\)]*atob\(['"](\w+=)['"]\)"#)
        .context("compile kodik api regex")?;
    let caps = regex
        .captures(&js)
        .ok_or_else(|| anyhow::anyhow!("kodik api path not found"))?;
    let encoded = caps.get(1).map(|m| m.as_str()).unwrap_or_default();
    let decoded = BASE64_ENGINE
        .decode(encoded.as_bytes())
        .context("decode kodik api path")?;
    let path = String::from_utf8(decoded).context("kodik api path utf8")?;
    Ok(path)
}

fn extract_url_params(html: &str) -> Result<HashMap<String, String>> {
    let regex = Regex::new(r"var urlParams = '([^']+)'")
        .context("compile urlParams regex")?;
    let caps = regex
        .captures(html)
        .ok_or_else(|| anyhow::anyhow!("urlParams not found"))?;
    let json_str = caps.get(1).map(|m| m.as_str()).unwrap_or_default();
    let value: Value = serde_json::from_str(json_str).context("parse urlParams json")?;
    let obj = value
        .as_object()
        .ok_or_else(|| anyhow::anyhow!("urlParams not an object"))?;
    let mut params = HashMap::new();
    for (key, value) in obj {
        if let Some(value) = json_value_to_string(value) {
            params.insert(key.clone(), value);
        }
    }
    Ok(params)
}

fn extract_vinfo(html: &str, url: &Url) -> Result<HashMap<String, String>> {
    let regex = Regex::new(r"vInfo\.(\w+)\s*=\s*'([^']*)'")
        .context("compile vInfo regex")?;
    let mut values = HashMap::new();
    for caps in regex.captures_iter(html) {
        let key = caps.get(1).map(|m| m.as_str()).unwrap_or_default();
        let value = caps.get(2).map(|m| m.as_str()).unwrap_or_default();
        if !key.is_empty() && !value.is_empty() {
            values.insert(key.to_string(), value.to_string());
        }
    }

    if !values.contains_key("type") {
        if let Some(value) = extract_var_string(html, "type") {
            values.insert("type".to_string(), value);
        }
    }

    if !values.contains_key("id") {
        if let Some(value) = extract_var_string(html, "videoId") {
            values.insert("id".to_string(), value);
        }
    }

    if !values.contains_key("hash") {
        if let Some(value) = extract_hash_from_url(url) {
            values.insert("hash".to_string(), value);
        }
    }

    Ok(values)
}

fn extract_var_string(html: &str, name: &str) -> Option<String> {
    let pattern = format!(r#"var {} = \"([^\"]+)\""#, name);
    let regex = Regex::new(&pattern).ok()?;
    regex
        .captures(html)
        .and_then(|caps| caps.get(1))
        .map(|m| m.as_str().to_string())
}

fn extract_hash_from_url(url: &Url) -> Option<String> {
    let path = url.path();
    let regex = Regex::new(r"/([a-f0-9]{32})/").ok()?;
    regex
        .captures(path)
        .and_then(|caps| caps.get(1))
        .map(|m| m.as_str().to_string())
}

fn json_value_to_string(value: &Value) -> Option<String> {
    match value {
        Value::Null => None,
        Value::Bool(val) => Some(val.to_string()),
        Value::Number(num) => Some(num.to_string()),
        Value::String(val) => Some(val.clone()),
        _ => Some(value.to_string()),
    }
}

#[derive(Debug, Deserialize)]
struct KodikResponse {
    links: HashMap<String, Vec<KodikLink>>,
}

#[derive(Debug, Deserialize)]
struct KodikLink {
    src: String,
}

async fn pick_playable_kodik_link(
    client: &reqwest::Client,
    headers: &HeaderMap,
    links: &HashMap<String, Vec<KodikLink>>,
) -> Result<String> {
    let mut candidates: Vec<(u32, String)> = Vec::new();
    for (key, items) in links {
        let Ok(quality) = key.parse::<u32>() else {
            continue;
        };
        let Some(item) = items.first() else {
            continue;
        };
        if let Ok(url) = decode_kodik_url(&item.src, key) {
            candidates.push((quality, url));
        }
    }

    if candidates.is_empty() {
        return Err(anyhow::anyhow!("no kodik candidates"));
    }

    candidates.sort_by(|a, b| b.0.cmp(&a.0));

    for (_, url) in candidates {
        if probe_kodik_url(client, headers, &url).await? {
            return Ok(url);
        }
    }

    Err(anyhow::anyhow!("no playable kodik links"))
}

async fn probe_kodik_url(
    client: &reqwest::Client,
    headers: &HeaderMap,
    url: &str,
) -> Result<bool> {
    let response = client
        .get(url)
        .headers(headers.clone())
        .send()
        .await
        .context("probe kodik url")?;

    if !response.status().is_success() {
        return Ok(false);
    }

    if url.contains(".m3u8") {
        let body = response.text().await.context("read m3u8 body")?;
        return Ok(body.contains("#EXTM3U"));
    }

    Ok(true)
}

fn decode_kodik_url(encoded: &str, quality_key: &str) -> Result<String> {
    if encoded.ends_with(".m3u8") {
        return Ok(prefix_https(encoded));
    }

    let mut base64_value = decrypt_rot(encoded);
    base64_value = base64_value.replace('-', "+").replace('_', "/");
    base64_value = base64_value
        .chars()
        .filter(|ch| ch.is_ascii_alphanumeric() || *ch == '+' || *ch == '/' || *ch == '=')
        .collect();
    while base64_value.len() % 4 != 0 {
        base64_value.push('=');
    }

    let decoded = BASE64_ENGINE
        .decode(base64_value.as_bytes())
        .context("decode kodik base64")?;
    let decoded = String::from_utf8(decoded).context("decode kodik url utf8")?;
    let mut decoded = prefix_https(&decoded);

    if quality_key == "720" && decoded.contains("/480.mp4:") {
        decoded = decoded.replace("/480.mp4:", "/720.mp4:");
    }

    Ok(decoded)
}

fn decrypt_rot(value: &str) -> String {
    let mut output = String::with_capacity(value.len());
    for ch in value.chars() {
        if ch.is_ascii_uppercase() {
            let base = ch as u8 - b'A';
            let rotated = (base + 18) % 26 + b'A';
            output.push(rotated as char);
        } else if ch.is_ascii_lowercase() {
            let base = ch as u8 - b'a';
            let rotated = (base + 18) % 26 + b'a';
            output.push(rotated as char);
        } else {
            output.push(ch);
        }
    }
    output
}

fn prefix_https(value: &str) -> String {
    if value.starts_with("http://") || value.starts_with("https://") {
        value.to_string()
    } else if value.starts_with("//") {
        format!("https:{}", value)
    } else {
        format!("https://{}", value.trim_start_matches('/'))
    }
}
