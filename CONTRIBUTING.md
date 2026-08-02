# Contributing

This is a development-only independent add-on. Preserve the initial prototype history, GPLv3 notices, Section 7 attribution, and MIT notice in `NOTICE`.

Before changing behavior:

1. Keep product-owned code under `app.dualvot` and canonical settings under `dualvot_yandex_*`.
2. Do not copy base patches, its release automation, or its updater into this repository.
3. Do not alter `AddOnApi` coordination semantics locally. Add-on lifecycle work must use the API-v1 engine methods and pass the compatibility gate, including `AddOnManager.registerAddOns()V`, first.
4. Do not claim stable support until an official upstream AddOnApi v1 tag is verified and recorded in `.github/compatibility.json`.
5. Do not add OAuth tokens, proxy credentials, cookies, or generated artifacts to source control.

Run `python .github/scripts/validate_addon.py --source`, `python .github/scripts/test_validate_addon.py`, and a build against the pinned base before proposing a change. A build proves compilation only; device, network, OAuth, and Yandex-service behavior require separate observed testing.
