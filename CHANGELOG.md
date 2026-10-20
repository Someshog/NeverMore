# Changelog

All notable changes to NeverMore are documented here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project aims to follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- `yt_adblock.js`: removes ad data (`adPlacements`, `playerAds`, `adSlots`) from YouTube API responses, injected at document start, with a player-level skip fallback.
- Blocking of YouTube's own ad endpoints and Google ad domains.
- Prebuilt native library for `armeabi-v7a` (32-bit ARM TVs and streaming sticks).
- Documentation: architecture, building and troubleshooting guides, contributing guide, code of conduct, security policy, privacy policy, disclaimer and third-party notices.
- GitHub issue and pull request templates, and a build workflow.

### Changed
- Renamed the project to **NeverMore** (the internal package `com.bravetv.app` is unchanged for now, because the native library depends on it).
- Added the `androidx.webkit` dependency.

## [1.0.0] - 2026

### Added
- Android TV launcher with YouTube TV, JioHotstar and Twitch shortcuts.
- WebView container with D-pad and media-key support.
- Native ad-block engine using `adblock-rust` with EasyList, uBlock Origin and Brave filter lists.
- HLS manifest cleaner for ad-break markers.
- Cosmetic filtering.

[Unreleased]: https://github.com/Someshog/NeverMore/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/Someshog/NeverMore/releases/tag/v1.0.0
