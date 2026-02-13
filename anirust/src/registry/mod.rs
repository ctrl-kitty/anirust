use crate::domain::ProviderId;
use crate::providers::AnimeProvider;

pub struct ProviderFactory {
    pub id: &'static str,
    pub build: fn() -> Box<dyn AnimeProvider>,
}

inventory::collect!(ProviderFactory);

pub struct ProviderRegistry {
    providers: Vec<Box<dyn AnimeProvider>>,
}

impl ProviderRegistry {
    pub fn load() -> Self {
        let mut providers = Vec::new();
        for factory in inventory::iter::<ProviderFactory> {
            providers.push((factory.build)());
        }

        Self { providers }
    }

    pub fn providers(&self) -> &[Box<dyn AnimeProvider>] {
        &self.providers
    }

    pub fn get(&self, id: &ProviderId) -> Option<&dyn AnimeProvider> {
        self.providers
            .iter()
            .find(|provider| &provider.id() == id)
            .map(|provider| provider.as_ref())
    }
}

#[cfg(test)]
mod tests;
