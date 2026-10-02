# LoApunto

A tiny personal Android inbox for thoughts: capture quickly, triage later.

**capture → inbox → review → today / later / done / trash**

[Download the latest APK](https://github.com/maximbetin/loapunto/releases/latest/download/loapunto.apk)

## Capture

- **Jot** button in the app
- Long-press the app icon → **Jot**
- Quick Settings tile **Jot** (add it by editing your tiles)
- Share text from any app → **LoApunto**

Tapping outside the capture sheet or going back keeps what you typed; only **Cancel** discards it.

## Triage

- Swipe right: done (or reopen / restore)
- Swipe left: trash (or delete for good from Trash)
- Tap an entry to edit it, star it, move it, or set a due date

Everything stays on the phone in a single JSON file.

## Builds

Every push to `main` runs tests, lint and a signed release build, then publishes it as the latest GitHub release. The repository secret `ANDROID_DEBUG_KEYSTORE_BASE64` holds the signing key so each APK installs as an update over the previous one.
