---
name: Skilltree Apply
description: Apply a saved skill-tree layout from tmp/skilltree-edits.json into Skill.java and both language files, verify it, refresh the wiki tables and compile. Use whenever the user says "примени правки", "apply the edits", saved the skill-tree editor, or sends a tmp/skilltree-edits.json.
---

# Apply saved skill-tree edits

The user lays out the tree by hand in the browser editor. Their saved file is the source of
truth. **Do not re-place nodes, do not second-guess coordinates, do not read `Skill.java` to
"check the layout", and do not narrate 147 nodes.** Run the pipeline, report the numbers.

## Fast path (the whole job)

From the repo root, in this order:

```powershell
python tools\skilltree_preview.py --apply
python tools\skilltree_preview.py --apply   # second run must print "statements updated: 0"
python tools\build_docs_skills.py
python tools\build_docs_skills.py --check
python tools\build_docs_search.py
python tools\build_docs_search.py --check
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\gradlew.bat compileJava
```

That is it. `--apply` already lints, writes backups to `tmp/backup/<stamp>/`, patches
`Skill.java` (node table only), patches `naruto.skill.*.name/.desc` in `en_us.json` and
`ru_ru.json`, and regenerates `tmp/skilltree-preview.html`.

### What success looks like

```
applied tmp/skilltree-edits.json
  statements updated: 147
  added: ...
  removed: ...
  lang en: N added, N updated, N removed
  lang ru: N added, N updated, N removed
  backups: tmp/backup/20261019-140056
```

* `added` / `removed` / `moved branches` lines appear only when the JSON changed the set of
  nodes or moved a node to another branch — normal, just quote them.
* A **second** `--apply` printing `statements updated: 0` is the cheap proof that the source
  file now matches the payload exactly. If it is non-zero, the write is unstable: stop and
  report, do not loop.
* Console is cp1251-mangled for Russian labels; filter instead of dumping everything:
  `python tools\skilltree_preview.py --apply 2>&1 | Select-String "updated|added|removed|moved|backup|rejected"`.

### If it fails

* `edits rejected: ...` — the payload itself is invalid (duplicate path, self/unknown
  prerequisite). Report the exact lines to the user and stop. Do **not** invent paths.
* `applied` but `lang ru: ...` looks odd, or a name is empty — report; do not "fix" wording,
  the user wrote the strings.
* `statements updated: N != number of nodes` is fine (only changed statements count).
* Never hand-edit `Skill.java`, `en_us.json` or `ru_ru.json` to patch coordinates or prices.
  Fix the JSON and re-run `--apply`; hand edits get overwritten and break the round-trip test.

## Rules that save time (learned the hard way)

* **Apply is idempotent.** Running it twice is the verification. No need to diff 147 nodes.
* **Backups are automatic** in `tmp/backup/<stamp>/`. Never make your own copy first.
* **The file is `tmp/skilltree-edits.json`** (relative to the repo root). Do not ask for a path
  unless the user names a different file; then pass it positionally: `--apply <path>`.
* **Line endings are preserved** by the tool (`write_like`); git may warn `LF will be replaced
  by CRLF` — harmless, do not "fix" it.
* **`docs/skills.html` is generated.** `build_docs_skills.py --check` must say up to date;
  never touch the tables between `SKILLS:START/END` by hand.
* **`docs/search-index.js` is generated.** After page edits run `build_docs_search.py`.
* **Compile matters only for structural changes.** If the payload changed effect names, unlock
  keys or added nodes, run `compileJava`; a mismatched `SkillEffect` constant is the only way
  a pure layout edit breaks the build.
* **The wiki page counts as changed** when nodes/costs changed: `docs/skills.html` regeneration
  is part of the job, don't leave it for later.
* **`tools/skilltree_auto.py`** is a separate balancer (proposes costs / automatic layout). Do
  not run it as part of "apply the edits" — it would overwrite the user's hand layout.
* Agent stops after `compileJava`. In-game testing is the user's job.

## Useful extras

```powershell
python tools\skilltree_preview.py --check                  # lint only, no writes
python tools\skilltree_preview.py --serve --open           # open the editor (user side)
python tools\skilltree_auto.py --check                     # board-rule lint, read only
```

Report back in a few lines: statements updated, added/removed nodes, lang deltas, docs state,
compile result. Nothing else.
