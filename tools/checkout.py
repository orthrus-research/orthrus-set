"""Checks each sibling out at the commit set.json names, for a plain clone with no Workbench (standard library only).

    python tools/checkout.py [--root <folder that holds the siblings>] [project ...]

Each named project (default: every line) must already be cloned beside the Set, in a folder named after it. The
script fetches, checks the commit out detached, and prints the order to build in: a project after the ones whose
files it uses, as far as set.json says. A build made this way is a development build: its bytes are not checked
against the set."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent.parent


def git(folder: Path, *args: str) -> subprocess.CompletedProcess:
    return subprocess.run(["git", "-C", str(folder), *args], capture_output=True, text=True)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=(__doc__ or "").splitlines()[0])
    parser.add_argument("--root", type=Path, default=HERE.parent, help="the folder that holds the siblings")
    parser.add_argument("projects", nargs="*")
    args = parser.parse_args(argv)
    lines = json.loads((HERE / "set.json").read_text(encoding="utf-8")).get("projects") or {}
    names = args.projects or sorted(lines)
    unknown = [n for n in names if n not in lines]
    if unknown:
        print(f"set.json has no line for {', '.join(unknown)}")
        return 2
    failed = False
    for name in names:
        folder = next((p for p in args.root.iterdir() if p.is_dir() and p.name.lower() == name.lower()), None)
        commit = lines[name]["commit"]
        if folder is None:
            where = lines[name].get("url") or "its repository"
            print(f"{name}: not cloned under {args.root}; clone {where} there first")
            failed = True
            continue
        git(folder, "fetch", "--quiet", "origin")
        done = git(folder, "checkout", "--quiet", "--detach", commit)
        if done.returncode:
            print(f"{name}: cannot check out {commit[:12]}: {done.stderr.strip()}")
            failed = True
        else:
            print(f"{name}: {folder} at {commit[:12]}")
    print("build in this order: " + ", ".join(names))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
