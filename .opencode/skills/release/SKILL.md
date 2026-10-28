---
name: Release
description: Cut a new GitHub release of Naruto Mod with the shortest safe path. Use when the user asks to make a release, publish, tag, or ship a new version.
---

# Release (shortest safe path)

The scheduled **release-please** pipeline does not work in this fork. Release-please, `manual-release.yml`
and the CurseForge/Modrinth/Discord uploads all need secrets that are not configured in
`StandarterDF/Naruto-Mod` (`gh secret list` is empty), so a release here is cut **by hand** and then
published with `gh release create`. That is the established path: `1.9.0`, `1.10.0`, `1.11.0` were all
shipped this way (tag + GitHub release + `NarutoMod-<mc>-<ver>-universal.jar`).

## gh points at the wrong repo

An `upstream` remote (`sekwah41/Naruto-Mod`) exists, so bare `gh` commands resolve to the **upstream**
repo and silently return `[]` / 404. Always pass the repo:

```
gh ... -R StandarterDF/Naruto-Mod
```

## Pre-flight (one command)

```
python tools/release_status.py
```

Prints version, git state, commits since the last tag, `gh` auth and the last runs. Fix what it flags:
uncommitted changes (commit or ask the user - some files are their scratch notes) and a non-`master`
branch. Treat `(no runs)` / empty run lists from plain `gh` as the wrong-repo symptom above, not as
"nothing runs".

## Fast path - normal release

1. **Version.** Conventional commits since the last tag: `feat` -> minor, `fix` -> patch, breaking -> major.
   Bump **both** `version.txt` and `.release-please-manifest.json` to the same value (write `version.txt`
   with a plain LF newline, no extra blank lines - `build.gradle` strips `\r`).
2. **CHANGELOG entry.** Insert right under `# Changelog`, GitHub style, with a compare link:
   `## [X.Y.Z](https://github.com/StandarterDF/Naruto-Mod/compare/v<prev>...vX.Y.Z) (YYYY-MM-DD)` plus
   `### Features` / `### Bug Fixes` bullets and a `**Full Changelog**` line. Keep bullets short and
   player-facing.
3. **Build the release jar** (must be `IS_RELEASE=true`, otherwise the name keeps `-SNAPSHOT`):
   `$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; $env:IS_RELEASE='true'; .\gradlew.bat build --offline`
   Artifact: `build/libs/NarutoMod-1.20-<ver>-universal.jar`. If the build fails on duplicate datagen
   resources, the `duplicatesStrategy` handling in `build.gradle` is the place to look.
4. **Commit + tag + push.**
   ```
   git add -A
   git commit -m "chore: release X.Y.Z"
   git tag -a vX.Y.Z -m "X.Y.Z"
   git push origin master
   git push origin vX.Y.Z
   ```
5. **Publish.**
   ```
   gh release create vX.Y.Z -R StandarterDF/Naruto-Mod --title "X.Y.Z" --latest \
     --notes-file tmp/release-notes-X.Y.Z.md build/libs/NarutoMod-1.20-X.Y.Z-universal.jar
   ```
   Write the notes into `tmp/` (git-ignored) first: a one-line summary, then `**Features**` /
   `**Bug Fixes**` style bullets, and the SekCore Lib reminder. Publish as a **normal** (non-prerelease)
   release: every shipped release `v1.8.2`..`v1.11.0` is `isPrerelease: false` and the newest one is
   `Latest`. The `"prerelease": true` in `release-please-config.json` is dead config - release-please
   never runs - do not copy it.

## What NOT to do (saves time)

- Do not `gh workflow run release-please.yml` or `manual-release.yml` and do not wait for them: with no
  `PAT` release-please dies on `Input required and not supplied: token`, and `manual-release` never
  creates a tag or a GitHub release - it only tries to upload to CurseForge/Discord.
- Do not look for or merge a release-please PR; there is none.
- Do not upload `-deobf` or `-sources` jars; releases ship the universal jar only.
- Do not run the dev client/server or in-game tests; the user tests those.
- Do not run `runData`/datagen unless resources changed.
- Do not poll `gh run list` in a loop. The only workflow you can count on green is
  `pages build and deployment`, so it is not a release signal.
- Do not re-download assets; they are cached, keep `--offline` unless deps changed.
- Do not force-push or rewrite history to censor a stray file in the release commit; say so in the
  report instead.

## Verify after

```
gh release view vX.Y.Z -R StandarterDF/Naruto-Mod --json tagName,isPrerelease,isLatest,assets
```

The tag exists, the release is `isPrerelease: false`, `isLatest: true` and exactly one asset
(`NarutoMod-1.20-X.Y.Z-universal.jar`) is `uploaded`. `git status --short` is clean. If you already
published it wrong (e.g. as a prerelease), repair instead of re-creating:
`gh release edit vX.Y.Z -R StandarterDF/Naruto-Mod --prerelease=false --latest`.

## Report back

Version, commit and tag pushed, artifact name, release URL - and, if it still applies, the one-liner
that the automated pipeline stays dead until `PAT` / `MODRINTH_TOKEN` / `CURSE_API` / `DISCORD_WEBHOOK`
secrets are added to the fork. Nothing else.
