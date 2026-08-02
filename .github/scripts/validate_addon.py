#!/usr/bin/env python3
"""Deterministic validation for the development-only add-on bundle."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import zipfile
from datetime import datetime
from pathlib import Path
from typing import Any


VERSION_PATTERN = re.compile(r"^\d+\.\d+\.\d+-dev\.\d+$")
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")
PATCH_NAME = "Voice Over Translation (Yandex)"
ADD_ON_MANAGER_CLASS_DESCRIPTOR = "Lapp/morphe/extension/youtube/addon/AddOnManager;"
ADD_ON_MANAGER_REQUIRED_METHODS = {"registerAddOns()V"}
TIMEZONE_FREE_PATTERN = re.compile(
    r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?$"
)


class ValidationError(ValueError):
    """Raised when a development bundle violates its published contract."""


def load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValidationError(f"Cannot read JSON {path}: {error}") from error
    if not isinstance(value, dict):
        raise ValidationError(f"JSON object expected in {path}")
    return value


def require_equal(actual: Any, expected: Any, label: str) -> None:
    if actual != expected:
        raise ValidationError(f"{label}: expected {expected!r}, got {actual!r}")


def parse_timezone_free_timestamp(value: Any, label: str) -> str:
    if not isinstance(value, str) or not TIMEZONE_FREE_PATTERN.fullmatch(value):
        raise ValidationError(
            f"{label} must be an ISO-8601 timestamp without a timezone offset: {value!r}"
        )
    try:
        parsed = datetime.fromisoformat(value)
    except ValueError as error:
        raise ValidationError(f"{label} is not a valid timestamp: {value!r}") from error
    if parsed.tzinfo is not None:
        raise ValidationError(f"{label} must not carry a timezone: {value!r}")
    return value


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def read_product(root: Path) -> tuple[dict[str, Any], dict[str, Any]]:
    configuration = load_json(root / ".github" / "compatibility.json")
    product = configuration.get("product")
    if not isinstance(product, dict):
        raise ValidationError("compatibility product object is missing")
    require_equal(product.get("name"), "Dual VoT Yandex Add-on", "product.name")
    require_equal(product.get("channel"), "development", "product.channel")
    version = product.get("version")
    if not isinstance(version, str) or not VERSION_PATTERN.fullmatch(version):
        raise ValidationError(f"product.version must be a development version, got {version!r}")
    require_equal(product.get("artifact_name"), f"patches-{version}.mpp", "product.artifact_name")
    require_equal(product.get("patch_name"), PATCH_NAME, "product.patch_name")
    return configuration, product


def validate_add_on_manager_contract(configuration: dict[str, Any]) -> None:
    manager = configuration.get("add_on_manager")
    if not isinstance(manager, dict):
        raise ValidationError("add_on_manager object is missing")
    require_equal(
        manager.get("class_descriptor"),
        ADD_ON_MANAGER_CLASS_DESCRIPTOR,
        "AddOnManager class descriptor",
    )
    require_equal(
        set(manager.get("required_methods", [])),
        ADD_ON_MANAGER_REQUIRED_METHODS,
        "required AddOnManager methods",
    )


def validate_source(root: Path, tag: str | None = None) -> dict[str, Any]:
    configuration, product = read_product(root)
    base_support = configuration.get("base_support")
    if not isinstance(base_support, dict):
        raise ValidationError("base_support object is missing")

    development = base_support.get("development")
    if not isinstance(development, dict):
        raise ValidationError("base_support.development object is missing")
    require_equal(
        development.get("repository"),
        "sashade8-ship-it/dual-vot-patches",
        "development base repository",
    )
    commit = development.get("commit")
    if not isinstance(commit, str) or not COMMIT_PATTERN.fullmatch(commit):
        raise ValidationError("development base commit must be a full immutable SHA")
    if development.get("availability") not in {
        "local-verified-pending-push",
        "remote-available",
    }:
        raise ValidationError("development base availability is invalid")

    stable = base_support.get("stable")
    if not isinstance(stable, dict):
        raise ValidationError("base_support.stable object is missing")
    require_equal(stable.get("minimum_official_upstream_tag"), None, "stable minimum tag")
    require_equal(stable.get("required_api_version"), 1, "stable API version")
    require_equal(
        stable.get("status"),
        "blocked-until-first-official-upstream-addon-api-v1-tag",
        "stable support status",
    )

    api = configuration.get("add_on_api")
    if not isinstance(api, dict):
        raise ValidationError("add_on_api object is missing")
    require_equal(api.get("required_version"), 1, "required AddOnApi version")
    expected_methods = {
        "registerVoiceOverEngine(Ljava/lang/String;Ljava/lang/Runnable;)Z",
        "activateVoiceOverEngine(Ljava/lang/String;)Z",
        "deactivateVoiceOverEngine(Ljava/lang/String;)Z",
        "stopActiveVoiceOverEngine()Z",
        "getActiveVoiceOverEngineId()Ljava/lang/String;",
        "addVoiceOverEngineListener(Ljava/util/function/Consumer;)V",
        "removeVoiceOverEngineListener(Ljava/util/function/Consumer;)V",
    }
    require_equal(set(api.get("required_methods", [])), expected_methods, "required AddOnApi methods")
    validate_add_on_manager_contract(configuration)

    version = product["version"]
    gradle_properties = (root / "gradle.properties").read_text(encoding="utf-8")
    if not re.search(rf"^version\s*=\s*{re.escape(version)}\s*$", gradle_properties, re.MULTILINE):
        raise ValidationError("gradle.properties version does not match compatibility.json")

    bundle = load_json(root / "patches-bundle.json")
    require_equal(bundle.get("version"), version, "patches-bundle version")
    parse_timezone_free_timestamp(bundle.get("created_at"), "patches-bundle created_at")
    require_equal(bundle.get("download_url"), "", "development bundle download_url")

    patch_source = root / "patches" / "src" / "main" / "kotlin" / "app" / "dualvot" / "patches" / "youtube" / "video" / "yandex" / "YandexVoiceOverTranslationPatch.kt"
    if not patch_source.is_file():
        raise ValidationError("independent Yandex patch source is missing")
    patch_text = patch_source.read_text(encoding="utf-8")
    for required_fragment in (
        "API_VERSION",
        "verifyAddOnApiV1",
        "registerVoiceOverEngine",
        "removeVoiceOverEngineListener",
        "ADD_ON_MANAGER_CLASS_DESCRIPTOR",
        "ADD_ON_MANAGER_REGISTER_METHOD_NAME",
        "AddOnManager.registerAddOns()V",
    ):
        if required_fragment not in patch_text:
            raise ValidationError(f"pre-mutation compatibility gate misses {required_fragment}")

    obsolete_paths = (
        root / "patches" / "src" / "main" / "kotlin" / "app" / "morphe" / "patches" / "youtube" / "video" / "yandexvot",
        root / "extensions" / "youtube" / "src" / "main" / "java" / "app" / "morphe" / "extension" / "youtube" / "patches" / "yandexvot",
        root / "patches" / "src" / "main" / "resources" / "yandexvotbutton",
    )
    for obsolete_path in obsolete_paths:
        if obsolete_path.exists() and any(path.is_file() for path in obsolete_path.rglob("*")):
            raise ValidationError(f"prototype product namespace still exists: {obsolete_path}")

    if tag is not None:
        require_equal(tag, f"v{version}", "prerelease tag")

    return product


def validate_manifest(root: Path, manifest_path: Path, artifact_path: Path, patch_list_path: Path) -> None:
    product = validate_source(root)
    manifest = load_json(manifest_path)
    require_equal(manifest.get("schema_version"), 1, "release manifest schema")
    require_equal(manifest.get("product"), product["name"], "release manifest product")
    require_equal(manifest.get("channel"), "development", "release manifest channel")
    require_equal(manifest.get("version"), product["version"], "release manifest version")
    require_equal(manifest.get("asset_name"), product["artifact_name"], "release manifest asset name")
    parse_timezone_free_timestamp(manifest.get("created_at"), "release manifest created_at")

    if artifact_path.name != product["artifact_name"]:
        raise ValidationError(f"artifact filename must be {product['artifact_name']}, got {artifact_path.name}")
    if not artifact_path.is_file():
        raise ValidationError(f"artifact does not exist: {artifact_path}")
    require_equal(manifest.get("asset_size_bytes"), artifact_path.stat().st_size, "release manifest asset size")
    expected_sha256 = sha256_file(artifact_path)
    actual_sha256 = manifest.get("asset_sha256")
    if not isinstance(actual_sha256, str) or not SHA256_PATTERN.fullmatch(actual_sha256):
        raise ValidationError("release manifest asset_sha256 must be lowercase SHA-256")
    require_equal(actual_sha256, expected_sha256, "release manifest asset SHA-256")

    # A clean build can retain harmless empty directory entries, but no old
    # prototype file may be present in the published archive.
    try:
        with zipfile.ZipFile(artifact_path) as archive:
            files = [name for name in archive.namelist() if not name.endswith("/")]
    except (OSError, zipfile.BadZipFile) as error:
        raise ValidationError(f"artifact is not a readable MPP archive: {error}") from error
    obsolete_archive_prefixes = (
        "app/morphe/patches/youtube/video/yandexvot/",
        "yandexvotbutton/",
    )
    for prefix in obsolete_archive_prefixes:
        if any(name.startswith(prefix) for name in files):
            raise ValidationError(f"artifact contains a prototype product file under {prefix}")
    if "extensions/youtube.mpe" not in files:
        raise ValidationError("artifact is missing the Yandex extension payload")

    patch_list = load_json(patch_list_path)
    require_equal(patch_list.get("version"), product["version"], "patch-list version")
    patches = patch_list.get("patches")
    if not isinstance(patches, list) or len(patches) != 1:
        raise ValidationError("an independent add-on artifact must contain exactly one patch")
    patch = patches[0]
    if not isinstance(patch, dict):
        raise ValidationError("patch-list patch entry is invalid")
    require_equal(patch.get("name"), product["patch_name"], "patch-list patch name")
    require_equal(manifest.get("patch_count"), 1, "release manifest patch count")
    require_equal(
        manifest.get("patch_list_sha256"),
        sha256_file(patch_list_path),
        "release manifest patch-list SHA-256",
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--source", action="store_true", help="validate tracked source and compatibility policy")
    parser.add_argument("--tag", help="require the given prerelease tag to match the development version")
    parser.add_argument("--manifest", type=Path, help="validate a generated release manifest")
    parser.add_argument("--artifact", type=Path, help="artifact paired with --manifest")
    parser.add_argument("--patch-list", type=Path, default=Path("patches-list.json"))
    args = parser.parse_args()

    root = args.root.resolve()
    try:
        if not args.source and args.manifest is None:
            raise ValidationError("specify --source and/or --manifest")
        if args.source:
            validate_source(root, args.tag)
        if args.manifest is not None:
            if args.artifact is None:
                raise ValidationError("--artifact is required with --manifest")
            manifest_path = args.manifest if args.manifest.is_absolute() else root / args.manifest
            artifact_path = args.artifact if args.artifact.is_absolute() else root / args.artifact
            patch_list_path = args.patch_list if args.patch_list.is_absolute() else root / args.patch_list
            validate_manifest(root, manifest_path, artifact_path, patch_list_path)
    except ValidationError as error:
        print(f"validation failed: {error}", file=sys.stderr)
        return 1

    print("validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
