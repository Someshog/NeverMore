<div align="center">

# NeverMore 📺🛡️

**Open-source, ad-blocking web app launcher for Android TV.**

Open YouTube TV on the big screen with ad and tracker blocking built in, powered by the same filtering engine Brave uses.

[![Build](https://github.com/Someshog/NeverMore/actions/workflows/build.yml/badge.svg)](https://github.com/Someshog/NeverMore/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Platform](https://img.shields.io/badge/platform-Android%20TV%20%7C%20Google%20TV%20%7C%20Fire%20TV-3ddc84)
![Min SDK](https://img.shields.io/badge/minSdk-23-informational)
[![PRs welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

[Features](#-features) · [Quick start](#-quick-start) · [How it works](#-how-it-works) · [Docs](#-documentation) · [Contributing](#-contributing) · [Legal](#%EF%B8%8F-legal)

</div>

---

> **Personal use only.** Blocking ads on YouTube is against YouTube's Terms of Service, and you use NeverMore at your own risk. It is meant to be sideloaded for personal use and is not available on Google Play. If you enjoy a creator's work, consider supporting them directly or subscribing to YouTube Premium. Please read the [Disclaimer](DISCLAIMER.md).

## ✨ Features

- 🛋️ **Built for the couch.** Leanback launcher, D-pad navigation, media-key support (Play/Pause), and a smart-TV user agent so YouTube loads its 10-foot TV interface.
- 🎮 **Intuitive TV remote Back button.** Dispatches synthetic navigation events into the page so Back steps backward through YouTube's internal history rather than trapping you. Long-press Back exits to home.
- ⚡ **Fast native filtering.** Rust bindings to [`adblock-rust`](https://github.com/brave/adblock-rust), with EasyList, uBlock Origin and Brave lists, refreshed daily.
- 🎯 **Targets in-stream ads.** Strips ad metadata (`adPlacements`, `playerAds`, `adSlots`) from YouTube API responses before the player initializes, with a player-level skip as backup.
- 🎬 **Hardware codec optimization.** Injects script overrides to steer YouTube toward hardware-accelerated video codecs, eliminating dropped frames on TVs that lack AV1 hardware decoding.
- 🧹 **Cleaner pages.** Cosmetic filtering hides leftover ad elements and promotional overlays.
- 🛡️ **Safe by default.** An allowlist protects video CDN endpoints (`googlevideo.com`) and player scripts from being blocked.
- 🔒 **No accounts, no analytics.** NeverMore doesn't track you or phone home. See [PRIVACY.md](PRIVACY.md).
- 📦 **Wide device support.** Prebuilt native libraries for `armeabi-v7a`, `arm64-v8a`, `x86` and `x86_64`.

## 🚀 Quick start

### Install on your TV (no computer needed)

You need your **phone** and your **TV**, both on the **same Wi-Fi network**. It takes about five minutes.

**Step 1. Download the app on your phone.**
Open the [Releases page](https://github.com/Someshog/NeverMore/releases/latest) on your phone. Under **Assets**, tap `NeverMore-v1.0.0.apk` (the name ends in `.apk`) to download it.

**Step 2. Get LocalSend on both devices.**
[LocalSend](https://localsend.org) is a free, open-source app that sends files over your Wi-Fi. Install it on your **phone** and on your **TV** from their app stores, then open it on both.

**Step 3. Send the APK to the TV.**
In LocalSend on your phone, tap **Select file** and choose the `NeverMore` APK. Your TV appears in the list of nearby devices. Tap it, then **accept** the file on the TV.

**Step 4. Let your TV install apps from outside the store.**
This is a one-time setting. On the TV, open **Settings** and look for **Install unknown apps** (it may be under *Apps → Security & restrictions*, depending on your TV). Allow it for the app you will use to open the file (LocalSend or your file manager).

**Step 5. Install it.**
On the TV, open the file you just received (in LocalSend's received files, or in a file manager under *Downloads*). Select it, then choose **Install**. If a Play Protect message appears, choose **Install anyway**.

**Step 6. Open NeverMore.**
Find **NeverMore** in your TV's app row and start watching.

<details>
<summary><b>Something not working?</b></summary>

| Problem | What to try |
|---|---|
| The TV doesn't show up in LocalSend | Make sure the phone and TV are on the same Wi-Fi. Guest networks and some routers block device-to-device traffic, so try the other Wi-Fi band or a different network. |
| "Install blocked" or nothing happens when you open the file | Repeat Step 4 for the app you used to open the file. |
| "App not installed" | Remove any older copy (including the old BraveTV build) in *Settings → Apps*, then install again. |
| The file won't open on the TV | Install a file manager from the TV's app store, such as **Files by Google**, and open the APK from *Downloads*. |

</details>

**Other ways to install**

- **Downloader app.** Install **Downloader** (by AFTVnews) on the TV, allow it to install unknown apps, and enter the APK link from the [Releases page](https://github.com/Someshog/NeverMore/releases/latest).
- **USB stick.** Copy the APK to a USB drive, plug it into the TV, and open it with a file manager.
- **ADB (advanced).** Turn on **Developer options → Network debugging** on your TV, then run:
  ```bash
  adb connect <TV_IP_ADDRESS>:5555
  adb install -r NeverMore-v1.0.0.apk
  adb shell am start -n com.nevermore.app/.MainActivity
  ```

**Updating:** download the new APK and install it the same way. Your settings are kept.

### Build from source

```bash
git clone https://github.com/Someshog/NeverMore.git
cd NeverMore
./gradlew assembleDebug        # Windows: .\gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Requirements are JDK 17+ and the Android SDK (API 35). Full instructions, including rebuilding the native library, are in [docs/BUILDING.md](docs/BUILDING.md).

> **Which build does my TV need?** Run `adb shell getprop ro.product.cpu.abilist`. The APK includes libraries for all common CPUs, so one APK works on most devices.

## 🔍 How it works

NeverMore blocks ads and optimizes playback in layers, because no single technique is enough:

| Layer | What it does |
|---|---|
| **Network filtering** | Checks each request against filter lists and blocks ad and tracker hosts, including YouTube's own ad endpoints (`/pagead/`, `/ptracking`). |
| **Ad data pruning** | A script (`yt_adblock.js`) injected at document start removes `adPlacements`, `playerAds` and `adSlots` from API responses so the player has no ads to play. |
| **Hardware codec routing** | A script (`yt_codec.js`) injected at document start blocks software-decoded video codecs (e.g. AV1 on low-end TV processors) to ensure smooth playback. |
| **Player fallback** | If an ad still reaches the player, it is muted, skipped to the end, and any skip button is triggered. |
| **Stream allowlist** | Protects video CDN traffic (`googlevideo.com`) and player scripts so playback doesn't stall. |
| **HLS cleaner** | Removes ad-break markers from `.m3u8` playlists on streaming sites. |
| **Cosmetic filtering** | Hides leftover ad elements and banners with injected CSS. |

A deeper explanation is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

> **Heads up:** YouTube changes how it delivers ads every few weeks, so ad blocking can break without warning. When it does, please [open an issue](https://github.com/Someshog/NeverMore/issues/new/choose) with logcat output. Fixes usually only need small changes to the pruning script.

## 📚 Documentation

| Document | What's in it |
|---|---|
| [docs/BUILDING.md](docs/BUILDING.md) | Building the app and the native library, installing on a TV |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | How the app and its blocking layers fit together |
| [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) | Ads still showing, playback problems, logcat tips |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to report bugs and submit changes |
| [CHANGELOG.md](CHANGELOG.md) | What changed in each version |
| [PRIVACY.md](PRIVACY.md) | What data the app does and doesn't touch |
| [DISCLAIMER.md](DISCLAIMER.md) | Terms-of-service, trademark and warranty notices |
| [SECURITY.md](SECURITY.md) | How to report a vulnerability |

## 🤝 Contributing

Contributions of every size are welcome, from fixing a typo to updating the ad-pruning script when YouTube changes. Start with [CONTRIBUTING.md](CONTRIBUTING.md). Please also read our [Code of Conduct](CODE_OF_CONDUCT.md).

## ⚖️ Legal

- NeverMore is released under the [MIT License](LICENSE).
- Third-party components keep their own licenses. See [NOTICE](NOTICE).
- NeverMore is an independent project and is not affiliated with Brave Software, Google or YouTube. See [DISCLAIMER.md](DISCLAIMER.md).

## 🙏 Acknowledgements

NeverMore stands on the shoulders of [adblock-rust](https://github.com/brave/adblock-rust) by Brave, [EasyList](https://easylist.to), and [uBlock Origin's](https://github.com/gorhill/uBlock) filter lists and scriptlet ideas.
