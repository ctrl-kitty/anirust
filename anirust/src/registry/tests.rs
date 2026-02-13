use super::*;

#[test]
fn registry_loads_known_providers() {
    let registry = ProviderRegistry::load();
    let mut anime_ids: Vec<String> = registry
        .providers()
        .iter()
        .map(|provider| provider.id().to_string())
        .collect();
    anime_ids.sort();

    let mut metadata_ids: Vec<String> = registry
        .metadata_providers()
        .iter()
        .map(|provider| provider.id().to_string())
        .collect();
    metadata_ids.sort();

    assert!(anime_ids.contains(&"yummy".to_string()));
    assert!(metadata_ids.contains(&"shikimori".to_string()));
}
