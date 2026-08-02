# Contributor and agent guide — Dual VoT Yandex Add-on

This repository is an independent, unofficial, development-only Yandex voice
over translation add-on for YouTube. It is **not self-contained**: it is built
and tested together with a separately maintained experimental base API.

Read `HANDOFF.md`, then `README.md`, `NOTICE`, `CONTRIBUTING.md`, and
`.github/compatibility.json` before changing code.

## Two-repository architecture

| Repository | Responsibility | Do not put here |
| --- | --- | --- |
| [`dual-vot-patches`](https://github.com/sashade8-ship-it/dual-vot-patches), branch `codex/addon-api-v1` | AddOnApi v1, AddOnManager, coordinator, base `Add-on support` patch and official engine integration | Yandex UI, Yandex runtime, or Yandex settings |
| This repository | `Voice Over Translation (Yandex)`, `app.dualvot` runtime, Yandex UI, settings, compatibility gate and add-on validation | A second coordinator, base API implementation, or normal Morphe patch catalogue |

The current add-on is compatible only with base commit
`c73d8555dbe0bd8db25a516bc2490331b5341001`, declared in
`.github/compatibility.json`. It is not compatible with a regular stable
Morphe bundle or an official upstream bundle unless that bundle exposes the
complete AddOnApi v1 contract.

## Invariants

- Generate exactly one patch: `Voice Over Translation (Yandex)`.
- Before any resource or bytecode mutation, run the mandatory non-mutating
  `verifyAddOnApiV1()` gate. It requires `API_VERSION == 1`, all required API
  descriptors, and `AddOnManager.registerAddOns()V`.
- Do not weaken, move, or convert that gate into a warning.
- Engine ownership is centralized in the base API: `official` belongs to the
  base and `yandex` belongs to this add-on. Do not duplicate the coordinator.
- Product code stays under `app.dualvot`; canonical settings/resources use
  `dualvot_yandex_*`. The migration from legacy `morphe_yandex_vot_*` keys is
  one-time and must never overwrite a canonical user value.
- Diagnostics must not include OAuth or profile data, URLs/query strings,
  video or translation identifiers, request bodies, cookies, proxy
  credentials, or local paths.
- Preserve all licenses, notices, and historical credits. Do not recreate the
  legacy `app.morphe...yandexvot` product tree.

## Build and source checks

Use JDK 21 and an Android SDK. Build the exact compatible base first, then
this repository:

```powershell
# In the checked-out base API repository
.\gradlew.bat :extensions:youtube:compileReleaseKotlin :extensions:youtube:compileReleaseJavaWithJavac --no-daemon --stacktrace

# In this repository
.\gradlew.bat :patches:buildAndroid generatePatchesList --no-daemon --offline --stacktrace --rerun-tasks
python .github/scripts/validate_addon.py --source --tag v1.0.0-dev.1
python .github/scripts/test_validate_addon.py
python .github/scripts/create_release_manifest.py --artifact patches/build/libs/patches-1.0.0-dev.1.mpp --output build/release-manifest.json
python .github/scripts/validate_addon.py --source --manifest build/release-manifest.json --artifact patches/build/libs/patches-1.0.0-dev.1.mpp
git diff --check
```

Successful compilation or CI is not device validation. Do not publish a stable
tag or release until a compatible upstream API and observed device test exist.

## Manual Morphe Manager test

This is an integration test, not an established end-user installation path.

1. Build the exact base and add-on `.mpp` bundles.
2. Copy both bundles to the device and add each one as a **Local patch source**
   in Morphe Manager. This repository has no GitHub Release asset, so adding
   its GitHub URL alone does not provide an installable test bundle.
3. In one Expert-mode YouTube patching session select, from the base source,
   `Add-on support` and `Voice over translation`; from this source select
   `Voice Over Translation (Yandex)`.
4. Patch and install a supported YouTube APK. Record the app version, the
   selected patch names, both bundle SHA-256 values, and the Manager log.
5. Only then test button visibility, settings, engine handoff, stop and
   video-change cleanup, followed separately by OAuth, direct, proxy, and
   audio-upload paths.

If the gate reports that `AddOnApi` or `registerAddOns()V` is missing, the
wrong base was selected or the base patches were not applied in the same
operation. If patching succeeds but the button is missing, collect the actual
APK and Manager logs before diagnosing the cause.

After material work, update `HANDOFF.md` with the commit pins, what changed,
commands and results, unverified behaviour, and the next concrete step.
