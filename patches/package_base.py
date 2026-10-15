#!/usr/bin/env python3
"""Repackage the original base with only patched classes5.dex/classes8.dex.

Preserves every other original entry, including assets and the raw manifest.
Removes obsolete JAR signatures. The resulting base APK is unsigned.
"""
import argparse
import hashlib
import json
import re
import zipfile
from pathlib import Path

from prepare_screen_off import APK_SHA256


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("original", type=Path)
    parser.add_argument("built_dex_directory", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--dex", action="append", help="DEX entries to replace; default classes5.dex and classes8.dex")
    args = parser.parse_args()
    if args.output.resolve() == args.original.resolve():
        parser.error("Output must differ from original")
    with args.original.open("rb") as stream:
        if hashlib.file_digest(stream, "sha256").hexdigest() != APK_SHA256:
            parser.error("Original APK differs from the version-pinned target")
    names = args.dex or ["classes5.dex", "classes8.dex"]
    if any(not re.fullmatch(r"classes\d*\.dex", name) for name in names):
        parser.error("Only classes*.dex replacements are allowed")
    replacements = {name: (args.built_dex_directory / name).read_bytes() for name in names}
    for name, data in replacements.items():
        if not data.startswith(b"dex\n"):
            parser.error(f"Not a DEX file: {name}")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.original) as source, zipfile.ZipFile(args.output, "w") as target:
        for entry in source.infolist():
            if re.fullmatch(r"META-INF/(?:MANIFEST\.MF|[^/]+\.(?:RSA|DSA|EC|SF))", entry.filename, re.I):
                continue
            target.writestr(entry, replacements.get(entry.filename, source.read(entry.filename)))
        for name, data in replacements.items():
            if name not in source.namelist():
                target.writestr(name, data, compress_type=zipfile.ZIP_STORED)
    with zipfile.ZipFile(args.original) as source, zipfile.ZipFile(args.output) as target:
        changed = []
        removed = []
        for name in source.namelist():
            if name not in target.namelist():
                removed.append(name)
            elif source.read(name) != target.read(name):
                changed.append(name)
        added = sorted(set(target.namelist()) - set(source.namelist()))
        if sorted(changed + added) != sorted(replacements):
            raise ValueError(f"Unexpected content changes: {changed}")
        if target.testzip() is not None:
            raise ValueError("Output ZIP integrity check failed")
    print(json.dumps({"output": str(args.output), "changed_entries": changed, "added_entries": added, "removed_signature_entries": removed, "unsigned": True, "device_tested": False}, indent=2))


if __name__ == "__main__":
    main()
