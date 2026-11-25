# islandcn 灵动岛 for Android

English | [中文说明](README.zh.md)

Upstream islandcn is a Next.js site plus a shadcn registry, not a native app. This folder is a
native Android app that borrows the look of the island: a black pill sits at the top of the screen,
springs open when a notification arrives, and collapses on its own.

It replaced an earlier WebView shell. Nothing here loads a web page. Two optional HTTPS lookups
exist — per-line lyrics and a temperature — and both are described below with their limits.

## What it shows

Every notification gets a card. Eight kinds get a richer card — four decided by `ContentParser` from
the title, body and application, four by their own sources:

| Kind | Trigger | Card |
|---|---|---|
| 验证码 | SMS with a code keyword, or a notification containing one | big code + 点击复制 |
| 取件码 | "取件码/取货码/提货码" plus a code nearby | big code + 点击复制 |
| 外卖 | "预计 12:35 送达", "还有 18 分钟" | countdown that refreshes every 15 s |
| 待办 | notification from 小米笔记 / a notes app | tag + the todo text |
| 来电 | call state callback | tag + 号码 / 未知号码，tap 打开拨号盘 |
| 闹钟 | next clock alarm, 15 min ahead | tag + 07:30 + 还有 N 分钟响铃 |
| 导航 | ongoing notification from a whitelisted map app | tag + the next turn |
| 音乐 | a playing media session, shown only while the launcher is in front | 44 dp strip: album art + one lyric line, lit character by character |

Every card expands to the same 44 dp strip: one app icon, one bold line, one body line. Two lines are
all that height holds, so the copyable code moved into the title row and a live countdown replaces the
body line instead of adding a third one.

The code rows copy to the clipboard and collapse the island; the notification itself is left in the
shade so the copy is never destructive.

### Bluetooth battery chips

When 蓝牙设备电量 is on, the right end of the pill shows one chip per connected accessory — watch,
earbuds, anything else — as an icon with the percentage underneath, up to three devices. The chips
appear on every card, including the music/lyric strip, and the pill widens instead of squeezing the
text.

Android has no public battery API for accessories. The percentage is whatever the device last pushed
through the platform's own `BATTERY_LEVEL_CHANGED` broadcast, so:

- a device that has never reported keeps its icon and shows `--`, because a wrong number on a watch is
  worse than none;
- after a fresh install the numbers only appear once a device reports again (typically at connect);
- the app never opens a GATT connection to read a level — doing that to earbuds disturbs playback.

### Standby pill (桌面常驻)

When 桌面常驻 is on and nothing else is on screen, a second, narrower pill stays on the launcher with
the items you enabled:

- **天气温度** — the current temperature, e.g. `23°`. Needs a city name typed in the app; there is no
  location permission and no GPS.
- **小狗头** — the golden-retriever head, swaying about 7° left and right on a 900 ms loop.
- **每日一句** — one line of encouragement, chosen by day of year so it changes once a day.

The three items can be reordered with 上移 / 下移, and the pill disappears the moment a card appears,
the launcher is no longer in front, or the screen is locked.

## How it works

- `IslandService` (`IslandService.java`) is a foreground service that owns one
  `TYPE_APPLICATION_OVERLAY` window, plus a second one for the standby pill. Both are `WRAP_CONTENT`,
  so animating the card's layout params is what grows and shrinks them, and
  `Gravity.TOP | CENTER_HORIZONTAL` keeps them centred.
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
  whenever the system announces a changed alarm, arms a second wake-up a minute past the alarm time
  that withdraws the card, and reconciles card against reality on every call: if the system no longer
  reports the alarm that was announced, the card is removed.
- Navigation cards come from the map apps listed in `IslandListener.NAV_PACKAGES`: their ongoing
  notification is allowed through, tagged 导航, and re-posted only when the line changes.
- `ContentParser` (`ContentParser.java`) is the classifier: keyword plus regular expression matching
  for codes, pickup numbers and delivery ETAs.
- `IslandView` draws the pill and the expanded card with plain platform views, cross-fading the
  content while the corner radius interpolates from a pill to a card. `OvershootInterpolator` gives
  the spring. Every card is the same 44 dp strip, which is why a copyable code sits in the title row
  and a countdown takes over the body line.
