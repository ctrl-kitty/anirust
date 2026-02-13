use super::*;
use crate::test_support::{env_lock, setup_temp_config};

#[test]
fn defaults_are_written_and_loaded() {
    let _lock = env_lock();
    let _dir = setup_temp_config();

    let state = ensure_config().expect("ensure config");
    assert!(state.path.exists());

    let settings = Settings::load().expect("load settings");
    assert_eq!(settings.preferred_provider, "yummy");
    assert_eq!(settings.yummy.lang, "ru");
    assert_eq!(settings.player.command, "mpv");
    assert!(!settings.player.args.is_empty());
}

#[test]
fn save_round_trip_preserves_updates() {
    let _lock = env_lock();
    let _dir = setup_temp_config();

    ensure_config().expect("ensure config");
    let mut settings = Settings::load().expect("load settings");
    settings.preferred_provider = "shikimori".to_string();
    settings.audio.preferred_dubbings = vec!["2x2".to_string()];
    settings.save().expect("save settings");

    let settings = Settings::load().expect("reload settings");
    assert_eq!(settings.preferred_provider, "shikimori");
    assert_eq!(settings.audio.preferred_dubbings, vec!["2x2".to_string()]);
}
