# LoApunto

A tiny personal Android inbox for thoughts: capture quickly, triage later.

**capture → inbox → review → today / later / done / trash**

[Download the latest APK](https://github.com/maximbetin/loapunto/releases/latest/download/loapunto.apk)

## Capture

- **New** button in the app
- Long-press the app icon → **New entry**
- Quick Settings tile **New entry** (add it by editing your tiles)
- Share text from any app → **LoApunto**

Tapping outside the capture sheet or going back keeps what you typed; only **Cancel** discards it.

Each entry has a title and optional details you can add later.

## Organise

- Three lists: **Inbox**, **Today**, **Later**. Done and Trash live behind the box icon in the top bar.
- **Hold** an entry for big buttons: Today / Later / Inbox / Done / Trash.
- **Drag ⋮⋮** to reorder. The bar on the left fades from top to bottom so the order is visible at a glance.
- **Sort inbox one by one**: tidy each entry and send it somewhere, or skip it.
- Add a time to a due date to get a reminder notification (text is hidden on the lock screen).

Everything stays on the phone in a single JSON file.

## Builds

Every push to `main` runs tests, lint and a signed release build, then publishes it as the latest GitHub release. The repository secret `ANDROID_DEBUG_KEYSTORE_BASE64` holds the signing key so each APK installs as an update over the previous one.