- `BtWatcher` (`BtWatcher.java`) listens for the hidden `BATTERY_LEVEL_CHANGED` broadcast, keeps the
  last known level per device address in `SharedPreferences`, and lists connected devices through
  `BluetoothManager.getConnectedDevices` for the `GATT` and `HEADSET` profiles. Every call is guarded:
  without `BLUETOOTH_CONNECT` it returns nothing instead of crashing the overlay.
- `StandbyView` (`StandbyView.java`) renders the standby pill and owns the temperature lookup:
  geocoding (`geocoding-api.open-meteo.com`) turns the typed city into coordinates, then
  `api.open-meteo.com/v1/forecast?current=temperature_2m` returns one number. 6 s timeout, memory
  cache for 30 minutes, one request in flight, nothing requested without a city.
- `IslandBridge` is a static, weakly referenced bus between the components, which run in the same
  process.
- `MediaWatcher` (`MediaWatcher.java`) reads the playing session through `MediaSessionManager`, which
  rides on the notification access the app already has. Track changes arrive as
  `MediaController.Callback`, so nothing polls for metadata. The only timer is a 2.5 s `UsageEvents`
  query that resolves the foreground package against every `CATEGORY_HOME` handler — anything that is
  not the launcher hides the strip — and it runs only while a session is playing: pause, screen off
  or a missing usage-access grant cancels it. One card per track.
- `Prefs` (`Prefs.java`) is the switch store: one boolean per card kind, `on_lyrics` for the lyric
  lookup, `on_bt` for the battery chips, `on_standby` plus three item switches and an order string for
  the standby pill. `IslandService.onIslandEvent` drops a card whose switch is off before the overlay
  is touched at all.
- `Lyric` parses LRC (`[mm:ss.cc]` lines, credit lines filtered out) and answers "which line at this
  position" by binary search; `LyricSource` does the one HTTPS lookup per track with a 6 s timeout,
  a 512 KB cap and a 16 entry memory-only cache.

Behaviour: tap the pill to expand, tap the card to open the notification's app, swipe up or tap the
cross to dismiss (which cancels the notification), auto collapse after 4.2 s, and removing the
notification from the shade hides the island.

The music card is the exception to the 4.2 s collapse: it stays open so the karaoke line keeps
ticking, and it goes away only when the track stops, when you swipe it off, or when another app
comes to the front. Returning to the desktop brings it straight back: the foreground answer is
cached, because `UsageEvents` reports *changes*, so an empty window means "nothing moved", not
"nothing is in front" — treating it as the latter hid the card a few seconds after it appeared.
The line itself is kept honest by trusting the player only as far as it is trustworthy: the
position is extrapolated while `getLastPositionUpdateTime()` is plausible, the card re-anchors
when the reported position drifts more than 700 ms from the running estimate, LRC `[offset:±ms]`
tags are applied, and a lyric whose total length is far from the track duration (a different
recording) is dropped instead of shown out of sync.

## The switches

Every feature has its own switch in the app, 10 for the cards plus the additions:

| Switch | Default | Effect |
|---|---|---|
| one per card kind | on | whether that kind of card may appear |
| 联网歌词 | on | the one lyric lookup per track |
| 蓝牙设备电量 | off | battery chips at the right end of the pill |
| 桌面常驻 | off | the standby pill on the launcher |
| 天气温度 / 小狗头 / 每日一句 | on | which items the standby pill contains |
| 城市 | empty | no city means no weather request at all |

## Permissions the user has to grant

1. **Display over other apps** (`SYSTEM_ALERT_WINDOW`) for the overlay itself.
2. **Notification access** (`BIND_NOTIFICATION_LISTENER_SERVICE`) for the content.
3. **SMS** (`RECEIVE_SMS`), optional, for codes that never reach the shade.
4. **Phone** (`READ_PHONE_STATE`), optional, for incoming-call cards. On Android 12+ the card still
   says 未知号码, because the caller id is no longer shared with third-party apps.
