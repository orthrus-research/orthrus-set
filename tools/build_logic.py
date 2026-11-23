"""Builds and tests build-logic at this commit, publishes it as the logic id (the tree id of build-logic/), and packs
the published Maven folder into build-logic/build/set-plugins-<id>.zip with sorted entries and one timestamp, so the
same tree packs the same bytes. Standard library only.

    python tools/build_logic.py

Gradle is the set's (the launcher Workbench names in WB_GRADLE), else gradle on PATH."""

from __future__ import annotations

import os
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LOGIC = ROOT / "build-logic"
STAMP = (1980, 1, 1, 0, 0, 0)


def main() -> int:
    logic = subprocess.run(["git", "rev-parse", "HEAD:build-logic"], cwd=ROOT, capture_output=True, text=True,
                           check=True).stdout.strip()
    published = LOGIC / "build" / "plugins"
    launcher = os.environ.get("WB_GRADLE")
    gradle = [sys.executable, launcher] if launcher else ["gradle"]
    done = subprocess.run([*gradle, "-p", str(LOGIC), "test", "publish", "--no-daemon", "--console=plain",
                           f"-Pset.logic={logic}", f"-Pset.publish={published}"], cwd=ROOT)
    if done.returncode != 0:
        return done.returncode
    packed = LOGIC / "build" / f"set-plugins-{logic}.zip"
    files = sorted(p for p in published.rglob("*") if p.is_file())
    with zipfile.ZipFile(packed, "w", zipfile.ZIP_DEFLATED) as archive:
        for path in files:
            info = zipfile.ZipInfo(path.relative_to(published).as_posix(), STAMP)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            archive.writestr(info, path.read_bytes())
    print(f"logic {logic}: {len(files)} file(s) packed into {packed.name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
