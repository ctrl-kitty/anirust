use super::handlers_flow::{select_series_index, sort_search_results};
use crate::domain::{Anime, AnimeId, ProviderId, SeriesEntry};

#[test]
fn selects_series_by_matching_yummy_id() {
    let anime_id = AnimeId {
        shikimori_id: None,
        mal_id: None,
        yummy_id: Some(119),
    };
    let provider = ProviderId::from("yummy");
    let series = vec![
        SeriesEntry {
            id: "111".to_string(),
            title: "Наруто".to_string(),
            order: Some(0),
            provider: provider.clone(),
        },
        SeriesEntry {
            id: "119".to_string(),
            title: "Наруто: Ураганные хроники".to_string(),
            order: Some(1),
            provider,
        },
    ];

    let index = select_series_index(&anime_id, &series);
    assert_eq!(index, Some(1));
}

#[test]
fn no_selection_when_no_yummy_id() {
    let anime_id = AnimeId {
        shikimori_id: Some(1735),
        mal_id: Some(1735),
        yummy_id: None,
    };
    let provider = ProviderId::from("yummy");
    let series = vec![SeriesEntry {
        id: "119".to_string(),
        title: "Наруто: Ураганные хроники".to_string(),
        order: Some(1),
        provider,
    }];

    let index = select_series_index(&anime_id, &series);
    assert_eq!(index, None);
}

#[test]
fn search_sort_prefers_exact_match() {
    let mut items = vec![
        Anime {
            id: AnimeId::default(),
            title: "Наруто: Ураганные хроники".to_string(),
            alt_titles: Vec::new(),
            synopsis: None,
            poster_url: None,
            source: None,
        },
        Anime {
            id: AnimeId::default(),
            title: "Наруто".to_string(),
            alt_titles: Vec::new(),
            synopsis: None,
            poster_url: None,
            source: None,
        },
        Anime {
            id: AnimeId::default(),
            title: "Боруто".to_string(),
            alt_titles: Vec::new(),
            synopsis: None,
            poster_url: None,
            source: None,
        },
    ];

    sort_search_results("Наруто", &mut items);
    assert_eq!(
        items.first().map(|anime| anime.title.as_str()),
        Some("Наруто")
    );
}
