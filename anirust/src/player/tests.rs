use super::*;
use crate::domain::PlayerKind;

#[test]
fn player_labels_and_support_flags() {
    assert_eq!(player_label(PlayerKind::Kodik), "Kodik");
    assert_eq!(player_label(PlayerKind::Direct), "Direct");
    assert_eq!(player_label(PlayerKind::Alloha), "Alloha");

    assert!(is_supported_kind(PlayerKind::Kodik));
    assert!(is_supported_kind(PlayerKind::Direct));
    assert!(!is_supported_kind(PlayerKind::Alloha));
    assert!(!is_supported_kind(PlayerKind::Unknown));
}

#[test]
fn player_order_prefers_kodik() {
    assert!(player_order(PlayerKind::Kodik) < player_order(PlayerKind::Direct));
    assert!(player_order(PlayerKind::Direct) < player_order(PlayerKind::Alloha));
}
