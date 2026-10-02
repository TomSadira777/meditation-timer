# 冥想 Meditation Timer — Android

A WebView-hosted meditation timer with an OS-guaranteed bell.

## Why this exists

As a PWA the timer would occasionally fail to ring: Android suspended the
page, the JS countdown stopped advancing, and the session ran past its end
with no bell. Two things fix that here.

1. **The countdown is derived from wall-clock timestamps**, never
   decremented. Suspend the process for ten minutes and the remaining time
   is still correct the instant it resumes.
2. **The bell is an exact `AlarmManager` alarm**, registered with the OS and
   scheduled with `setExactAndAllowWhileIdle`, so it fires through Doze
   whether or not the webview is alive.

## Layout

```
app/src/main/
  assets/index.html        the entire UI — unchanged from the PWA
  res/raw/gong.mp3         extracted from the HTML's base64 blob
  java/.../MainActivity.kt WebView host, immersive mode, JS bridge
  java/.../TimerService.kt foreground service, alarms, audio
  java/.../BellReceiver.kt alarm callback
```

`index.html` runs unmodified in a desktop browser — the native bridge is
feature-detected, so every native call is a no-op outside the APK. Edit it
and test in a browser; the APK picks up the same file.

## Division of labour

| Concern            | Owner                       |
|--------------------|-----------------------------|
| Countdown display  | WebView (JS)                |
| Pause / resume     | WebView, relayed to service |
| Audible bell       | TimerService (native)       |
| Staying alive      | Foreground service          |
| Screen stays on    | `FLAG_KEEP_SCREEN_ON`       |

Inside the APK the webview deliberately does **not** play the gong — the
service does, or you would hear it twice.

## Building

Push to `main`, or hit **Run workflow** on the Actions tab. The APK lands as
a downloadable artifact on the run page.

The release build is signed with the debug key so it installs directly.
Replace that with a real keystore before distributing to anyone else.

## Installing

Download the artifact, unzip, open the `.apk`, and allow installs from your
browser or file manager when prompted.
