use crate::domain::ProviderId;
use crate::providers::{AnimeProvider, MetadataProvider};

pub struct ProviderFactory {
    pub id: &'static str,
    pub build: fn() -> Box<dyn AnimeProvider>,
}

inventory::collect!(ProviderFactory);

pub struct MetadataProviderFactory {
    pub id: &'static str,
    pub build: fn() -> Box<dyn MetadataProvider>,
}

inventory::collect!(MetadataProviderFactory);

pub struct ProviderRegistry {
    providers: Vec<Box<dyn AnimeProvider>>,
    metadata_providers: Vec<Box<dyn MetadataProvider>>,
}

impl ProviderRegistry {
    pub fn load() -> Self {
        let mut providers = Vec::new();
        for factory in inventory::iter::<ProviderFactory> {
            providers.push((factory.build)());
        }

        let mut metadata_providers = Vec::new();
        for factory in inventory::iter::<MetadataProviderFactory> {
            metadata_providers.push((factory.build)());
        }

        Self {
            providers,
            metadata_providers,
        }
    }

    pub fn providers(&self) -> &[Box<dyn AnimeProvider>] {
        &self.providers
    }

    pub fn metadata_providers(&self) -> &[Box<dyn MetadataProvider>] {
        &self.metadata_providers
    }

    pub fn get(&self, id: &ProviderId) -> Option<&dyn AnimeProvider> {
        self.providers
            .iter()
            .find(|provider| &provider.id() == id)
            .map(|provider| provider.as_ref())
    }

    pub fn get_metadata(&self, id: &ProviderId) -> Option<&dyn MetadataProvider> {
        self.metadata_providers
            .iter()
            .find(|provider| &provider.id() == id)
            .map(|provider| provider.as_ref())
    }
}

#[cfg(test)]
mod tests;
