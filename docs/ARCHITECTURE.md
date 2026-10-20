# Architecture

This page explains how NeverMore is put together and why ad blocking uses several layers.

## Overview

```
┌────────────────────────────────────────────────────────┐
│                  NeverMore Android App                 │
├──────────────────────────┬─────────────────────────────┤
│      MainActivity        │     TvWebViewActivity       │
│  (Compose TV launcher)   │  (10-ft WebView container)  │
├──────────────────────────┴─────────────────────────────┤
│                 AdBlockWebViewClient                   │
│   • Intercepts every request                           │
│   • YouTube essential allowlist + ad endpoint blocking │
│   • HLS manifest cleaner                               │
│   • Cosmetic CSS injection                             │
├────────────────────────────────────────────────────────┤
│        yt_adblock.js (injected at document start)      │
│   • Prunes ad data from fetch / XHR / JSON.parse       │
│   • Player-level ad skip fallback                      │
├────────────────────────────────────────────────────────┤
│                     BraveEngine                        │
│   • Downloads and caches filter lists                  │
│   • Kotlin facade for the native library               │
├────────────────────────────────────────────────────────┤
│                 libadblock_jni.so (Rust)               │
│   • adblock-rust: rule matching and cosmetic filters   │
└────────────────────────────────────────────────────────┘
```

## Components

### `MainActivity`
A Jetpack Compose for TV launcher with shortcut cards. Choosing a service starts `TvWebViewActivity` with the site's URL.

### `TvWebViewActivity`
Hosts the WebView. It configures a smart-TV user agent, handles fullscreen video, D-pad back navigation and media keys, and injects `yt_adblock.js` with `WebViewCompat.addDocumentStartJavaScript` so the script runs **before any page script**.

### `AdBlockWebViewClient`
Overrides `shouldInterceptRequest` to look at each request:

1. YouTube's own ad URLs (`/pagead/`, `/ptracking`, `/api/stats/ads`) and Google ad domains get an empty response.
2. An **allowlist** protects essentials (`googlevideo.com`, player scripts, thumbnails) from being blocked by mistake.
3. Everything else is checked against the filter lists through `BraveEngine`.
4. `.m3u8` playlists are fetched and cleaned of ad-break markers (`#EXT-X-CUE-OUT`, SCTE-35).

After a page loads it also injects cosmetic filters that hide leftover ad elements.

### `BraveEngine`
Downloads EasyList, uBlock Origin and Brave lists (cached for 24 hours, with a bundled offline fallback), builds the native engine, and exposes `shouldBlockUrl` and cosmetic filtering. Remote lists are loaded with scriptlet permissions **disabled**.

### `native/` (Rust)
A small JNI wrapper around [`adblock-rust`](https://github.com/brave/adblock-rust). The engine runs on a dedicated thread, and the app talks to it through opaque handles.

### `assets/yt_adblock.js`
Runs in the page before YouTube's code. See below.

## Why several layers?

Blocking by URL works for third-party ad servers, but YouTube serves most ads from its **own** domains, so a URL filter can't tell them apart from videos. The layers cover different cases:

| Ad type | How it is delivered | What handles it |
|---|---|---|
| Third-party ads and trackers | Separate hosts | Network filtering |
| YouTube client-side ads | Listed in the player's JSON response, then fetched separately | **Ad data pruning**: delete the list and there is nothing to play |
| Ads stitched into the stream (SSAI) | Same video stream as the content | **Player fallback**: mute, skip to the end, click skip |
| Ad-break markers in HLS streams | Playlist tags | HLS cleaner |
| Leftover banners and overlays | Page elements | Cosmetic filtering |

## Ad data pruning in detail

`yt_adblock.js` wraps four places where YouTube's data can flow:

1. **`fetch`**: clones the response for `/youtubei/v1/*` endpoints, deletes ad fields, and returns a new `Response`.
2. **`XMLHttpRequest`**: prunes `response` / `responseText` for the same endpoints.
3. **`JSON.parse`**: prunes anything that looks like player data.
4. **`ytInitialPlayerResponse` / `ytInitialData`**: defines setters that prune the data embedded in the page.

The fields removed are `adPlacements`, `playerAds`, `adSlots` and `adBreakHeartbeatParams`, plus sponsored feed items. This is the same idea as uBlock Origin's `json-prune` scriptlet.

Every wrapper is wrapped in `try/catch` and returns the original value on error, so a bug in the script should never break playback.

## Known limits

- **YouTube changes often.** Field names and delivery methods change every few weeks, so expect to update the script.
- **Stitched-in ads** can't be removed from the data. The fallback relies on spotting the ad in the page, and the TV interface's markup differs from the desktop site.
- **Old WebViews** may not support `addDocumentStartJavaScript`. Update Android System WebView on the device.
- **Native library per CPU.** If no library matches the device's CPU, the app runs without network filtering and logs a warning.
