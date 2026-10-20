#!/usr/bin/env python3
"""Check repository-relative Markdown links and OpenSpec coverage in docs/README.md."""

from __future__ import annotations

import os
import re
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit


ROOT = Path(__file__).resolve().parents[1]
DOC_MAP = ROOT / "docs" / "README.md"
CURRENT_SPECS = ROOT / "openspec" / "specs"
ARCHIVE = ROOT / "openspec" / "changes" / "archive"
LINK_PATTERN = re.compile(r"!?\[[^\]]*\]\(([^)]+)\)")
ATX_HEADING = re.compile(r"^\s{0,3}#{1,6}\s+(.+?)\s*#*\s*$")
HTML_ID = re.compile(r'''\bid\s*=\s*["']([^"']+)["']''', re.IGNORECASE)


def visible_markdown(text: str) -> str:
    """Remove fenced and inline code so examples are not mistaken for links."""
    visible: list[str] = []
    fence_char: str | None = None
    fence_size = 0

    for line in text.splitlines():
        fence = re.match(r"^\s{0,3}(`{3,}|~{3,})", line)
        if fence_char is not None:
            if fence and fence.group(1)[0] == fence_char and len(fence.group(1)) >= fence_size:
                fence_char = None
                fence_size = 0
            visible.append("")
            continue
        if fence:
            fence_char = fence.group(1)[0]
            fence_size = len(fence.group(1))
            visible.append("")
            continue
        visible.append(re.sub(r"(`+).*?\1", "", line))

    return "\n".join(visible)


def markdown_files() -> list[Path]:
    excluded = {".git", "node_modules", "target", "dist", ".venv", "venv"}
    files: list[Path] = []
    for current, directories, names in os.walk(ROOT):
        directories[:] = sorted(name for name in directories if name not in excluded)
        files.extend(Path(current) / name for name in names if name.lower().endswith(".md"))
    return sorted(files)


def markdown_links(source: Path) -> list[str]:
    text = visible_markdown(source.read_text(encoding="utf-8"))
    return [match.group(1).strip() for match in LINK_PATTERN.finditer(text)]


def destination(raw: str) -> str:
    raw = raw.strip()
    if raw.startswith("<") and ">" in raw:
        return raw[1 : raw.index(">")]
    return raw.split(maxsplit=1)[0]


def local_target(source: Path, raw: str) -> tuple[Path | None, str]:
    parsed = urlsplit(destination(raw))
    if parsed.scheme or parsed.netloc:
        return None, ""

    path_text = unquote(parsed.path)
    if path_text.startswith("/"):
        target = ROOT / path_text.lstrip("/")
    elif path_text:
        target = source.parent / path_text
    else:
        target = source
    return target.resolve(), unquote(parsed.fragment)


def heading_slug(text: str) -> str:
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", text)
    text = re.sub(r"`+([^`]+)`+", r"\1", text)
    text = re.sub(r"[^\w\- ]", "", text.lower(), flags=re.UNICODE)
    return re.sub(r"\s+", "-", text.strip())


def heading_ids(path: Path) -> set[str]:
    ids: set[str] = set()
    occurrences: dict[str, int] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        for explicit_id in HTML_ID.findall(line):
            ids.add(explicit_id)
        heading = ATX_HEADING.match(line)
        if not heading:
            continue
        base = heading_slug(heading.group(1))
        occurrence = occurrences.get(base, 0)
        occurrences[base] = occurrence + 1
        ids.add(base if occurrence == 0 else f"{base}-{occurrence}")
    return ids


def check_links(files: list[Path]) -> tuple[int, list[str]]:
    errors: list[str] = []
    count = 0
    for source in files:
        for raw in markdown_links(source):
            target, fragment = local_target(source, raw)
            if target is None:
                continue
            count += 1
            if not target.exists():
                errors.append(f"{source.relative_to(ROOT)}: missing link target {raw}")
                continue
            if fragment and target.is_file() and target.suffix.lower() == ".md":
                if fragment not in heading_ids(target):
                    errors.append(f"{source.relative_to(ROOT)}: unresolved heading in {raw}")
    return count, errors


def check_map_coverage() -> list[str]:
    if not DOC_MAP.is_file():
        return ["Missing documentation landing page: docs/README.md"]

    targets: set[Path] = set()
    for raw in markdown_links(DOC_MAP):
        target, _ = local_target(DOC_MAP, raw)
        if target is not None:
            targets.add(target)

    errors: list[str] = []
    specs = sorted(CURRENT_SPECS.glob("*/spec.md"))
    for spec in specs:
        if spec.resolve() not in targets:
            errors.append(f"docs/README.md does not link current capability: {spec.relative_to(ROOT)}")

    archives = sorted(path for path in ARCHIVE.iterdir() if path.is_dir()) if ARCHIVE.is_dir() else []
    for change in archives:
        if change.resolve() not in targets:
            errors.append(f"docs/README.md does not link archived change: {change.relative_to(ROOT)}")

    return errors


def main() -> int:
    files = markdown_files()
    link_count, errors = check_links(files)
    errors.extend(check_map_coverage())

    if errors:
        print("Documentation integrity check failed:")
        for error in errors:
            print(f"- {error}")
        return 1

    spec_count = len(list(CURRENT_SPECS.glob("*/spec.md")))
    archive_count = sum(1 for path in ARCHIVE.iterdir() if path.is_dir()) if ARCHIVE.is_dir() else 0
    print(
        "Documentation integrity check passed: "
        f"{len(files)} Markdown files, {link_count} local links, "
        f"{spec_count} current capabilities, {archive_count} archived changes."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
