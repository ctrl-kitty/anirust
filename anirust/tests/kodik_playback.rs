use anirust::player::resolve_playback;
use anirust::providers::yummy::YummyProvider;
use anirust::providers::AnimeProvider;
use anirust::domain::{Episode, ProviderStatus};
use std::time::{SystemTime, UNIX_EPOCH};

#[tokio::test]
async fn kodik_m3u8_is_playable() {
    setup_temp_config();

    let provider = YummyProvider::new();
    let search = provider.search("naruto").await;
    assert!(
        matches!(search.status, ProviderStatus::Ok | ProviderStatus::Partial),
        "search failed with status {:?}",
        search.status
    );

    let results = search.data.unwrap_or_default();
    assert!(!results.is_empty(), "no results for naruto");

    let target = results
        .iter()
        .find(|anime| contains_naruto(&anime.title))
        .or_else(|| results.iter().next())
        .expect("no naruto results returned");

    let yummy_id = target
        .id
        .yummy_id
        .expect("naruto result missing yummy id");
    let episodes_result = provider.episodes(&yummy_id.to_string()).await;
    assert!(
        matches!(
            episodes_result.status,
            ProviderStatus::Ok | ProviderStatus::Partial
        ),
        "episodes status {:?}",
        episodes_result.status
    );

    let episodes = episodes_result.data.unwrap_or_default();
    assert!(!episodes.is_empty(), "no episodes for naruto");

    let candidate = episodes
        .iter()
        .find(|episode| has_2x2_dubbing(episode) && is_kodik_link(episode));

    let candidate = candidate.expect("no 2x2 kodik episodes found");
    let iframe_url = candidate
        .iframe_url
        .as_ref()
        .expect("missing iframe url")
        .as_str()
        .to_string();

    let resolved = resolve_playback(&iframe_url)
        .await
        .expect("resolve playback failed");

    let client = reqwest::Client::new();
    let mut request = client.get(&resolved.url);
    for (key, value) in resolved.headers {
        request = request.header(&key, &value);
    }

    let response = request.send().await.expect("request failed");
    assert!(response.status().is_success(), "status {}", response.status());
    let body = response.text().await.expect("read body failed");
    assert!(
        body.contains("#EXTM3U"),
        "expected m3u8 playlist, got {} bytes",
        body.len()
    );
}

fn setup_temp_config() {
    let mut dir = std::env::temp_dir();
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_nanos();
    dir.push(format!("anirust-test-{}-{}", std::process::id(), now));
    std::fs::create_dir_all(&dir).expect("create temp config dir");
    std::env::set_var("XDG_CONFIG_HOME", dir);
}

fn has_2x2_dubbing(episode: &Episode) -> bool {
    episode.voice_variants.iter().any(|voice| {
        let label = voice.label.to_lowercase();
        label.contains("2x2") || label.contains("2х2")
    })
}

fn is_kodik_link(episode: &Episode) -> bool {
    episode
        .iframe_url
        .as_ref()
        .and_then(|url| url.host_str())
        .map(|host| host.to_lowercase().contains("kodik"))
        .unwrap_or(false)
}

fn contains_naruto(title: &str) -> bool {
    let value = title.to_lowercase();
    value.contains("naruto") || value.contains("наруто")
}
