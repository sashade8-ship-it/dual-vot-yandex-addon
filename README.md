# Dual VoT Yandex Add-on

An independent, GPLv3 `Voice Over Translation (Yandex)` add-on for YouTube patching. It is development-only and interoperates with the compatible platform through `AddOnApi` v1; it is not an official Morphe project or a replacement for the base bundle.

## Support status

Development support is pinned to `sashade8-ship-it/dual-vot-patches` commit `c73d8555dbe0bd8db25a516bc2490331b5341001`. That commit has been verified locally but is explicitly **pending push**: do not treat it as a remotely available build input yet.

The intended canonical product home is `https://github.com/sashade8-ship-it/dual-vot-yandex-addon`. It is planned metadata only until the repository is actually created and verified; this work does not publish, push, or release it.

Stable support is intentionally blocked until an official upstream tag is verified to expose the complete `AddOnApi.API_VERSION == 1` contract. The machine-readable policy is in [.github/compatibility.json](.github/compatibility.json).

## What the add-on provides

- A separate Yandex translation button in modern and legacy player controls.
- Long-press configuration, translated/original-volume controls, proxy fallback, live voices, OAuth sign-in, retry/fallback behavior, and cancellation on video changes.
- Countdown, progress ring, color, thickness, and error state for the modern player button. The custom view layers over the API-v1 host button so the host retains its own layout and spacing ownership.
- An isolated `dualvot_yandex_*` settings screen, with a one-time non-overwriting migration from the prototype’s `morphe_yandex_vot_*` keys.
- Exclusive voice-over ownership through `AddOnApi` v1. Starting Yandex first stops the active engine; all terminal paths, errors, pauses, and video transitions clear Yandex through the same coordinator.

The patch begins with a mandatory bytecode compatibility gate. It verifies `API_VERSION == 1`, every coordinator method the add-on calls, and `AddOnManager.registerAddOns()V` before resource writes, extension merge, registration injection, or the `AudioTrack` hook.

## Build locally

Use a sibling API-v1 base checkout named `morphe-addon-api`:

```text
.addon-work/
├── morphe-addon-api/          # pinned compatible platform base
└── dual-vot-yandex-addon/     # this repository
```

Compile the base extension classes first, then build the add-on. On Windows, the local project uses JDK 21 and the Android SDK configured by the workspace.

```powershell
Set-Location ..\morphe-addon-api
.\gradlew.bat :extensions:youtube:compileReleaseKotlin :extensions:youtube:compileReleaseJavaWithJavac --no-daemon --stacktrace

Set-Location ..\dual-vot-yandex-addon
.\gradlew.bat :patches:buildAndroid generatePatchesList --no-daemon --stacktrace
```

If the Gradle package repository needs credentials merely to configure, use non-secret local placeholder values for an offline cache build; never add a real token to source control.

The resulting development artifact is `patches-1.0.0-dev.1.mpp`. It is not published or released by this repository.

## Validate an artifact

```powershell
python .github/scripts/validate_addon.py --source
python .github/scripts/test_validate_addon.py
python .github/scripts/create_release_manifest.py `
  --artifact patches/build/libs/patches-1.0.0-dev.1.mpp `
  --output build/release-manifest.json
python .github/scripts/validate_addon.py --source `
  --manifest build/release-manifest.json `
  --artifact patches/build/libs/patches-1.0.0-dev.1.mpp
```

Validation requires the exact development version and asset name, one Yandex patch only, matching artifact size and SHA-256, and timezone-free `created_at` fields. It also rejects the old prototype package tree so base patches cannot leak into this add-on bundle.

## CI

- `PR validation` validates source policy and the manifest rules on pull requests.
- Once the pinned SHA is actually pushed and `availability` becomes `remote-available`, the same workflow also compiles the pinned base and add-on together.
- `Development prerelease candidate` runs for `v*-dev.*` tags and uploads a candidate artifact plus manifest. It deliberately does not create a GitHub release, publish packages, push tags, or modify release metadata.

## Credits and license

This repository retains the historical two-commit MarcaDian Yandex add-on prototype as its initial history. The Yandex implementation preserves upstream attribution to Jav1x and anddea, including GPLv3 Section 7 notices. Its independent YouTube audio-stream fallback retains the MIT notice for sodapng and ilyhalight.

`Dual VoT Yandex Add-on` is a distinct product name. “Morphe” is used only as an accurate compatibility description. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
