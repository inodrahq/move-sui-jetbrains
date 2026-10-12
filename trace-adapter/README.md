# trace-adapter

Bundles Mysten's Move trace debug adapter (the DAP server behind the VS Code "Move Trace Debugger",
`external-crates/move/crates/move-analyzer/trace-adapter` in the sui repository) into one self-contained
file that the plugin runs with Node.js: `src/main/resources/trace-adapter/server.js`.

The npm release of the adapter lags behind the compiler's debug-info format, so it is built from source at a
pinned sui commit (see `build.sh` and `src/main/resources/trace-adapter/SUI_COMMIT`). The bundle is committed;
to refresh it:

```sh
cd trace-adapter && SUI_COMMIT=<sha> ./build.sh
```
