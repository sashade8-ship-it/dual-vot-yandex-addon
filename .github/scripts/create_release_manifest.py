#!/usr/bin/env python3
"""Create the checksummed, timezone-free development artifact manifest."""

from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime
from pathlib import Path


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--artifact", type=Path, required=True)
    parser.add_argument("--patch-list", type=Path, default=Path("patches-list.json"))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument(
        "--created-at",
        help="timezone-free ISO-8601 timestamp; defaults to the local build time",
    )
    args = parser.parse_args()

    root = args.root.resolve()
    configuration = json.loads((root / ".github" / "compatibility.json").read_text(encoding="utf-8"))
    product = configuration["product"]
    artifact = args.artifact if args.artifact.is_absolute() else root / args.artifact
    patch_list = args.patch_list if args.patch_list.is_absolute() else root / args.patch_list
    output = args.output if args.output.is_absolute() else root / args.output

    if artifact.name != product["artifact_name"]:
        raise SystemExit(f"unexpected artifact name: {artifact.name}")
    if not artifact.is_file() or not patch_list.is_file():
        raise SystemExit("artifact and generated patch list must both exist")

    created_at = args.created_at or datetime.now().replace(microsecond=0).isoformat()
    manifest = {
        "schema_version": 1,
        "product": product["name"],
        "channel": product["channel"],
        "version": product["version"],
        "asset_name": artifact.name,
        "asset_size_bytes": artifact.stat().st_size,
        "asset_sha256": sha256_file(artifact),
        "created_at": created_at,
        "patch_count": 1,
        "patch_list_sha256": sha256_file(patch_list),
        "development_base_commit": configuration["base_support"]["development"]["commit"],
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
