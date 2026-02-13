# Settings Module

Purpose
- Config loading, saving, and defaults.

Expandable
- Yes, add new settings fields as needed.

How to add a new setting
- Add fields to `Settings` in `anirust/src/settings/mod.rs`.
- Update `Default` impls for new fields.
- Update any logic that reads or writes settings.
- Add tests in `anirust/src/settings/tests.rs`.

Useful functions
- `Settings::load`
- `Settings::save`
- `Settings::to_toml`
- `ensure_config`
