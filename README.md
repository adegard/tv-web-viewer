# TV Web Viewer

An Android TV / Fire TV web viewer with built-in ad blocking, remote-friendly
navigation, and a configurable startup page.

## Features

- **Ad blocking**: blocks ad, tracker, and video-ad requests. Ships with a
  starter blocklist (`library/src/main/assets/adblock/blocklist.txt`) that you
  can extend, plus injected JS hooks that neutralize popups, pop-unders,
  `document.write` ad scripts, and anti-adblock gates.
- **TV navigation**: on-screen mouse cursor controlled with the D-pad
  (auto-hides after 5s of inactivity), OK clicks, FF/RW scroll the page.
- **Configurable startup page**: click the top-right corner of the page to open
  a dialog that sets which page loads on startup. Otherwise the last visited
  page is restored.
- **Smart URL entry**: on-screen keyboard so text input works without relying
  on the system IME.

## Modules

| Module    | Description                                              |
|-----------|----------------------------------------------------------|
| `library` | Reusable `AdBlockWebView` + blocking engine              |
| `sample`  | Runnable TV app demonstrating the library                |

## Build

Requires JDK 17 and an Android SDK (compileSdk 34).

```bash
./gradlew :sample:assembleDebug
```

The APK is produced at `sample/build/outputs/apk/debug/sample-debug.apk`.
A ready-to-install build is also included in `release/`.

## Install on Fire TV / Android TV

With ADB over the network enabled on the device:

```bash
adb install -r release/tv-web-viewer-debug.apk
```

## Controls

- **D-pad**: move the mouse cursor
- **OK**: click at the cursor position
- **FF / RW**: scroll the page
- **Menu**: toggle cursor mode on/off
- **Top-right corner click**: set the startup page