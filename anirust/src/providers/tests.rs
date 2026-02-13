use super::MetadataProvider;
use crate::domain::{Anime, ProviderId, ProviderResult};
use async_trait::async_trait;

struct SearchOnlyProvider;

#[async_trait]
impl MetadataProvider for SearchOnlyProvider {
    fn id(&self) -> ProviderId {
        ProviderId::from("search-only")
    }

    async fn search(&self, _query: &str) -> ProviderResult<Vec<Anime>> {
        ProviderResult::ok(Vec::new())
    }
}

#[test]
fn metadata_provider_only_requires_search() {
    let provider = SearchOnlyProvider;
    let _id = provider.id();
    let _provider_ref: &dyn MetadataProvider = &provider;
}