5. **Usage access** (`PACKAGE_USAGE_STATS`), optional but required for the desktop-only rule and for
   the standby pill: it is how the app knows the launcher is in front. One cheap query every 2.5 s
   while a track plays, and one every 4 s while the standby pill is active.
6. **Nearby devices** (`BLUETOOTH_CONNECT`), optional and only asked for on Android 12+, for the names
   and the connected-device list behind the battery chips.
7. On MIUI / HyperOS also enable **后台弹出界面** for this app, otherwise the system drops overlays
   started from the background.
8. Android 13+ asks for `POST_NOTIFICATIONS` so the foreground service can show its status
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
  full-screen intent covers everything. The card is withdrawn a minute after the alarm time, and
  `AlarmWatcher.schedule` retracts it as soon as `getNextAlarmClock()` stops reporting that exact
  alarm. One alarm is announced once: the trigger time is remembered in `Prefs.KEY_ALARM_SHOWN`.
  A ROM that keeps reporting an alarm the clock app no longer lists would still produce one card;
  the 闹钟 switch is the way out of that.
- 导航 depends on the map app publishing the next turn as an ongoing notification. The island only
  shows the latest line.
- 音乐 shows what the player publishes. Real per-line lyrics are not obtainable offline: 网易云音乐
  keeps its lyrics inside its own UI and puts nothing in the media session, so the line comes from the
  public endpoint on `music.163.com` over HTTPS, once per track, only while 联网歌词 is on, capped at
  512 KB, cached in memory only. That endpoint gives one timestamp per line, not per syllable, so the
  character-by-character light-up is interpolated linearly inside each line: it shows which line is
  being sung and roughly how far into it the singer is, not word accuracy. When the lookup fails or
  the switch is off, that line shows the song name instead.
- The music strip and the standby pill are **desktop only** and need the **usage access** grant;
  without it the app cannot tell the launcher from an app, so the strip falls back to showing over any
  screen and the standby pill never appears.
- The temperature needs a city typed by hand and hits Open-Meteo; the accessory percentage needs the
  device to report and `BLUETOOTH_CONNECT`. Neither is a promise the system makes.

## Build

Locally, with JDK 17 and the Android SDK (`compileSdk 34`):

```bash
cd android
gradle assembleRelease
# android/app/build/outputs/apk/release/app-release.apk
```

Signing is pinned: `app/build.gradle.kts` reads `ANDROID_KEYSTORE_PATH`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD` and uses that PKCS12
keystore when they are set, so consecutive releases install over each other. Without them it falls
back to the debug key, and such a build needs an uninstall first. The key is not in this repo: it
lives in the repository secrets and in the maintainer's offline backup. Losing it means exactly one
more uninstall.

Or push to the `android-apk` branch / run the **Build Android APK** workflow by hand; it builds the
APK and attaches it to a GitHub release.

## Install on the phone

1. Download the `.apk` from the release assets.
2. Allow installs from unknown sources for the app you download with (MIUI: 设置 → 应用设置 →
   特殊权限 → 安装未知应用).
3. Open the app, grant the permissions, press 启动灵动岛, then 预览效果.

## Version numbers

`versionName` is `2.<batch>.<run>`, `versionCode` is the workflow run number, and the release tag is
`island-v2.<batch>.<run>`. The middle digit is bumped once per feature batch, so released series skip
numbers: failed runs never ship, and each batch starts a new middle digit on purpose.

## Which download for which phone

One APK. There is nothing to choose: the app is plain Java in `classes.dex` with no native code, so
there is no `lib/` folder and therefore no per-ABI split, and the only device-specific decisions are
made at runtime (the launcher package is resolved through `CATEGORY_HOME`, the foreground package
through `UsageEvents`). `minSdk 26` means Android 8.0 and newer; `compileSdk`/`targetSdk 34` means it
is built and checked against Android 14. That one file installs on 小米 / 红米 / POCO with MIUI 12-14
or HyperOS, and on other skins, with the caveats listed above.

Splitting it per system would only be packaging theatre: the differences between those phones are
permissions the user grants and runtime detection, not code that can be compiled out.
