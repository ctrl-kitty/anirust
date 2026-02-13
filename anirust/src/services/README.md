# Services Module

Purpose
- Shared orchestration logic used by CLI and TUI.

Expandable
- Yes, add new services as real use cases appear.

How to add a new service
- Put it in `anirust/src/services/` and export from `anirust/src/services/mod.rs`.
- Keep services pure and reusable across UI and CLI.
- Avoid adding placeholder types that are not used.

Useful functions
- `unify::unify_search`
- `catalog::CatalogService`
- `catalog::CatalogService::episodes_with_stats`
