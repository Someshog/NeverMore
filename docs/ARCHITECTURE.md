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
│     Document-start scripts (WebViewCompat injected)    │
│   • yt_adblock.js: prunes ad data from fetch/XHR/JSON  │
│   • yt_codec.js: blocks software-decoded video codecs  │
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
A Jetpack Compose for TV launcher displaying TV-optimized service cards (YouTube TV). Launching a card initiates `TvWebViewActivity` with the site's URL.

### `TvWebViewActivity`
Hosts the full-screen WebView:
- Sets a smart-TV user agent (`Tizen 6.0 SMART-TV`) to load YouTube's 10-foot couch interface.
- Handles custom fullscreen video view (`WebChromeClient.onShowCustomView`) ensuring no black screens or crash on video expand.
- Injects `yt_adblock.js` and `yt_codec.js` via `WebViewCompat.addDocumentStartJavaScript` so they execute **before any page scripts or player initializers**.
- Intercepts D-pad Back key presses: dispatches synthetic Escape/Back events into the page to traverse YouTube TV's internal SPA history, while long-press Back cleanly closes the activity. Media keys (Play/Pause) trigger HTML5 video control.

### `AdBlockWebViewClient`
Overrides `shouldInterceptRequest` to inspect every subresource on background threads:

1. Blocks YouTube ad/tracking endpoints (`/pagead/`, `/ptracking`, `/api/stats/ads`, DoubleClick, Google ad services).
2. Protects essential stream resources (`googlevideo.com`, `/s/player/`, `generate_204`, thumbnails) from being blocked.
3. Checks all other requests against active filter rules in `BraveEngine`.
4. Fetches and cleans `.m3u8` playlists of ad-break cues (`#EXT-X-CUE-OUT`, SCTE-35).
5. Injects cosmetic CSS selectors post-load to hide leftover visual ad units.

### `BraveEngine`
Downloads EasyList, uBlock Origin, and Brave lists (cached for 24 hours, with a bundled offline fallback), builds the native engine, and exposes `shouldBlockUrl` and cosmetic filtering. Remote lists are loaded with scriptlet permissions **disabled**.

### `native/` (Rust)
A small JNI wrapper around [`adblock-rust`](https://github.com/brave/adblock-rust). The engine runs on a dedicated thread, and the app talks to it through opaque handles.

### `assets/yt_adblock.js` & `assets/yt_codec.js`
Injected before any remote scripts run:
- `yt_adblock.js`: Prunes ad objects from player data.
- `yt_codec.js`: Overrides `MediaSource.isTypeSupported` and `HTMLMediaElement.canPlayType` to reject codecs that would trigger laggy software decoding (such as AV1 on hardware lacking hardware decoders), forcing YouTube to deliver hardware-accelerated streams.

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
