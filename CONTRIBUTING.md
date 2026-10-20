# Contributing to NeverMore

Thanks for your interest in NeverMore! Every contribution helps, whether it's a bug report, a fix to the ad-pruning script after YouTube changes something, a doc improvement, or a new feature. Everyone taking part is expected to follow our [Code of Conduct](CODE_OF_CONDUCT.md).

## Ways to help

- 🐛 **Report a bug.** Especially "ads are showing again" reports with logcat output.
- 💡 **Suggest an idea** through a feature request.
- 📝 **Improve the docs.** Typos, clearer steps, and screenshots are all welcome.
- 🔧 **Send a fix.** The easiest high-value fixes are in `app/src/main/assets/yt_adblock.js`.
- 📺 **Test on devices.** Tell us what works and what doesn't on your TV or streaming stick.

## Reporting bugs

Before opening an issue, check [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) and search existing issues. A good report includes:

- Device model, Android version, and CPU (`adb shell getprop ro.product.cpu.abilist`)
- Android System WebView version
- NeverMore version
- What you expected and what happened
- Logcat output captured while the problem occurs:
  ```bash
  adb logcat -s NeverMore BraveEngine
  ```

> 🔒 Security problems should **not** go in public issues. See [SECURITY.md](SECURITY.md).

## Development setup

1. Fork the repo and clone your fork.
2. Install JDK 17+ and the Android SDK (API 35).
3. Build: `./gradlew assembleDebug`
4. Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

You only need Rust and the NDK if you change the native library. See [docs/BUILDING.md](docs/BUILDING.md).

## Making changes

1. Create a branch from `main`: `git checkout -b fix/short-description`
2. Make your change, keeping it focused. One topic per pull request is easiest to review.
3. Build and test on a device or emulator when you can. Say in the PR what you tested.
4. Commit with a clear message (see below).
5. Open a pull request and fill in the template.

### Commit messages

Use a short summary in the imperative mood, about 70 characters or fewer:

```
Fix pruning of adSlots in /next responses
```

Add a blank line and more detail if the *why* isn't obvious.

### Code style

- **Kotlin:** follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html). Keep functions small and comment the non-obvious.
- **JavaScript (`yt_adblock.js`):** ES5-style syntax (`var`, no arrow functions) so it runs on old TV WebViews. Wrap risky code in `try/catch`. A bug in this script must never break the page.
- **Rust:** run `cargo fmt` and `cargo clippy`.
- Don't add analytics, tracking, or network calls to third-party services. See [PRIVACY.md](PRIVACY.md).

### Important: the native library and package name

The Rust library's JNI function names include the Kotlin package and class (`Java_com_bravetv_app_BraveEngine_*`). If you rename either, you must update `native/src/lib.rs` **and rebuild all `.so` files**, or ad blocking will silently stop working. Include the rebuilt libraries in the same pull request.

## Updating the YouTube ad pruning

When ads reappear, the usual cause is YouTube renaming a field. To investigate:

1. Capture a player response (`/youtubei/v1/player` or `/next`) in a desktop browser's dev tools.
2. Look for the keys that describe ads and compare them with `AD_KEYS` / `AD_ITEM_KEYS` in `yt_adblock.js`.
3. Update the lists, test on a TV, and open a PR describing what changed.

[uBlock Origin's scriptlets](https://github.com/gorhill/uBlock/wiki/Resources-Library) are a good reference for current patterns.

## Licensing of contributions

By submitting a contribution, you agree that it is licensed under the project's [MIT License](LICENSE), and that you have the right to submit it. Do not copy code from sources with incompatible licenses (for example GPL code) into the MIT-licensed parts of the project. If you are unsure, ask in the pull request.

## Questions?

Open a discussion or an issue. We're happy to help new contributors get started.
