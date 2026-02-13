use super::*;

#[test]
fn registry_loads_known_providers() {
    let registry = ProviderRegistry::load();
    let mut ids: Vec<String> = registry
        .providers()
        .iter()
        .map(|provider| provider.id().to_string())
        .collect();
    ids.sort();

    assert!(ids.contains(&"shikimori".to_string()));
    assert!(ids.contains(&"yummy".to_string()));
}
