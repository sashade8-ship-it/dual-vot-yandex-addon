#!/usr/bin/env python3
"""Expose the immutable development-base pin to GitHub Actions."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--github-output", type=Path, required=True)
    args = parser.parse_args()

    configuration = json.loads(
        (args.root.resolve() / ".github" / "compatibility.json").read_text(encoding="utf-8")
    )
    development = configuration["base_support"]["development"]
    product = configuration["product"]
    remote_available = development["availability"] == "remote-available"
    args.github_output.write_text(
        "\n".join((
            f"repository={development['repository']}",
            f"ref={development['commit']}",
            f"remote_available={'true' if remote_available else 'false'}",
            f"version={product['version']}",
            f"artifact_name={product['artifact_name']}",
            "",
        )),
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
