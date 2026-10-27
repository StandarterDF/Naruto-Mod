---
name: Quick Push
description: Commit and push the current changes to origin/master with minimal steps. Use when the user asks to push, commit, save, or sync changes.
---

# Quick push (minimal path)

## Fast path

1. Check state:
   `git status --short`
   If empty, stop - nothing to push.
2. Stage everything that is not ignored:
   `git add -A`
3. Commit with a concise message (ask the user for one if not given; default to a one-line
   summary of the diff):
   `git commit -m "<message>"`
4. Push:
   `git push origin master`
5. Verify:
   `git status --short` (should be empty) and `git log -1 --oneline`.

## Helpers

- `python tools/quick_push.py "message"` does steps 2-4 with a confirmation prompt; pass `--yes`
  to skip the prompt. Use `--no-push` to commit only.
- `python tools/release_status.py` for a fuller picture before pushing.

## Rules that save time

- Do not amend published commits; make a new commit.
- Do not run builds/tests unless the user asks or the change touches code that must compile.
- Do not push to `upstream` (sekwah41) - only `origin` (StandarterDF).
- If there are untracked build artifacts, check `.gitignore` before `git add -A`.
- On push rejection, `git pull --rebase origin master` then push again; do not force-push.