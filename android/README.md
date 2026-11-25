# islandcn for Android (WebView shell)

Upstream islandcn is a Next.js site plus a shadcn registry, not a native app, so there is no
`apk` build anywhere in this repository. This folder adds one: a minimal Android shell that
renders `https://islandcn.dev` in the system WebView, so the React island demos can be installed
and opened from the launcher on a phone.

It is not a system-level dynamic island. Nothing here hooks into MIUI/HyperOS status bar APIs;
the island is still the web component running inside an app window.

## What it is

- `MainActivity.java` — a single `Activity` that owns a full-screen `WebView`. It keeps links to
  `islandcn.dev` in the app, hands every other URL to the browser, maps the back gesture to
  `WebView.goBack()`, shows a progress bar and an offline panel with retry.
- No AndroidX, no third-party dependencies. Only the platform `WebView`.
- `minSdk 26`, `targetSdk 34`, ARM-agnostic (no native code), so it installs on any phone from
  Android 8 upwards, including MIUI builds.
- The release variant is signed with the debug key so CI can produce an installable APK without
  storing a keystore. That makes it fine for sideloading and unsuitable for publishing.

## Build

Locally, with JDK 17 and the Android SDK (`compileSdk 34`):

```bash
cd android
gradle assembleRelease
# android/app/build/outputs/apk/release/app-release.apk
```

Or push to the `android-apk` branch / run the **Build Android APK** workflow by hand; it builds
the APK and attaches it to a GitHub release.

## Install on the phone

1. Download the `.apk` from the release assets.
2. Allow installs from unknown sources for the app you download with (MIUI: 设置 → 应用设置 →
   特殊权限 → 安装未知应用).
3. Tap the APK, confirm the MIUI warning, open `islandcn`.

The app needs network access on first open; nothing is bundled offline.
