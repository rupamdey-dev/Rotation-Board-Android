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
- Alarms survive phone restarts.

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
