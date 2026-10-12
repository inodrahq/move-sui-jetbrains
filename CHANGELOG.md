<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Move for Sui Changelog

## [Unreleased]

### Added
- Move 2024 language support: lexer, parser, highlighting, structure view, folding, breadcrumbs, live and file templates.
- Sui analyzer integration (errors, completion, hover, inlay hints, navigation, rename, quick fixes) with settings for lint level, auto-imports and inlay hints.
- Native name resolution, find usages, quick documentation, go to symbol/class, keyword completion and inspections (unused import, unresolved reference, key struct without `id: UID`).
- `sui move build` / `sui move test` run configurations with a test tree, gutter run icons and clickable compiler locations.
- Trace debugging of unit tests with breakpoints, stepping, frames and variables.
- Move.toml completion, validation and `local` path navigation; Move Registry search, hover documentation and "Add MVR Dependency".
- New Project wizard, New Move Module action, Tools | Sui Move menu, toolchain settings with suiup install, prettier-move formatting, optional compile-on-save diagnostics.
