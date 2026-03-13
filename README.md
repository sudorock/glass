# glass

A Clojure utils library

## `glass.python.token`

`glass.python` uses a Python environment that already has `tiktoken` installed.

For local development in this repo:

```bash
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
```

Use `glass.python.token` from a REPL:

```clojure
(require '[glass.python :as py]
         '[glass.python.token :as token])

(def runtime (py/init "/path/to/python"))

(token/count-text runtime "hello world")
(token/count-text runtime
                  "hello <|endoftext|>"
                  {:allowed-special #{"<|endoftext|>"}})
(token/count-file runtime "README.md")
(py/runtime)
```

For consumers of Glass, pass any Python interpreter or virtualenv executable path that already has `tiktoken` installed to `glass.python/init`.

`count-text` and `count-file` also accept `:allowed-special` and `:disallowed-special`.
These mirror `tiktoken`, including the special `"all"` value.
