# BraveTv 📺🛡️

**BraveTv** is a privacy-first, ad-blocking Smart TV web app and streaming launcher designed for **Android TV**, **Google TV**, and Leanback-compatible devices.

Powered by Brave's native `adblock-rust` filtering engine, BraveTv allows you to stream content from your favorite web streaming services (such as YouTube TV, Hotstar, Twitch, etc.) on the big screen with high-performance ad-blocking and clean video playback.

---

## ✨ Features

- 🛋️ **TV-First Experience**: Built for Android TV / Google TV with Leanback launcher banner and D-Pad remote navigation.
- ⚡ **Native Brave Ad-Block Engine**: Uses compiled Rust JNI bindings (`adblock-rust`) with high-speed rule matching for EasyList, uBlock Origin, and Brave filter lists.
- 🛡️ **Multi-Tier Ad Mitigation**:
  1. **Network Layer**: Blocks third-party ad networks, telemetry, and tracking endpoints before requests leave the device.
  2. **Smart Stream Allowlist**: Protects video CDN streams (`googlevideo.com`), essential player runtime scripts (`/s/player/`), and connectivity probes so video playback never breaks or stalls.
  3. **HLS Stream Cleanser**: Intercepts `.m3u8` playlists and strips `#EXT-X-CUE-OUT` / SCTE-35 ad break markers in real-time.
  4. **DOM Ad-Skipper & Cosmetic Filter**: Automatically dismisses overlays, fast-forwards in-stream ads, and injects cosmetic CSS hide rules.
- 🖥️ **Smart TV User-Agent**: Configured with a Smart TV User-Agent string to automatically load optimized 10-foot TV interfaces.
- 🎮 **Hardware Remote Integration**: Handles media keys (Play/Pause) and back navigation gracefully.

---

## 🏗️ Architecture

```
┌────────────────────────────────────────────────────────┐
│                   BraveTv Android App                  │
├──────────────────────────┬─────────────────────────────┤
│      MainActivity        │     TvWebViewActivity       │
│  (Compose TV Launcher)   │  (10-ft WebView Container)  │
├──────────────────────────┴─────────────────────────────┤
│                 AdBlockWebViewClient                   │
│   • YouTube Essential Allowlist                        │
│   • HLS Manifest SCTE-35 / CUE-OUT Cleanser            │
│   • Cosmetic CSS Injection & DOM Ad-Skipper            │
├────────────────────────────────────────────────────────┤
│                     BraveEngine                        │
│   • Background rule caching & auto-updates             │
│   • EasyList / uBlock / Brave rule bundles             │
├────────────────────────────────────────────────────────┤
│                 libadblock_jni.so (Rust)               │
│   • High-speed regex / bloom filter matching           │
└────────────────────────────────────────────────────────┘
```

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** (Koala / Ladybug or newer recommended)
- **Android SDK** (Min SDK: 23 / Android 6.0, Target SDK: 35)
- **JDK 17+**
- *(Optional)* **Rust & `cargo-ndk`** — only required if modifying the native Rust engine in `native/`. Pre-compiled libraries for `arm64-v8a`, `x86`, and `x86_64` are already included in `app/src/main/jniLibs/`.

---

## 🛠️ Building from Source

### Using Android Studio

1. Open Android Studio.
2. Select **File → Open...** and choose the `braveTv` folder.
3. Allow Gradle to sync dependencies.
4. Select your target TV device or emulator in the device selector.
5. Click **Run** (`Shift + F10`) or navigate to **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

### Using Command Line (Gradle)

#### Build Debug APK:
```bash
# Windows
.\gradlew assembleDebug

# Linux / macOS
./gradlew assembleDebug
```
The output APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

#### Build Release APK:
```bash
# Windows
.\gradlew assembleRelease

# Linux / macOS
./gradlew assembleRelease
```
The output APK will be located at:
```
app/build/outputs/apk/release/app-release-unsigned.apk
```

---

## 📲 Installation / Sideloading onto Android TV

You can install the APK directly to any Android TV device or emulator using `adb`:

```bash
# Connect to your Android TV (ensure ADB Debugging is enabled on TV)
adb connect <YOUR_TV_IP_ADDRESS>:5555

# Install the APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch the app
adb shell am start -n com.bravetv.app/.MainActivity
```

---

## 🦀 Rebuilding the Native Rust Library (Optional)

If you modify the Rust engine located in `native/`:

1. Install Rust and `cargo-ndk`:
   ```bash
   rustup target add aarch64-linux-android x86_64-linux-android
   cargo install cargo-ndk
   ```
2. Build and output directly into `app/src/main/jniLibs/`:
   ```bash
   cd native
   cargo ndk -t arm64-v8a -t x86_64 -o ../app/src/main/jniLibs build --release
   ```

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
Brave's `adblock-rust` library is subject to its respective upstream Mozilla Public License (MPL 2.0).
