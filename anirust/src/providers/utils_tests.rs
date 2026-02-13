use crate::providers::utils::{normalized_text, parse_url};

#[test]
fn normalized_text_trims_and_rejects_empty() {
    assert_eq!(
        normalized_text("  Naruto  ".to_string()),
        Some("Naruto".to_string())
    );
    assert_eq!(normalized_text("   ".to_string()), None);
}

#[test]
fn parse_url_accepts_https_or_protocol_relative() {
    let direct = parse_url("https://example.com/path").expect("direct url");
    assert_eq!(direct.scheme(), "https");
    assert_eq!(direct.host_str(), Some("example.com"));

    let relative = parse_url("//example.com/stream").expect("relative url");
    assert_eq!(relative.scheme(), "https");
    assert_eq!(relative.host_str(), Some("example.com"));
}
