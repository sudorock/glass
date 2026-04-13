# glass

Glass is my personal Clojure grab bag for building services, tooling, and integrations.

It collects small namespaces I reuse across projects for common data work, runtime helpers, and external system adapters.

## Who It Is For

Glass is primarily for my own use.

The repo is public, but it is not intended to provide a stable public API. Namespaces, behavior, and dependency choices may change or break at any time.

## Installation

Glass is currently consumed as a Git dependency.

```clojure
sudorock/glass {:git/url "https://github.com/sudorock/glass"
                :git/sha "77c9a16bb2b04a659a01dd6a633f3d48266371b3"}
```

Pin to the commit or tag you want to depend on.

## Library Structure

The code currently falls into three broad groups.

### Core Utilities

General-purpose namespaces for data and text shaping, JSON, keywords, timestamps, URLs, UUIDs, exception data, and fractional indexing.

### Runtime Helpers

Reusable helpers for HTTP, shell execution, templating, scheduling, logging, Python interop, and Integrant/Aero-based system setup.

### Integrations

Optional integrations for MCP servers, OpenAI, Qdrant, and Datascript with SQLite persistence.

## Suggested Structure

The current namespace layout follows a simple split:

- Keep broadly reusable code in `glass.*`
- Keep service adapters in `glass.service.*`
- Keep database adapters in `glass.db.*`
- Keep MCP-specific code in `glass.mcp.*`

This is only a rough organizing principle, not a stability promise.

## Development

Glass uses `deps.edn`.

Check outdated dependencies with:

```bash
clojure -M:outdated
```

Some namespaces depend on external runtimes or services.

- Python interop expects a Python executable with `tiktoken` installed
- Integration namespaces may require service credentials or a running local service

For local Python setup in this repo:

```bash
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
```

## License

Glass is available under the MIT License. See [LICENSE](/Users/indy/dev/glass/LICENSE).
