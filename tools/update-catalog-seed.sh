#!/bin/sh
set -eu

if [ "$#" -gt 1 ]; then
    echo "Usage: $0 [release-tag]" >&2
    exit 2
fi

repo=lihenggui/blocker-catalog
assets=$(CDPATH='' cd -- "$(dirname -- "$0")/../core/data/src/main" && pwd)/assets
mkdir -p "$assets"
work=$(mktemp -d "$assets/.catalog.XXXXXX")
trap 'rm -rf "$work"' EXIT
trap 'exit 1' HUP INT TERM

tag=${1:-latest}
if [ "$tag" = latest ]; then
    curl -fsSL --retry 3 "https://api.github.com/repos/$repo/releases/latest" -o "$work/release.json"
    tag=$(python3 -c 'import json, sys; print(json.load(sys.stdin)["tag_name"])' < "$work/release.json")
fi
case "$tag" in
    ''|*[!a-zA-Z0-9._-]*) echo "Invalid release tag: $tag" >&2; exit 2 ;;
esac
for asset in v2-catalog.json v2-rules.json.gz v2-components.jsonl.gz v2-icons.zip; do
    curl -fsSL --retry 3 "https://github.com/$repo/releases/download/$tag/$asset" -o "$work/$asset"
done

python3 - "$work" "$assets/catalog" <<'PY'
import gzip
import hashlib
import json
import shutil
import sys
import zipfile
from pathlib import Path

work, destination = map(Path, sys.argv[1:])
seed = work / "seed"
seed.mkdir()
manifest_file = seed / "catalog.json"
shutil.copyfile(work / "v2-catalog.json", manifest_file)
manifest = json.loads(manifest_file.read_bytes())
if manifest["format"] != 2:
    raise ValueError("Expected catalog format 2")


def is_icon(path):
    parts = path.split("/")
    return (
        len(parts) > 1 and parts[0] == "icon"
        and all(part not in ("", ".", "..") for part in parts)
        and "\\" not in path and "\x00" not in path
    )


def extract(source, path, entry):
    target = seed / path
    target.parent.mkdir(parents=True, exist_ok=True)
    digest = hashlib.sha256()
    size = 0
    with target.open("xb") as output:
        while chunk := source.read(64 * 1024):
            size += len(chunk)
            if size > entry["size"]:
                raise ValueError(f"Size mismatch: {path}")
            digest.update(chunk)
            output.write(chunk)
    if size != entry["size"] or digest.hexdigest() != entry["sha256"]:
        raise ValueError(f"Size or SHA-256 mismatch: {path}")


entries = manifest["files"]
files = {entry["path"]: entry for entry in entries}
if [entry["path"] for entry in entries] != sorted(files):
    raise ValueError("Catalog paths must be sorted and unique")

for path in ("rules.json", "components.jsonl"):
    with gzip.open(work / f"v2-{path}.gz") as source:
        extract(source, path, files.pop(path))

with zipfile.ZipFile(work / "v2-icons.zip") as icons:
    for entry in icons.infolist():
        path = entry.filename
        if entry.is_dir() or not is_icon(path) or path not in files:
            raise ValueError(f"Unexpected icon: {path}")
        with icons.open(entry) as source:
            extract(source, path, files.pop(path))
if files:
    raise ValueError(f"Missing files: {', '.join(files)}")

previous = work / "previous"
if destination.exists():
    destination.rename(previous)
try:
    seed.rename(destination)
except OSError:
    if previous.exists():
        previous.rename(destination)
    raise
print(f"Updated catalog seed to sequence {manifest['sequence']} ({len(entries)} payload files)")
PY
