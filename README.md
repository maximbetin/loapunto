# LoApunto

A tiny personal Android inbox for thoughts: capture quickly, triage later.

**capture → inbox → review → today / later / done / trash**

[Download the latest APK](https://github.com/maximbetin/loapunto/releases/latest/download/loapunto.apk)

## Capture

- **New** button in the app
- The second app icon, **LoApunto +**, opens straight into a new entry (on Samsung you can set it as the side-key double-press)
- Long-press the app icon → **New entry**
- Quick Settings tile **New entry** (add it by editing your tiles)
- Share text from any app → **LoApunto**

Tapping outside the capture sheet or going back keeps what you typed; only **Cancel** discards it.

The mic button dictates through [FUTO Voice Input](https://play.google.com/store/apps/details?id=org.futo.voiceinput) (offline Whisper) when it's installed, otherwise through the phone's own speech app.

Each entry has a title and optional details you can add later. **Make it a checklist** turns the details into tickable items; nothing is reformatted while you type.

## Organise

- Three lists: **Inbox**, **Today**, **Later**. Done & Trash, the daily nudge and backups live in the ⋮ menu.
- **Hold** an entry for big buttons: Today / Later / Inbox / Done / Trash.
- **Drag ⋮⋮** to reorder. The bar on the left fades from top to bottom so the order is visible at a glance.
- **Sort inbox one by one**: tidy each entry and send it somewhere, or skip it.
- Add a time to a due date to get a reminder notification (text is hidden on the lock screen), with **Done**, **In 1 hour** and **Tomorrow** buttons.
- **Daily nudge** (09:00 by default): only the counts for Today and Inbox, and only when there's something.
- Today items from earlier days show a small **left over · Keep · Later** bar. Entries untouched for a month fade a little.
- English or Spanish, following the phone's language.

Everything stays on the phone in a single JSON file. **Back up to a file** / **Restore from a file** copy it wherever you like.

## Builds

Every push to `main` runs tests, lint and a signed release build, then publishes it as the latest GitHub release. The repository secret `ANDROID_DEBUG_KEYSTORE_BASE64` holds the signing key so each APK installs as an update over the previous one.
