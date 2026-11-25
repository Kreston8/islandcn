# islandcn 灵动岛 for Android

Upstream islandcn is a Next.js site plus a shadcn registry, not a native app. This folder is a
native Android app that borrows the look of the island: a black pill sits at the top of the screen,
springs open when a notification arrives, and collapses on its own.

It replaced an earlier WebView shell. Nothing here loads a web page and the app has no network
permission.

## How it works

- `IslandService` (`IslandService.java`) is a foreground service that owns one
  `TYPE_APPLICATION_OVERLAY` window. The window is `WRAP_CONTENT`, so animating the card's layout
  params is what grows and shrinks it, and `Gravity.TOP | CENTER_HORIZONTAL` keeps it centred.
- `IslandListener` (`IslandListener.java`) is a `NotificationListenerService`. It drops group
  summaries, ongoing events and its own status notification, then hands title, body, icon and
  content intent to the island.
- `IslandView` draws the pill and the expanded card with plain platform views, cross-fading the
  content while the corner radius interpolates from a pill to a card. `OvershootInterpolator` gives
  the spring.
- `IslandBridge` is a static, weakly referenced bus between the two components, which run in the
  same process.
- `MainActivity` is the setup screen: permission states, start/stop, and a preview that posts a fake
  event.

Behaviour: tap the pill to expand, tap the card to open the notification's app, swipe up or tap the
cross to dismiss (which cancels the notification), auto collapse after 4.2 s, and removing the
notification from the shade hides the island.

## Permissions the user has to grant

1. **Display over other apps** (`SYSTEM_ALERT_WINDOW`) for the overlay itself.
2. **Notification access** (`BIND_NOTIFICATION_LISTENER_SERVICE`) for the content.
3. On MIUI / HyperOS also enable **后台弹出界面** for this app, otherwise the system drops overlays
   started from the background.
4. Android 13+ asks for `POST_NOTIFICATIONS` so the foreground service can show its status
   notification.

## What it is not

- Not a `SystemUI` plugin. It cannot draw above the status bar (the status bar window is a higher
  layer), so the island is anchored just below it instead of around the punch hole.
- No interaction with the lock screen: the system hides `TYPE_APPLICATION_OVERLAY` windows while the
  device is locked.
- No media/charging/battery states, no gesture to hide a running timer. It reacts to new
  notifications only, which is the part that reads as "dynamic island" in daily use.

## Build

Locally, with JDK 17 and the Android SDK (`compileSdk 34`):

```bash
cd android
gradle assembleRelease
# android/app/build/outputs/apk/release/app-release.apk
```

Or push to the `android-apk` branch / run the **Build Android APK** workflow by hand; it builds the
APK and attaches it to a GitHub release.

## Install on the phone

1. Download the `.apk` from the release assets.
2. Allow installs from unknown sources for the app you download with (MIUI: 设置 → 应用设置 →
   特殊权限 → 安装未知应用).
3. Open the app, grant the two permissions, press 启动灵动岛, then 预览效果.
