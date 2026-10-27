---
name: Release
description: Cut a new GitHub release of Naruto Mod with the shortest safe path. Use when the user asks to make a release, publish, tag, or ship a new version.
---

# Release (shortest safe path)

This repo uses **release-please** (manifest mode): pushing to `master` is what creates the release
PR/version bump, and merging it creates the GitHub release + jars. Do not hand-bump versions or
edit CHANGELOG.md.

## Pre-flight (one command)

```
python tools/release_status.py
```

It prints version, git state, commits since last tag, gh auth and last workflow runs. Fix anything
it flags (uncommitted changes, wrong branch) before proceeding.

## Fast path - normal release

1. Make sure the working tree is clean and you are on `master`:
   `git status --short` must be empty, `git branch --show-current` == `master`.
2. Build once, locally, to catch compile errors before pushing:
   `$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\gradlew.bat compileJava`
   (full `build` only if you changed resources/deps.)
3. Push:
   `git push origin master`
4. That is it. release-please runs on the push, opens/updates a release PR, and on merge creates
   the tag + GitHub release + CurseForge/Modrinth/Discord uploads.

## Fast path - emergency manual release

Only when release-please is stuck or a hotfix must ship now:

```
gh workflow run manual-release.yml
gh run watch
```

## What NOT to do (saves time)

- Do not run the dev client/server or in-game tests; the user tests those.
- Do not edit `version.txt`, `.release-please-manifest.json` or `CHANGELOG.md` by hand.
- Do not run `runData`/datagen unless resources changed.
- Do not poll `gh run list` in a loop; use `gh run watch` once.
- Do not re-download assets; they are cached.

## Verify after

- `gh run list --limit 3` shows green.
- `gh release view` shows the new tag and jars.
- If the release PR is open, merge it; do not create a second one.

## Report back

Version, commit pushed, workflow run status, and the release URL. Nothing else.