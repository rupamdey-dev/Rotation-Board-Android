# Rotation Board (Android)

A native Android app for tracking Gmail account cooldowns used with Claude —
with a real system alarm (loud sound + vibration + lock-screen popup) when an
account's cooldown finishes, not just a notification.

## Features
- Register / log in — each person's account list is private and stays saved
  between sessions (unlike a website, this app can remember you're logged in).
- Add a Gmail address + project name. Set the cooldown either as "in X hours"
  (defaults to 5 if left blank) or "at a specific time" (e.g. 1:00 PM, 5:00 AM).
- Edit the email, project, or timing on any account at any time.
- Accounts are **never auto-deleted** when a timer finishes — only the ✕ button
  removes one.
- When the time hits, you get a real alarm: loud looping sound (plays even in
  silent mode, same as your Clock app), vibration, and a full-screen popup —
  even if your phone is locked or the app is fully closed.
- **Snooze** button on the alarm screen (+10 minutes) alongside Dismiss.
- Alarms survive phone restarts.
- A one-tap **"Fix this"** banner appears if your phone's battery optimizer
  might block background alarms — tapping it takes you straight to the
  system permission screen (this matters a lot on Xiaomi/Vivo/Oppo/Realme/
  OnePlus phones, which are aggressive about killing background apps).
- **Search bar** to filter accounts by email or project once you have several.
- **Status summary** at the top ("2 ready · 3 cooling · 1 idle").
- Accounts auto-sort with the most actionable ones on top: ready first, then
  soonest-to-finish, then idle.
- **Copy button** on each row to quickly copy the Gmail address to your
  clipboard when you're ready to switch accounts.

## App structure
- **Dashboard** (home screen) — just your account list: search, add, edit,
  start/reset timers. Kept exactly as focused as before.
- **Settings** (tap "Settings" top-right of the dashboard) — everything else
  lives here: battery optimization fix, autostart settings shortcut, alarm
  sound picker, app lock toggle, and "Test alarm now."

The debug log has been removed now that the alarm pipeline is confirmed
working — it did its job for diagnosis and isn't needed day-to-day.

## New features
- **Custom alarm sound** — "Alarm sound" button on the dashboard opens your
  phone's ringtone picker so you can choose any sound instead of the bundled
  beep. Falls back to the bundled sound automatically if picking fails.
- **Home screen widget** — long-press your home screen → Widgets →
  Rotation Board. Shows what needs attention (ready accounts, or the next
  one coming up) without opening the app. Updates whenever you add/edit an
  account, whenever the backup worker runs (~every 15 min), and passively
  every ~30 minutes otherwise. Tap it to open the app.
- **App lock** — toggle in Settings. Requires your phone's fingerprint/face
  unlock or PIN/pattern to open the app after it's been in the background.
  Needs a screen lock or biometric already set up on your phone to enable.

## If a scheduled alarm doesn't ring (but "Test alarm now" does)
This means the ringing mechanism itself is fine — the problem is Android (or
your phone's manufacturer) killing the alarm before it fires. Two things now
help with this directly:

1. **"Open autostart settings"** button on the dashboard — on Xiaomi/MIUI,
   Vivo, Oppo, Realme, OnePlus, and Huawei/Honor phones, there's a SEPARATE
   permission system from standard Android's battery optimization, usually
   called "Autostart" or "Allow background activity." This button jumps
   straight to that screen for your phone brand and enables it — this is by
   far the most common cause of "the alarm just doesn't ring" on these
   brands, and standard Android battery optimization settings don't cover it.
2. **A 15-minute backup safety net** — even if a scheduled alarm still gets
   killed somehow, the app now also checks in the background roughly every
   15 minutes (the shortest interval Android allows) for any account whose
   time has passed without ringing, and fires it then. Not instant, but you
   should never be left waiting indefinitely with zero alert.

If it's still silent after enabling autostart, use **"Test alarm now"** to
re-confirm ringing still works, then let me know your exact phone brand,
model, and Android version so this can be narrowed down further.



## How to build the APK (no local Android Studio needed)

1. **Create a free GitHub account** at github.com/join if you don't have one.
2. **Create a new repository** (e.g. `rotation-board-android`) — public or
   private, either works.
3. **Upload this entire folder's contents** to that repository, keeping the
   folder structure intact. Easiest ways:
   - If you have `git` installed: `git init`, `git add .`, `git commit -m "init"`,
     then follow GitHub's "push an existing repository" instructions shown on
     your new repo's page.
   - No git? On the repo page, click **Add file → Upload files**, then drag
     the *entire* extracted `RotationBoardApp` folder onto the page (most
     browsers preserve the folder structure when you drag a folder in).
4. Once pushed, go to the **Actions** tab of your repository. A build should
   start automatically (or click **Run workflow** if it doesn't).
5. Wait 3–5 minutes for it to finish (green checkmark).
6. Open the completed run, scroll down to **Artifacts**, and download
   `rotation-board-debug-apk` — it's a zip containing `app-debug.apk`.
7. Transfer that `.apk` to your phone (or just open the Actions page in your
   phone's browser and download it directly there), then tap it to install.
   Android will ask you to allow "install unknown apps" for whichever app
   you used to open the file — that's normal for anything installed outside
   the Play Store.

Your friend can do the exact same thing with the same APK — installing it on
their own phone and registering their own username gives them their own
private, separate account list.

## Notes
- This is a **debug build** (not signed for the Play Store) — that's expected
  and fine for installing directly on your own device.
- All data stays on-device (Room/SQLite database) — nothing is sent to any
  server.
