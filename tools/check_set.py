"""Checks set.json against schema 1, with the standard library only: python tools/check_set.py [set.json].

A line names a project at a commit with the files its build made there; a hold keeps one artifact, or one
consumer's view of a project, on another commit; the platform names the JDK builds, Gradle and Cleanroom every
project builds against. Exit 0 when the file holds, 1 with one line per problem when it does not."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

HEX40 = re.compile(r"^[0-9a-f]{40}$")
HEX64 = re.compile(r"^[0-9a-f]{64}$")
NAME = re.compile(r"^[a-z0-9][a-z0-9._-]*$")
TOP = {"schema", "projects", "third", "platform", "holds", "doctor"}
LINE = {"url", "branch", "commit", "version", "artifacts"}
ARTIFACT = {"sha256", "file", "glob", "check", "mod_id"}
HOLD = {"commit", "artifacts", "reason", "since", "source"}
PLATFORM = {"java", "jdk", "gradle", "cleanroom", "cleanroomgradle", "plugin_repositories", "logic"}


def problems(data) -> list[str]:
    found: list[str] = []

    def need(condition, text):
        if not condition:
            found.append(text)

    if not isinstance(data, dict):
        return ["set.json is not an object"]
    need(data.get("schema") == 1, "schema must be 1")
    for key in sorted(set(data) - TOP):
        found.append(f"unknown key {key}")
    for name, line in (data.get("projects") or {}).items():
        where = f"projects.{name}"
        need(bool(NAME.match(name)), f"{where}: not a project name")
        if not isinstance(line, dict):
            found.append(f"{where}: not an object")
            continue
        for key in sorted(set(line) - LINE):
            found.append(f"{where}: unknown key {key}")
        need(bool(HEX40.match(str(line.get("commit", "")))), f"{where}.commit: not 40 hex")
        need(isinstance(line.get("branch"), str) and line["branch"], f"{where}.branch: missing")
        found += _artifacts(where, line.get("artifacts") or {})
    for name, hold in (data.get("holds") or {}).items():
        where = f"holds.{name}"
        need(bool(re.match(r"^[a-z0-9._-]+(:[a-z0-9._-]+)?(@[a-z0-9._-]+)?$", name)), f"{where}: not <project>[:<artifact>][@<consumer>]")
        if not isinstance(hold, dict):
            found.append(f"{where}: not an object")
            continue
        for key in sorted(set(hold) - HOLD):
            found.append(f"{where}: unknown key {key}")
        need(bool(HEX40.match(str(hold.get("commit", "")))), f"{where}.commit: not 40 hex")
        need(isinstance(hold.get("reason"), str) and hold["reason"], f"{where}.reason: missing")
        found += _artifacts(where, hold.get("artifacts") or {})
    platform = data.get("platform") or {}
    for key in sorted(set(platform) - PLATFORM):
        found.append(f"platform: unknown key {key}")
    need(isinstance(platform.get("java"), int), "platform.java: not a JDK major")
    for major, jdk in (platform.get("jdk") or {}).items():
        need(isinstance(jdk.get("build"), str), f"platform.jdk.{major}.build: missing")
        for system in ("linux", "windows"):
            archive = jdk.get(system) or {}
            need(str(archive.get("url", "")).startswith("https://"), f"platform.jdk.{major}.{system}.url: not https")
            need(bool(HEX64.match(str(archive.get("sha256", "")))), f"platform.jdk.{major}.{system}.sha256: not 64 hex")
    need(str(platform.get("java")) in (platform.get("jdk") or {}), "platform.java: no jdk entry for that major")
    gradle = platform.get("gradle") or {}
    need(str(gradle.get("url", "")).startswith("https://"), "platform.gradle.url: not https")
    need(bool(HEX64.match(str(gradle.get("sha256", "")))), "platform.gradle.sha256: not 64 hex")
    return found


def _artifacts(where: str, artifacts) -> list[str]:
    found = []
    for name, item in artifacts.items():
        at = f"{where}.artifacts.{name}"
        if not isinstance(item, dict):
            found.append(f"{at}: not an object")
            continue
        for key in sorted(set(item) - ARTIFACT):
            found.append(f"{at}: unknown key {key}")
        if not HEX64.match(str(item.get("sha256", ""))):
            found.append(f"{at}.sha256: not 64 hex")
        if not item.get("file"):
            found.append(f"{at}.file: missing")
    return found


def main(argv: list[str]) -> int:
    path = Path(argv[1] if len(argv) > 1 else "set.json")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        print(f"{path}: {error}")
        return 1
    found = problems(data)
    for line in found:
        print(f"{path}: {line}")
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
