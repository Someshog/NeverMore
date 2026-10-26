# Changelog

All notable changes to NeverMore are documented here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project aims to follow [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-10-27

### Added
- **Android TV Launcher**: Leanback UI built with Jetpack Compose for TV with YouTube TV shortcut.
- **Dedicated 10-Foot WebView**: Configured with smart-TV user agent (Tizen/SmartHub) for the YouTube TV interface, full D-pad navigation, and media key support (Play/Pause).
- **Remote Navigation Handling**: Back button sends synthetic Back event to the web page to navigate YouTube TV's single-page app history cleanly without trapping the user. Long-press Back exits the application.
- **Multi-Layered Ad Blocking**:
  - **Native Filter Engine**: Powered by Brave's `adblock-rust` compiled via JNI for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.
  - **Filter Lists**: EasyList, uBlock Origin filters, Brave specific, and Brave unbreak lists, cached for 24 hours with local offline fallback.
  - **Document-Start Script Injection**: `yt_adblock.js` injected via `WebViewCompat.addDocumentStartJavaScript` to prune ad metadata (`adPlacements`, `playerAds`, `adSlots`) from YouTube API responses (`fetch`, `XMLHttpRequest`, `JSON.parse`) before the player loads.
  - **Player Fallback**: In-player ad detection that mutes, fast-forwards, and triggers skip on lingering ads.
  - **Network Interception**: Blocks YouTube tracking and ad endpoints (`/pagead/`, `/ptracking`, `/api/stats/ads`, DoubleClick, Google Ad Services).
  - **Stream Protection Allowlist**: Safe-lists critical playback infrastructure (`googlevideo.com`, `/s/player/`, `generate_204`, thumbnails) to prevent playback stalls.
  - **Cosmetic Filtering**: Dynamic CSS injection hiding lingering ad containers and promotional overlays.
  - **HLS Playlist Cleaner**: Sanitizes `.m3u8` streams by stripping ad cues (`#EXT-X-CUE-OUT`, SCTE-35).
- **Hardware Codec Optimization**: `yt_codec.js` overrides `MediaSource.isTypeSupported` and `canPlayType` to steer YouTube away from software-decoded formats (like AV1 on unsupported TVs) to smooth hardware-accelerated playback.
- **Robust Fullscreen & System UI**: `WebChromeClient.onShowCustomView` implementation with sticky immersive mode to prevent black-screen video playback issues.
- **Comprehensive Project Docs**: Architecture, building, troubleshooting guides, contributing guidelines, security policy, privacy statement, and legal disclaimer.
- **CI/CD Workflow**: GitHub Actions build workflow for automated debug APK compilation.

[1.0.0]: https://github.com/Someshog/NeverMore/releases/tag/v1.0.0
