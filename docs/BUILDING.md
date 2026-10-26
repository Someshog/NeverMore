# Building NeverMore

This guide covers building the app, rebuilding the native ad-block library, and installing on a TV.

## Requirements

| Tool | Version | Needed for |
|---|---|---|
| JDK | 17 or newer | Building the app |
| Android SDK | API 35 (platform + build tools) | Building the app |
| Android Studio | Recent stable (optional) | Easiest way to build and run |
| Rust + `cargo-ndk` | Stable | **Only** if you change `native/` |
| Android NDK | Recent (side-by-side) | **Only** if you change `native/` |

Prebuilt `libadblock_jni.so` files are already in `app/src/main/jniLibs/` for `armeabi-v7a`, `arm64-v8a`, `x86` and `x86_64`, so most contributors never need Rust.

## Build the app

### Android Studio

1. **File → Open…** and choose the project folder.
2. Wait for Gradle to sync.
3. Pick a TV device or emulator and click **Run**.

### Command line

```bash
./gradlew assembleDebug          # Windows: .\gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

For a clean rebuild: `./gradlew clean assembleDebug`.

### Release build

```bash
./gradlew assembleRelease
```

The release APK is **unsigned**. Sign it with your own keystore before installing or publishing, and **never commit a keystore or its passwords** to the repository.

## Install on a TV

1. On the TV, enable **Developer options** (tap *Build number* in *Settings → About* seven times) and turn on **USB debugging** or **Network debugging**.
2. Connect and install:
   ```bash
   adb connect <TV_IP_ADDRESS>:5555
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.nevermore.app/.MainActivity
   ```
3. If something looks stale after an update, uninstall first so cached filter lists and scripts are cleared:
   ```bash
   adb uninstall com.nevermore.app
   ```

## Which CPU does my TV use?

```bash
adb shell getprop ro.product.cpu.abilist
```

| Output includes | Library used |
|---|---|
| `arm64-v8a` | 64-bit ARM (most newer TVs and sticks) |
| `armeabi-v7a` only | 32-bit ARM (older TVs, some Fire TV sticks) |
| `x86` / `x86_64` | Emulators and some Intel devices |

## Rebuild the native library

You only need this when you change code in `native/`.

1. Install Rust targets and `cargo-ndk`:
   ```bash
   rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
   cargo install cargo-ndk
   ```
2. Install the NDK (Android Studio → *SDK Manager → SDK Tools → NDK (Side by side)*) and set `ANDROID_NDK_HOME` to its folder.
3. Build all ABIs into `jniLibs`:
   ```bash
   cd native
   cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -t x86 -o ../app/src/main/jniLibs build --release
   ```
4. Rebuild the app. In logcat you should see `Native library load: success=true`.

> ⚠️ **JNI names are tied to the package.** The functions in `native/src/lib.rs` are named `Java_com_nevermore_app_BraveEngine_*`. If you rename the Kotlin package or the `BraveEngine` class, rename the functions to match **and rebuild every ABI**, or the library will fail to load and ad blocking will silently turn off.

## Continuous integration

Every push and pull request is built by GitHub Actions (`.github/workflows/build.yml`). The debug APK is attached to each run as an artifact.
