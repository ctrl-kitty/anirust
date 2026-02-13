use super::*;
use url::Url;

fn sample_anime(id: AnimeId, title: &str) -> Anime {
    Anime {
        id,
        title: title.to_string(),
        alt_titles: Vec::new(),
        synopsis: None,
        poster_url: None,
        source: None,
    }
}

#[test]
fn merge_prefers_shikimori_metadata() {
    let yummy = Anime {
        id: AnimeId {
            shikimori_id: Some(1),
            mal_id: Some(2),
            yummy_id: Some(10),
        },
        title: "Yummy Title".to_string(),
        alt_titles: vec!["Yummy Alt".to_string()],
        synopsis: Some("Yummy Synopsis".to_string()),
        poster_url: None,
        source: None,
    };
    let shiki = Anime {
        id: AnimeId {
            shikimori_id: Some(1),
            mal_id: Some(2),
            yummy_id: None,
        },
        title: "Shiki Title".to_string(),
        alt_titles: vec!["Shiki Alt".to_string()],
        synopsis: Some("Shiki Synopsis".to_string()),
        poster_url: Url::parse("https://example.com/poster.jpg").ok(),
        source: None,
    };

    let merged = merge_anime(shiki, yummy);
    assert_eq!(merged.title, "Shiki Title");
    assert_eq!(merged.id.yummy_id, Some(10));
    assert!(merged.alt_titles.iter().any(|title| title == "Yummy Title"));
    assert!(merged.alt_titles.iter().any(|title| title == "Shiki Alt"));
    assert_eq!(merged.synopsis.as_deref(), Some("Shiki Synopsis"));
    assert!(merged.poster_url.is_some());
}

#[test]
fn merge_results_only_when_ids_match() {
    let yummy = vec![sample_anime(
        AnimeId {
            shikimori_id: Some(1),
            mal_id: Some(2),
            yummy_id: Some(10),
        },
        "Yummy",
    )];
    let shiki = vec![sample_anime(
        AnimeId {
            shikimori_id: Some(99),
            mal_id: Some(2),
            yummy_id: None,
        },
        "Shiki",
    )];

    let merged = merge_results(yummy, shiki);
    assert_eq!(merged.len(), 1);
    assert_eq!(merged[0].title, "Yummy");
}
