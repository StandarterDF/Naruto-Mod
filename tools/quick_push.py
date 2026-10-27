#!/usr/bin/env python3
"""Commit all changes and push to origin/master with minimal steps.

Usage:
  python tools/quick_push.py "commit message" [--yes] [--no-push]
"""

import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def run(cmd, check=True):
    r = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True)
    if check and r.returncode != 0:
        print(r.stdout)
        print(r.stderr, file=sys.stderr)
        sys.exit(r.returncode)
    return r


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("message", nargs="?", default=None)
    ap.add_argument("--yes", action="store_true", help="skip confirmation")
    ap.add_argument("--no-push", action="store_true", help="commit only")
    args = ap.parse_args()

    rc, out, _ = run(["git", "status", "--short"], check=False)
    if not out.strip():
        print("Nothing to commit.")
        return

    print("Changes:")
    print(out)

    msg = args.message
    if not msg:
        msg = input("Commit message: ").strip()
    if not msg:
        print("Empty commit message; aborting.", file=sys.stderr)
        sys.exit(1)

    if not args.yes:
        ans = input(f'Commit as "{msg}" and push? [y/N] ').strip().lower()
        if ans not in ("y", "yes"):
            print("Aborted.")
            return

    run(["git", "add", "-A"])
    run(["git", "commit", "-m", msg])
    if not args.no_push:
        run(["git", "push", "origin", "master"])
    print("Done.")


if __name__ == "__main__":
    main()
