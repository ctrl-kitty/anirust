# Registry Module

Purpose
- Runtime registry of providers using `inventory`.

Expandable
- Yes, new providers should register here.

How to add a new provider
- Implement `AnimeProvider` or `MetadataProvider`.
- Register via `inventory::submit!` with `ProviderFactory` or
  `MetadataProviderFactory`.
- Use `ProviderRegistry::load()` to access registered providers.

Useful functions
- `ProviderRegistry::load`
- `ProviderRegistry::get`
- `ProviderRegistry::providers`
- `ProviderRegistry::get_metadata`
- `ProviderRegistry::metadata_providers`
