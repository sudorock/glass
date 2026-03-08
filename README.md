# glass

A Clojure utils library

## `glass.token`

`glass.token` uses a Python environment that already has `tiktoken` installed.

For local development in this repo:

```bash
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
export GLASS_PYTHON_EXECUTABLE="$PWD/.venv/bin/python"
```

Use `glass.token` from a REPL:

```clojure
(require '[glass.token :as token])

(token/count-text "hello world")
(token/count-file "README.md")
```

For consumers of Glass, point `GLASS_PYTHON_EXECUTABLE` at any Python interpreter or virtualenv that has `tiktoken` installed before loading `glass.token`.
