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

## If an alarm doesn't ring
This app now bundles its own alarm sound file inside the app (instead of
relying on your phone's system ringtone, which can silently be missing or
null on some devices), and auto-boosts your phone's dedicated **Alarm**
volume slider if it's muted — Android has a separate alarm volume from your
ringer/media volume, and it's very easy to have it sitting at zero without
realizing.

If it's still silent, use the **"Test alarm now"** button on the dashboard —
it triggers the exact same ringing screen/sound/vibration immediately,
skipping the scheduled-alarm system entirely. This tells you which half of
the problem you have:

- **Test alarm rings fine, but scheduled ones don't** → the problem is Android
  killing the scheduled alarm before it fires. Go to Settings → Apps →
  Rotation Board and check for any "Autostart" or "Allow background
  activity" toggle (common on MIUI/Vivo/Oppo/Realme/OnePlus) and enable it,
  in addition to the "Fix this" battery banner in the app.
- **Test alarm doesn't ring either** → something more fundamental is
  blocking it on your specific device/Android version. Please share what
  happens (or doesn't) when you tap it, plus your phone brand and Android
  version, so this can be narrowed down further.



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
