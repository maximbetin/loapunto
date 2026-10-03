# LoApunto

A tiny personal Android inbox for thoughts: capture quickly, triage later.

**capture → inbox → review → today / later / done / trash**

[Download the latest APK](https://github.com/maximbetin/loapunto/releases/latest/download/loapunto.apk)

## Capture

- **New** button in the app: adds to the list you're looking at (Inbox, Today or Later)
- The second app icon, **LoApunto +**, opens straight into a new entry (on Samsung you can set it as the side-key double-press)
- Long-press the app icon → **New entry**
- Quick Settings tile **New entry** (add it by editing your tiles)
- Share text from any app → **LoApunto**

Everything except the New button lands in the **Inbox**.

Tapping outside the capture sheet or going back keeps what you typed; only **Cancel** discards it.

Each entry has a title and optional details you can add later. **Make it a checklist** turns the details into tickable items; nothing is reformatted while you type.

## Organise

- Three lists: **Inbox**, **Today**, **Later**. Later shows **Coming back** (parked until a day) above **No date** (things that stay until you move them). 🕘 **History** holds Done and Trash, grouped by day with a count per day (cleared after 30 days, or never: ⚙); ⚙ has the daily nudge and backups.
- **Hold** an entry for big buttons: Today / Later / Inbox / Done / Trash.
- Gesture hints (hold, drag) show until you have used the gesture once, then stop.
- **Drag ⋮⋮** to reorder. The bar on the left fades from top to bottom so the order is visible at a glance.
- New entries and moved entries join the **bottom** of a list, so things keep the order you sent them in and never jump above what you put first.
- **Sort inbox one by one**: tidy each entry and send it somewhere, or skip it. Every move, here and in the lists, offers **Undo**.
- Add a time to a due date to get a reminder notification (text is hidden on the lock screen), with **Done**, **In 1 hour** and **Tomorrow** buttons.
- **One at a time** (Today): only the next entry on screen, with Done, Later and Not now.
- **Back in the inbox on…** (hold an entry): park it until **Tomorrow**, **Next week**, **Next month**, or any day you pick. It waits faded in Later, then comes back into the **Inbox** on that day, to be sorted like anything new. A reminder set before that day moves to it, same time.
- **Daily nudge** (09:00 by default): only the counts for Today and Inbox, and only when there's something.
- Today items from earlier days show a small **left over · Keep · Later** bar. Entries untouched for a month fade a little, and dateless ones in Later get a **N here for over a month · Review** bar that walks you through them one at a time.
- English or Spanish, following the phone's language.

Everything stays on the phone in a single JSON file. ⚙ → Backup → **Save** / **Restore** copies it wherever you like.

## Builds

Every push to `main` runs tests, lint and a signed release build, then publishes it as the latest GitHub release. The repository secret `ANDROID_DEBUG_KEYSTORE_BASE64` holds the signing key so each APK installs as an update over the previous one.
