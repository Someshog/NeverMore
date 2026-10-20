# Troubleshooting

Start by watching the logs while the problem happens:

```bash
adb logcat -s NeverMore BraveEngine
```

## Ads are still showing

1. **Check that the native library loaded.** Look for `Native library load: success=true`. If you see `Native library NOT available — ad blocking disabled!`, no library matched your TV's CPU. Run `adb shell getprop ro.product.cpu.abilist` and see [BUILDING.md](BUILDING.md).
2. **Check that the script was injected.** If logcat shows `DOCUMENT_START_SCRIPT unsupported on this WebView`, update **Android System WebView** (and Google Chrome, which provides it on some devices) from the Play Store or the device's settings.
3. **Check the filter lists loaded.** Look for `Filter lists fetched: 4/4 non-empty` and `Engine initialized: ... ACTIVE`. If lists are empty, the TV may be offline or blocking `raw.githubusercontent.com` / `easylist.to`.
4. **YouTube may have changed something.** Ads that return after working for a while usually mean a renamed field. Please [open an issue](https://github.com/Someshog/NeverMore/issues/new/choose) with logcat output captured while an ad plays.
5. **Try a clean install.** Cached lists and scripts can go stale:
   ```bash
   adb uninstall com.bravetv.app
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

## YouTube shows a warning or the video stalls

YouTube can detect missing ad data and may show a warning, slow playback, or refuse to load. This usually clears after a script update. Try restarting the app, clearing app data (*Settings → Apps → NeverMore → Clear data*), and checking for a newer release.

## The video won't play or the screen is black

- Make sure Android System WebView is up to date.
- Check logcat for `BLOCKED:` lines that include `googlevideo.com` or `youtube.com/s/player`. Those should never be blocked, so please report it.
- If fullscreen is black, press **Back** once and retry.

## YouTube loads the wrong interface

NeverMore sets a smart-TV user agent so `youtube.com/tv` serves its TV interface. If a site renders badly, report the site and device. Some sites work better with a different user agent.

## The app crashes at startup

- Capture the crash with `adb logcat -b crash`.
- Check that you installed an APK built for this version of the code (stale APKs can mismatch the native library).
- If you rebuilt the native library after renaming the package, make sure you rebuilt **every** ABI.

## Build problems

| Symptom | Fix |
|---|---|
| Gradle can't find the SDK | Set `ANDROID_HOME` or create `local.properties` with `sdk.dir=...` |
| `androidx.webkit` unresolved | **File → Sync Project with Gradle Files** |
| `cargo ndk` can't find the NDK | Set `ANDROID_NDK_HOME` to your NDK folder |
| `./gradlew: Permission denied` | `chmod +x gradlew` |

## Still stuck?

[Open an issue](https://github.com/Someshog/NeverMore/issues/new/choose) and include your device model, Android version, CPU ABI, WebView version, and the logcat output.
