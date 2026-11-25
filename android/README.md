# islandcn 灵动岛 for Android

Upstream islandcn is a Next.js site plus a shadcn registry, not a native app. This folder is a
native Android app that borrows the look of the island: a black pill sits at the top of the screen,
springs open when a notification arrives, and collapses on its own.

It replaced an earlier WebView shell. Nothing here loads a web page and the app has no network
permission.

## What it shows

Every notification gets a card. Seven kinds get a richer card — four decided by `ContentParser` from
the title, body and application, three by their own sources:

| Kind | Trigger | Card |
|---|---|---|
| 验证码 | SMS with a code keyword, or a notification containing one | big code + 点击复制 |
| 取件码 | "取件码/取货码/提货码" plus a code nearby | big code + 点击复制 |
| 外卖 | "预计 12:35 送达", "还有 18 分钟" | countdown that refreshes every 15 s |
| 待办 | notification from 小米笔记 / a notes app | tag + the todo text |
| 来电 | call state callback | tag + 号码 / 未知号码，tap 打开拨号盘 |
| 闹钟 | next clock alarm, 15 min ahead | tag + 07:30 + 还有 N 分钟响铃 |
| 导航 | ongoing notification from a whitelisted map app | tag + the next turn |

The code rows copy to the clipboard and collapse the island; the notification itself is left in the
shade so the copy is never destructive.

## How it works

- `IslandService` (`IslandService.java`) is a foreground service that owns one
  `TYPE_APPLICATION_OVERLAY` window. The window is `WRAP_CONTENT`, so animating the card's layout
  params is what grows and shrinks it, and `Gravity.TOP | CENTER_HORIZONTAL` keeps it centred.
- `IslandListener` (`IslandListener.java`) is a `NotificationListenerService`. It drops group
  summaries, ongoing events and its own status notification, then hands title, body, icon and
  content intent to the island.
- `SmsReceiver` (`SmsReceiver.java`) listens for `SMS_RECEIVED` so a code arrives even when the
  messaging app does not post a notification. Ordinary texts are dropped; when this receiver is
  active the messaging app's own notification is skipped to avoid showing the code twice.
- `PhoneWatcher` (`PhoneWatcher.java`) registers a call-state callback, `TelephonyCallback` on
  Android 12+ and the legacy `PhoneStateListener` below it. Only the legacy API still carries the
  caller id, hence 未知号码 on newer phones.
- `AlarmWatcher` (`AlarmWatcher.java`) reads `AlarmManager.getNextAlarmClock()` and schedules one
  inexact wake-up fifteen minutes earlier, which is what posts the 闹钟 card. It re-arms itself
  whenever the system announces a changed alarm.
- Navigation cards come from the map apps listed in `IslandListener.NAV_PACKAGES`: their ongoing
  notification is allowed through, tagged 导航, and re-posted only when the line changes.
- `ContentParser` (`ContentParser.java`) is the classifier: keyword plus regular expression matching
  for codes, pickup numbers and delivery ETAs.
- `IslandView` draws the pill and the expanded card with plain platform views, cross-fading the
  content while the corner radius interpolates from a pill to a card. `OvershootInterpolator` gives
  the spring. The card grows by one row when a copyable code or a countdown is present.
- `IslandBridge` is a static, weakly referenced bus between the components, which run in the same
  process.
- `MainActivity` is the setup screen: permission states, start/stop, and a preview that posts a fake
  verification code.

Behaviour: tap the pill to expand, tap the card to open the notification's app, swipe up or tap the
cross to dismiss (which cancels the notification), auto collapse after 4.2 s, and removing the
notification from the shade hides the island.

## Permissions the user has to grant

1. **Display over other apps** (`SYSTEM_ALERT_WINDOW`) for the overlay itself.
2. **Notification access** (`BIND_NOTIFICATION_LISTENER_SERVICE`) for the content.
3. **SMS** (`RECEIVE_SMS`), optional, for codes that never reach the shade.
4. **Phone** (`READ_PHONE_STATE`), optional, for incoming-call cards. On Android 12+ the card still
   says 未知号码, because the caller id is no longer shared with third-party apps.
5. On MIUI / HyperOS also enable **后台弹出界面** for this app, otherwise the system drops overlays
   started from the background.
6. Android 13+ asks for `POST_NOTIFICATIONS` so the foreground service can show its status
   notification.

## What it is not

- Not a `SystemUI` plugin. It cannot draw above the status bar (the status bar window is a higher
  layer), so the island is anchored just below it instead of around the punch hole.
- No interaction with the lock screen: the system hides `TYPE_APPLICATION_OVERLAY` windows while the
  device is locked.
- The 待办 card only sees Mi Notes' *reminder notifications*. Reading the note database itself needs
  root, and there is no public provider for it, so the island cannot list upcoming todos.
- 来电 cards cannot show the number on Android 12+: the platform stopped passing the caller id to
  third-party apps. The in-call screen is also a full-screen activity, so it covers the overlay while
  the phone is actually ringing.
- The 闹钟 card is a heads-up about fifteen minutes early; when the alarm fires, the clock app's
  full-screen intent covers everything.
- 导航 depends on the map app publishing the next turn as an ongoing notification, and it refreshes
  every few seconds while driving — the island only shows the latest line.

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
3. Open the app, grant the permissions, press 启动灵动岛, then 预览效果.
