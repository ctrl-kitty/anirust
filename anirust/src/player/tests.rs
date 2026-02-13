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

#[tokio::test]
async fn resolver_lookup_by_kind() {
    let resolved = resolve_playback_with_kind(
        PlayerKind::Direct,
        "https://example.com/video.mp4",
    )
    .await
    .expect("direct resolver");
    assert_eq!(resolved.url, "https://example.com/video.mp4");
    assert!(resolved.headers.is_empty());
}

#[tokio::test]
async fn unsupported_player_returns_error() {
    let err = resolve_playback_with_kind(PlayerKind::Alloha, "https://alloha.example/embed")
        .await
        .expect_err("unsupported player");
    assert!(err.to_string().contains("unsupported player: alloha"));
}
