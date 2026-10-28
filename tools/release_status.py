#!/usr/bin/env python3
"""Quick status for a GitHub release of Naruto Mod.

Prints: current version, git state, commits since last tag, gh auth, repo secrets,
last runs and the last release. Does not build or push anything.

Usage:
  python tools/release_status.py
"""

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
# The fork is the release target; the `upstream` remote makes bare `gh` resolve elsewhere.
REPO = "StandarterDF/Naruto-Mod"
# Secrets the automated pipeline needs. Without them everything is released by hand.
REQUIRED_SECRETS = ["PAT", "MODRINTH_TOKEN", "CURSE_API", "DISCORD_WEBHOOK"]


def run(cmd):
    try:
        r = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            cwd=ROOT,
        )
        return r.returncode, r.stdout.strip(), r.stderr.strip()
    except FileNotFoundError:
        return 127, "", "command not found"


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    print("== version ==")
    vfile = ROOT / "version.txt"
    print("version.txt:", vfile.read_text().strip() if vfile.exists() else "MISSING")
    manifest = ROOT / ".release-please-manifest.json"
    if manifest.exists():
        try:
            m = json.loads(manifest.read_text())
            print("release-please manifest:", m.get(".", "?"))
        except Exception as e:
            print("manifest parse error:", e)

    print("\n== git ==")
    rc, out, _ = run(["git", "status", "--short"])
    if out:
        print("UNCOMMITTED CHANGES:")
        print(out)
    else:
        print("working tree clean")
    rc, out, _ = run(["git", "rev-parse", "--abbrev-ref", "HEAD"])
    print("branch:", out)
    rc, out, _ = run(["git", "log", "-1", "--oneline"])
    print("HEAD:", out)
    rc, out, _ = run(["git", "describe", "--tags", "--abbrev=0"])
    print("last tag:", out if rc == 0 else "(none)")

    print("\n== commits since last tag ==")
    rc2, tag, _ = run(["git", "describe", "--tags", "--abbrev=0"])
    if rc2 == 0:
        rc, out, _ = run(["git", "log", "--oneline", f"{tag}..HEAD"])
        print(out if out else "(none)")
    else:
        print("(no tags)")

    print("\n== gh ==")
    print("repo:", REPO)
    rc, out, err = run(["gh", "auth", "status"])
    print(out or err)

    rc, out, _ = run(["gh", "secret", "list", "-R", REPO])
    names = [line.split()[0] for line in out.splitlines() if line.strip()]
    missing = [s for s in REQUIRED_SECRETS if s not in names]
    if rc != 0:
        print("secrets: (could not list)")
    elif missing:
        print("secrets: MISSING", ", ".join(missing))
        print(
            "  -> automated release (release-please / uploads) cannot run; release by hand."
        )
    else:
        print("secrets: ok")

    rc, out, err = run(["gh", "release", "list", "-R", REPO, "--limit", "3"])
    print("last releases:")
    print(out if out else (err or "(none)"))

    rc, out, err = run(["gh", "run", "list", "-R", REPO, "--limit", "5"])
    print("last runs:")
    print(out if out else (err or "(no runs)"))

    print("\n== build artifact ==")
    libs = ROOT / "build" / "libs"
    if libs.exists():
        jars = sorted(libs.glob("*.jar"))
        print("jars:", [j.name for j in jars] if jars else "(none)")
    else:
        print("no build/libs yet")


if __name__ == "__main__":
    main()
