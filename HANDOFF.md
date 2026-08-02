# Dual VoT Yandex Add-on handoff

**Updated:** 2026-08-02

## Status

This is the public handoff for the independent, unofficial, development-only
Dual VoT Yandex Add-on. The public source branch is `main` at
`af9b45fcd4cfd049780ba44658b63a5f73182ed6` before this documentation update.
There is no add-on tag or GitHub Release asset.

The add-on depends on the experimental base API at
[`sashade8-ship-it/dual-vot-patches:codex/addon-api-v1`](https://github.com/sashade8-ship-it/dual-vot-patches/tree/codex/addon-api-v1),
commit `c73d8555dbe0bd8db25a516bc2490331b5341001`. This is a development pin,
not a stable integration or an upstream-supported release contract.

## What is implemented

- One patch: `Voice Over Translation (Yandex)`.
- `app.dualvot` runtime, Yandex controls, resources and settings migration.
- A pre-mutation compatibility gate for AddOnApi v1 and
  `AddOnManager.registerAddOns()V`.
- Registration of the `yandex` engine through the base API, whose coordinator
  owns cross-engine exclusivity and lifecycle handoff.
- Redacted diagnostics: sensitive identifiers, URLs, credentials and local
  paths are not logged.
- Source and artifact-manifest validation in GitHub Actions.

The base repository contains the complementary API, manager, `Add-on support`
patch, and official-engine integration. Looking at only this add-on repository
will therefore not show the base coordinator; that separation is deliberate.

## Verified evidence

Local validation with JDK 21 and Android SDK completed successfully:

```powershell
.\gradlew.bat :patches:buildAndroid generatePatchesList --no-daemon --offline --stacktrace --rerun-tasks
python .github/scripts/create_release_manifest.py --artifact patches/build/libs/patches-1.0.0-dev.1.mpp --output build/release-manifest.json
python .github/scripts/validate_addon.py --source --manifest build/release-manifest.json --artifact patches/build/libs/patches-1.0.0-dev.1.mpp
python .github/scripts/test_validate_addon.py
git diff --check
```

The source-policy and combined base/add-on build GitHub Actions run
[`30734867481`](https://github.com/sashade8-ship-it/dual-vot-yandex-addon/actions/runs/30734867481)
passed. Its artifact is a development build, not a device-tested release.

## Not verified

No observed Manager installation, patched APK device run, OAuth flow, Yandex
request, proxy path, or audio-upload path has been performed. In particular,
the report that the Yandex button does not appear is an untriaged integration
report, not a confirmed regression in either repository.

## Next device-test protocol

1. Build the exact pinned base bundle and this add-on bundle.
2. Add both `.mpp` files as Local sources in Morphe Manager.
3. In a single Expert-mode YouTube session select base `Add-on support`, base
   `Voice over translation`, and add-on `Voice Over Translation (Yandex)`.
4. Patch/install a supported YouTube APK and preserve the Manager log, APK
   version, patch selections, and both bundle hashes.
5. Check button visibility and settings before testing engine handoff and
   network paths.

An `AddOnApi`/`registerAddOns()` gate failure indicates an incompatible or
unselected base. A successful patch with a missing button requires the real
APK and Manager logs; do not infer the fault from source layout or build logs.

## Rules for the next contributor

Read `AGENTS.md` before editing. Keep the API pin and compatibility gate
strict, preserve notices and credits, do not introduce sensitive logging, and
do not silently merge this development integration into a stable branch.
