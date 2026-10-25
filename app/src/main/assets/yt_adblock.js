/*
 * Runs at document start (before YouTube's own scripts) and strips ad data out of the
 * player response so the player never receives any ads to play.
 * Same idea as uBlock Origin's json-prune / json-prune-fetch-response scriptlets.
 *
 * Performance notes: this runs on YouTube's main thread, so it does as little work as
 * possible. It only touches the player endpoint, and only parses responses it has to.
 */
(function () {
  if (window.__ytAdPruneInstalled) return;
  window.__ytAdPruneInstalled = true;

  var AD_KEYS = ['adPlacements', 'playerAds', 'adSlots', 'adBreakHeartbeatParams'];
  // Only the player endpoint carries the ad list. Feeds (browse/search) are left alone so
  // large UI responses are not rewritten, which was a major cause of stutter.
  var PLAYER_RE = /\/youtubei\/v1\/player(\?|$)/;

  function pruneAds(data) {
    if (!data || typeof data !== 'object') return data;
    for (var i = 0; i < AD_KEYS.length; i++) {
      if (AD_KEYS[i] in data) {
        try { delete data[AD_KEYS[i]]; } catch (e) { data[AD_KEYS[i]] = undefined; }
      }
    }
    return data;
  }

  // Fetch: rewrite only the player response.
  var origFetch = window.fetch;
  if (origFetch) {
    window.fetch = function (input, init) {
      var url = typeof input === 'string' ? input : (input && input.url) || '';
      var p = origFetch.apply(this, arguments);
      if (!PLAYER_RE.test(url)) return p;
      return p.then(function (res) {
        return res.text().then(function (text) {
          var data;
          try { data = JSON.parse(text); } catch (e) {
            return new Response(text, { status: res.status, statusText: res.statusText, headers: res.headers });
          }
          pruneAds(data);
          var headers = new Headers(res.headers);
          headers.delete('content-length');
          return new Response(JSON.stringify(data), { status: res.status, statusText: res.statusText, headers: headers });
        });
      });
    };
  }

  // XHR: rewrite the player response once per response, not on every property read.
  var xhrOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function (method, url) {
    this.__ytPlayer = PLAYER_RE.test(String(url || ''));
    return xhrOpen.apply(this, arguments);
  };
  var textDesc = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'responseText');
  if (textDesc && textDesc.get) {
    var lastRaw = null, lastPruned = null;
    Object.defineProperty(XMLHttpRequest.prototype, 'responseText', {
      configurable: true,
      get: function () {
        var raw = textDesc.get.call(this);
        if (!this.__ytPlayer || typeof raw !== 'string' || !raw) return raw;
        if (raw === lastRaw) return lastPruned; // cached: the same response is read many times
        try {
          lastPruned = JSON.stringify(pruneAds(JSON.parse(raw)));
          lastRaw = raw;
          return lastPruned;
        } catch (e) { return raw; }
      }
    });
  }

  // Initial player response embedded in the page HTML.
  ['ytInitialPlayerResponse'].forEach(function (name) {
    var stored;
    try {
      Object.defineProperty(window, name, {
        configurable: true,
        get: function () { return stored; },
        set: function (v) { stored = pruneAds(v); }
      });
    } catch (e) { }
  });

  // Fallback: if an ad still reaches the player, mute it and jump to the end. The original
  // mute and speed are restored afterwards, because YouTube reuses one <video> for ads and
  // for the real video.
  var savedState = null;
  function skipFallback() {
    var player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
    var adShowing = player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'));
    var video = adShowing ? document.querySelector('video') : null;
    if (video) {
      if (!savedState) savedState = { muted: video.muted, rate: video.playbackRate };
      if (isFinite(video.duration) && video.duration > 0) {
        video.muted = true;
        video.playbackRate = 16;
        video.currentTime = video.duration;
      }
    } else if (savedState) {
      var v = document.querySelector('video');
      if (v) {
        v.muted = savedState.muted;
        v.playbackRate = savedState.rate;
      }
      savedState = null;
    }
    if (adShowing) {
      var skip = document.querySelector('.ytp-skip-ad-button, .ytp-ad-skip-button, .ytp-ad-skip-button-modern');
      if (skip && skip.offsetParent !== null) skip.click();
    }
    return !!adShowing;
  }
  // Poll quickly while a video starts or an ad is showing, then slow down for normal playback.
  var fastPollUntil = Date.now() + 5000;
  function markVideoStart(event) {
    var target = event.target;
    if (target && target.tagName === 'VIDEO') fastPollUntil = Date.now() + 5000;
  }
  document.addEventListener('loadstart', markVideoStart, true);
  document.addEventListener('playing', markVideoStart, true);
  (function pollForAds() {
    var adShowing = skipFallback();
    var video = document.querySelector('video');
    var normalPlayback = video && !video.paused && video.readyState >= 2 && !adShowing;
    if (normalPlayback) fastPollUntil = 0;
    var fastPolling = adShowing || Date.now() < fastPollUntil;
    setTimeout(pollForAds, fastPolling ? 300 : 1000);
  })();

  // Hide ad overlays with one CSS rule. No polling, no DOM observer.
  var HIDE_CSS = [
    '.ytp-ad-overlay-container', '.ytp-ad-text-overlay', '.ytp-ad-player-overlay-instream-info',
    '#player-ads', '.ytd-banner-promo-renderer', 'ytd-action-companion-ad-renderer',
    'ytd-display-ad-renderer', 'ytd-promoted-sparkles-web-renderer', '.ytp-ce-element'
  ].join(',') + '{display:none!important}';
  function addHideStyle() {
    try {
      var st = document.createElement('style');
      st.textContent = HIDE_CSS;
      (document.head || document.documentElement).appendChild(st);
    } catch (e) { }
  }
  if (document.documentElement) addHideStyle();
  else document.addEventListener('DOMContentLoaded', addHideStyle);
})();
