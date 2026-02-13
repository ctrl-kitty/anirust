use crate::domain::{AnimeId, Episode, PlayerKind, ProviderId};
use crate::registry::ProviderRegistry;
use crate::settings;
use crate::test_support::{env_lock, setup_temp_config};
use crate::ui::app::App;

#[test]
fn saves_default_dubbing_for_series() {
    let _lock = env_lock();
    let _dir = setup_temp_config();
    settings::ensure_config().expect("ensure config");
    let settings = settings::Settings::load().expect("load settings");
    let registry = ProviderRegistry::load();
    let mut app = App::new(registry, ProviderId::new("yummy"), settings);
    app.current_anime_id = Some(AnimeId {
        shikimori_id: Some(1735),
        mal_id: Some(1735),
        yummy_id: Some(119),
    });
    app.current_series_id = Some("119".to_string());

    app.save_default_dubbing("2x2".to_string())
        .expect("save dubbing");

    let saved = settings::Settings::load().expect("reload settings");
    let key = "yummy:shiki:1735:mal:1735";
    let value = saved
        .anime
        .defaults
        .get(key)
        .and_then(|entry| entry.dubbing.as_deref());
    assert_eq!(value, Some("2x2"));
}

#[test]
fn prefill_dubbing_selects_saved_default() {
    let _lock = env_lock();
    let _dir = setup_temp_config();
    settings::ensure_config().expect("ensure config");

    let mut settings = settings::Settings::load().expect("load settings");
    settings.anime.defaults.insert(
        "yummy:shiki:1735:mal:1735".to_string(),
        settings::AnimeDefaults {
            dubbing: Some("2x2".to_string()),
        },
    );
    settings.save().expect("save settings");

    let settings = settings::Settings::load().expect("reload settings");
    let registry = ProviderRegistry::load();
    let mut app = App::new(registry, ProviderId::new("yummy"), settings);
    app.current_anime_id = Some(AnimeId {
        shikimori_id: Some(1735),
        mal_id: Some(1735),
        yummy_id: Some(119),
    });
    app.current_series_id = Some("119".to_string());
    app.dubbing_options = vec!["2x2".to_string(), "Other".to_string()];

    app.prefill_dubbing();

    let selected = app.selected_dubbing_option().map(|value| value.as_str());
    assert_eq!(selected, Some("2x2"));
}

#[test]
fn select_last_watched_episode() {
    let _lock = env_lock();
    let _dir = setup_temp_config();
    settings::ensure_config().expect("ensure config");

    let mut settings = settings::Settings::load().expect("load settings");
    settings.anime.history.insert(
        "shiki:1735:mal:1735".to_string(),
        settings::AnimeHistory {
            last_episode_id: Some("e5".to_string()),
            last_episode_number: Some(5),
        },
    );
    settings.save().expect("save settings");

    let settings = settings::Settings::load().expect("reload settings");
    let registry = ProviderRegistry::load();
    let mut app = App::new(registry, ProviderId::new("yummy"), settings);
    app.current_anime_id = Some(AnimeId {
        shikimori_id: Some(1735),
        mal_id: Some(1735),
        yummy_id: Some(119),
    });
    app.current_series_id = Some("119".to_string());
    app.episodes_all = vec![
        Episode {
            id: "e1".to_string(),
            number: Some(1),
            title: None,
            iframe_url: None,
            voice_variants: Vec::new(),
            subtitle_variants: Vec::new(),
            player_kind: PlayerKind::Kodik,
            provider: ProviderId::from("yummy"),
        },
        Episode {
            id: "e5".to_string(),
            number: Some(5),
            title: None,
            iframe_url: None,
            voice_variants: Vec::new(),
            subtitle_variants: Vec::new(),
            player_kind: PlayerKind::Kodik,
            provider: ProviderId::from("yummy"),
        },
        Episode {
            id: "e6".to_string(),
            number: Some(6),
            title: None,
            iframe_url: None,
            voice_variants: Vec::new(),
            subtitle_variants: Vec::new(),
            player_kind: PlayerKind::Kodik,
            provider: ProviderId::from("yummy"),
        },
    ];

    app.apply_episode_filter();

    let selected = app.episodes_state.selected();
    assert_eq!(selected, Some(1));
}
