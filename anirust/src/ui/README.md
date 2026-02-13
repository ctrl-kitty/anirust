# UI Module

Purpose
- Ratatui TUI state, rendering, input handling, and playback flow.

Expandable
- Yes, new screens and flows are expected.

How to add a new view
- Update the `View` enum in `anirust/src/ui/app.rs`.
- Add any required state fields to `App`.
- Handle input in `anirust/src/ui/handlers.rs` and flow in `anirust/src/ui/handlers_flow.rs`.
- Use `CatalogService` for search/series/episodes orchestration.
- Render header/list/footer in `anirust/src/ui/app_view.rs` and `anirust/src/ui/render.rs`.
- Update footer hints in `App::footer_text`.
- Add tests in `anirust/src/ui/app_tests.rs` or `anirust/src/ui/handlers_tests.rs`.

Useful functions
- `App::set_status`
- `App::apply_episode_filter`
- `selection::{select_next, select_prev, select_first}`
- `input::InputState`
