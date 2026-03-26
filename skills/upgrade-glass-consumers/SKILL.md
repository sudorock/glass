---
name: upgrade-glass-consumers
description: Normalize local `deps.edn` consumers of `/Users/indy/dev/glass` to the canonical `sudorock/glass` Git dependency form and update direct usages based on the diff between each consumer's pinned SHA and the target published SHA. Use when an agent needs to propagate a published `glass` SHA across sibling repos under `/Users/indy/dev` or migrate legacy `aktopia/glass`, `nanyar/glass`, or `io.github.sudorock/glass` entries.
---

# Upgrade Glass Consumers

Update sibling repos after a published `glass` change.

## Target SHA

Use the explicit target SHA from the prompt when it is provided.

Otherwise read the `:git/sha` from `README.md` in `/Users/indy/dev/glass` and use that. That README value is the last published source commit, not the README follow-up commit.

## Canonical Dependency Form

Use this exact dependency shape in each consumer:

```clojure
sudorock/glass {:git/url "https://github.com/sudorock/glass"
                :git/sha "<target-sha>"}
```

Preserve any extra keys already present in the dep map, such as `:exclusions`. Only normalize the lib symbol, `:git/url`, and `:git/sha` or legacy `:sha`.

## Consumer Discovery

- Derive the workspace root as the parent of the repo root. In this setup that is `/Users/indy/dev`.
- Scan only `deps.edn`.
- Ignore `project.clj` silently.
- Ignore `/Users/indy/dev/glass` itself.
- Treat a file as a consumer if it mentions any known symbol or URL for this repo:
  - `sudorock/glass`
  - `io.github.sudorock/glass`
  - `aktopia/glass`
  - `nanyar/glass`
  - `https://github.com/sudorock/glass`
  - `https://github.com/sudorock/glass.git`
  - `https://github.com/aktopia/glass.git`

## Workflow

1. Resolve `target_sha`.
2. Find consumer `deps.edn` files with `rg`.
3. For each consumer:
   - Read the current dep entry and extract its pinned SHA from `:git/sha` or `:sha`.
   - Rewrite the dep entry to the canonical `sudorock/glass` form and keep any extra map keys.
   - If the old SHA already equals `target_sha`, stop after the dep normalization. Do not inspect code changes.
   - Run `git diff --name-only <old-sha>..<target-sha>` in `/Users/indy/dev/glass`.
   - If the diff is empty or additive only, stop after the dep change.
   - Inspect the exact change surface with `git diff --unified=0 <old-sha>..<target-sha>`.
   - Search the consumer for concrete breakpoints revealed by that diff:
     - removed or renamed public vars
     - namespace renames or moved requires
     - literal keyword or option renames
     - call-shape changes that are visible in the diff
   - Patch only direct matches backed by the diff.
   - Current concrete example: if the diff renames `:tls?` to `:tls` in `glass.service.qdrant`, update matching consumer configs or call sites from `:tls?` to `:tls`.
4. Leave the consumer repo edited and uncommitted.
5. Report, for each consumer, the old SHA, target SHA, dep normalization, and any code changes made.

## Stop Conditions

- Do not invent broad refactors from a long diff.
- If a consumer change cannot be justified by a concrete diff hunk plus a direct usage match, leave the code alone and say so.
- Do not touch non-consumer repos.
- Do not commit or push consumer repos.
