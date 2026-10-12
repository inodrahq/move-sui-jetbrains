# Move for Sui

JetBrains IDE plugin for [Sui Move](https://docs.sui.io/concepts/sui-move-concepts). Works in IntelliJ IDEA, RustRover, WebStorm, PyCharm, GoLand, CLion and every other JetBrains IDE from 2026.1.4.

<!-- Plugin description -->
First-class **Sui Move** support for JetBrains IDEs.

- Native Move 2024 parser: syntax and semantic highlighting, structure view, breadcrumbs, folding, brace matching, live templates, file templates
- Semantic features via the Sui analyzer (`sui analyzer`): errors as you type, completion with auto-import, hover docs, inlay type and parameter hints, go to definition, find usages, rename, quick fixes
- Navigation that keeps working without the analyzer: native name resolution across modules, packages and the Sui framework, quick documentation, go to symbol
- `sui move build` / `sui move test` run configurations with a test tree, clickable errors and gutter run icons on `#[test]` functions
- Trace debugging of Move unit tests: breakpoints, stepping, call stack and variables (powered by Mysten's trace debug adapter)
- `Move.toml` completion, validation and dependency navigation; Move Registry (MVR) search, hover and "Add MVR Dependency"; dependency sources (git and MVR, pinned in `Move.lock`) attached from the local Move cache for navigation
- New Project wizard, "New Move Module" action, toolchain settings with one-click `suiup` install, `Reformat Code` via prettier-move
<!-- Plugin description end -->

## Requirements

- A JetBrains IDE 2026.1.4 or newer.
- The [`sui`](https://docs.sui.io/guides/developer/getting-started/sui-install) CLI, 1.45 or newer (`suiup install sui`). The plugin starts `sui analyzer` for language intelligence and runs `sui move` for builds and tests; it does not bundle its own analyzer, so semantic features track the installed `sui` version. The binary is auto-detected (PATH, `~/.local/bin`, `~/.sui/bin`, `~/.cargo/bin`, Homebrew) and the plugin offers to install or update it through `suiup` when it is missing or too old.
- Optional: Node.js for trace debugging; `npm i -g prettier @mysten/prettier-plugin-move` for `Reformat Code`.

Settings live under **Settings | Languages & Frameworks | Move for Sui** (sui path, analyzer options, Node.js path, compile on save).

## Development

```sh
./gradlew runIde          # IntelliJ IDEA sandbox; pass a Move package as --args="/path/to/package"
./gradlew runRustRover    # locally installed RustRover (/Applications/RustRover.app)
./gradlew test            # parser, resolve, run-config and (with sui installed) analyzer + trace-debugger integration tests
                          # the integration tests use the sample package in src/test/testData/counter-package
./gradlew buildPlugin verifyPlugin
```

Needs JDK 21. The lexer and parser are generated from `src/main/grammars` into `build/generated`. The trace debug
adapter bundle in `src/main/resources/trace-adapter` is built from the sui repository by `trace-adapter/build.sh`.

Parser regression against the whole Sui framework: `MOVE_FRAMEWORK_DIR=~/.move/git/<sui checkout>/crates/sui-framework/packages ./gradlew test --tests '*MoveParsingTest*'`.

## Releasing

`.github/workflows/release.yml` builds, verifies, signs and drafts a GitHub release on demand. Publishing to the
JetBrains Marketplace is a separate, manually approved job (`publish` input) that needs the `CERTIFICATE_CHAIN`,
`PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD` and `PUBLISH_TOKEN` secrets. Nothing is published automatically.

## License

Apache-2.0
