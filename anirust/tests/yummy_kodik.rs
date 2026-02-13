use anirust::domain::{Episode, ProviderStatus};
use anirust::providers::yummy::YummyProvider;
use anirust::providers::AnimeProvider;
use std::time::{SystemTime, UNIX_EPOCH};

#[tokio::test]
async fn yummy_naruto_2x2_has_kodik_links() {
    setup_temp_config();

    let provider = YummyProvider::new();
    let result = provider.search("naruto").await;
    assert!(
        matches!(result.status, ProviderStatus::Ok | ProviderStatus::Partial),
        "search failed with status {:?}",
        result.status
    );

    let results = result.data.unwrap_or_default();
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
        matches!(episodes_result.status, ProviderStatus::Ok | ProviderStatus::Partial),
        "episodes status {:?} error {:?}",
        episodes_result.status,
        episodes_result.error
    );

    let episodes = episodes_result.data.unwrap_or_default();
    assert!(!episodes.is_empty(), "no episodes for naruto");

    let has_kodik = episodes
        .iter()
        .filter(|episode| has_2x2_dubbing(episode))
        .any(|episode| is_kodik_link(episode));

    let sample = episodes
        .iter()
        .take(3)
        .map(|episode| {
            let host = episode
                .iframe_url
                .as_ref()
                .and_then(|url| url.host_str())
                .unwrap_or("-");
            let dubbing = episode
                .voice_variants
                .first()
                .map(|voice| voice.label.as_str())
                .unwrap_or("-");
            format!("ep {:?} dub {} host {}", episode.number, dubbing, host)
        })
        .collect::<Vec<_>>()
        .join("; ");

    assert!(
        has_kodik,
        "expected kodik link for 2x2 dubbing; sample: {}",
        sample
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
