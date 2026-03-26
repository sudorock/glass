---
name: publish-glass
description: Publish the `/Users/indy/dev/glass` repo and keep its README install snippet aligned with the last published source commit. Use when an agent needs to publish `glass`, create a source commit if the repo is dirty, push the current branch, or create the README-only follow-up commit that pins `sudorock/glass` to the latest published source SHA.
---

# Publish Glass

Publish `glass` in two phases: publish the source commit first, then create a README-only follow-up commit that points at that published source SHA.

## Canonical README Snippet

Use this exact dependency shape in `README.md`:

```clojure
sudorock/glass {:git/url "https://github.com/sudorock/glass"
                :git/sha "<source-sha>"}
```

Treat `<source-sha>` as the published source commit. Do not try to make the README point at the SHA of the README follow-up commit.

## Workflow

1. Run from `/Users/indy/dev/glass`.
2. Inspect `git status -sb`, `git branch --show-current`, and `git diff-tree --no-commit-id --name-only -r HEAD`.
3. Decide the source commit.
   - If the repo is dirty, require a commit message if the prompt did not provide one, then run `git add -A` and `git commit -m "<message>"`.
   - If the repo is clean, use `HEAD` as the candidate source commit.
4. Detect the existing README follow-up case before creating new commits.
   - If the repo is clean, `HEAD` changed only `README.md`, and the README `:git/sha` already equals `git rev-parse HEAD^`, treat `HEAD` as the existing README follow-up commit.
   - In that case, push the current branch if it is ahead, then stop. Do not create another commit.
5. Publish the source commit with `git push origin <current-branch>`.
6. Record `source_sha` as `git rev-parse HEAD` immediately after step 5 and before editing `README.md`.
7. Rewrite only the installation snippet in `README.md` to the canonical form above using `source_sha`.
8. If the README already matches that canonical snippet, stop. Do not create a no-op commit.
9. Create the README-only follow-up commit with `git add README.md` and `git commit -m "Update README git SHA"`.
10. Push the current branch again.
11. Report both SHAs:
   - `source_sha`: the commit consumers should pin.
   - `readme_commit_sha`: the follow-up README commit.

## Notes

- Use the current branch. Do not create a release branch.
- Keep the README change narrow. Do not rewrite unrelated README sections.
- If the repo starts dirty and already contains user edits to `README.md`, let those ship in the source commit, then apply the snippet rewrite in the README-only follow-up commit.
