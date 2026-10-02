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

The first line of an entry is its title; anything below it is details (a shopping list, notes, whatever).

## Triage

- **Sort inbox one by one**: tidy each entry, pick a priority, send it to Today / Later / Done / Trash
- Drag right past half the card: done (or reopen / restore)
- Drag left past half the card: trash (or delete for good from Trash)
- Tap an entry to edit it, set priority (High / Normal / Low), move it, or set a due date
- Add a time to a due date to get a reminder notification (text is hidden on the lock screen)

Everything stays on the phone in a single JSON file.

## Builds

Every push to `main` runs tests, lint and a signed release build, then publishes it as the latest GitHub release. The repository secret `ANDROID_DEBUG_KEYSTORE_BASE64` holds the signing key so each APK installs as an update over the previous one.
