/*
 * Runs at document start (before YouTube's own scripts) and strips ad data out of
 * YouTube API responses so the player never receives any ads to play.
 * Same idea as uBlock Origin's json-prune / json-prune-fetch-response / set-constant scriptlets.
 */
(function () {
  if (window.__ytAdPruneInstalled) return;
  window.__ytAdPruneInstalled = true;

  var AD_KEYS = ['adPlacements', 'playerAds', 'adSlots', 'adBreakHeartbeatParams'];
  var AD_ITEM_KEYS = ['adSlotRenderer', 'promotedSparklesWebRenderer', 'displayAdRenderer', 'compactPromotedVideoRenderer'];
  var API_RE = /\/youtubei\/v1\/(player|next|browse|get_watch|reel\/reel_item_watch|search)/;

  function prune(node, depth) {
    if (!node || typeof node !== 'object' || depth > 8) return node;
    if (Array.isArray(node)) {
      for (var i = node.length - 1; i >= 0; i--) {
        var item = node[i];
        if (item && typeof item === 'object' && hasAdItem(item)) {
          node.splice(i, 1);
        } else {
          prune(item, depth + 1);
        }
      }
      return node;
    }
    for (var k = 0; k < AD_KEYS.length; k++) {
      if (AD_KEYS[k] in node) {
        try { delete node[AD_KEYS[k]]; } catch (e) { node[AD_KEYS[k]] = undefined; }
      }
    }
    for (var key in node) {
      if (Object.prototype.hasOwnProperty.call(node, key)) prune(node[key], depth + 1);
    }
    return node;
  }

  function hasAdItem(item) {
    for (var i = 0; i < AD_ITEM_KEYS.length; i++) {
      if (AD_ITEM_KEYS[i] in item) return true;
      // renderers are often wrapped one level down, e.g. {richItemRenderer:{content:{adSlotRenderer:{}}}}
      var inner = item.richItemRenderer && item.richItemRenderer.content;
      if (inner && AD_ITEM_KEYS[i] in inner) return true;
    }
    return false;
  }

  function looksLikePlayerData(obj) {
    return obj && typeof obj === 'object' &&
      (obj.streamingData || obj.playabilityStatus || obj.adPlacements || obj.playerAds || obj.contents || obj.playerResponse);
  }

  // 1. JSON.parse (covers pages that parse the response text themselves)
  var origParse = JSON.parse;
  JSON.parse = function () {
    var result = origParse.apply(this, arguments);
    try { if (looksLikePlayerData(result)) prune(result, 0); } catch (e) {}
    return result;
  };

  // 2. fetch (covers Response.json(), which does NOT go through JSON.parse)
  var origFetch = window.fetch;
  if (origFetch) {
    window.fetch = function (input, init) {
      var url = typeof input === 'string' ? input : (input && input.url) || '';
      var p = origFetch.apply(this, arguments);
      if (!API_RE.test(url)) return p;
      return p.then(function (res) {
        try {
          return res.clone().text().then(function (text) {
            var data;
            try { data = origParse(text); } catch (e) { return res; }
            prune(data, 0);
            var headers = new Headers(res.headers);
            headers.delete('content-length');
            return new Response(JSON.stringify(data), { status: res.status, statusText: res.statusText, headers: headers });
          });
        } catch (e) { return res; }
      });
    };
  }

  // 3. XMLHttpRequest (YouTube TV historically uses XHR for some calls)
  var xhrOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function (method, url) {
    this.__ytUrl = String(url || '');
    return xhrOpen.apply(this, arguments);
  };
  ['response', 'responseText'].forEach(function (prop) {
    var desc = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, prop);
    if (!desc || !desc.get) return;
    Object.defineProperty(XMLHttpRequest.prototype, prop, {
      configurable: true,
      get: function () {
        var value = desc.get.call(this);
        if (!this.__ytUrl || !API_RE.test(this.__ytUrl) || typeof value !== 'string' || !value) return value;
        try {
          var data = origParse(value);
          prune(data, 0);
          return JSON.stringify(data);
        } catch (e) { return value; }
      }
    });
  });

  // 4. Initial player response embedded in the page HTML
  ['ytInitialPlayerResponse', 'ytInitialData'].forEach(function (name) {
    var stored;
    try {
      Object.defineProperty(window, name, {
        configurable: true,
        get: function () { return stored; },
        set: function (v) { try { prune(v, 0); } catch (e) {} stored = v; }
      });
    } catch (e) {}
  });

  // 5. Fallback for anything that still slips through: skip the ad at the player level.
  function skipFallback() {
    var video = document.querySelector('video');
    var player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
    var adShowing = player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'));
    if (video && adShowing && isFinite(video.duration) && video.duration > 0) {
      video.muted = true;
      video.playbackRate = 16;
      video.currentTime = video.duration;
    }
    var skip = document.querySelector('.ytp-skip-ad-button, .ytp-ad-skip-button, .ytp-ad-skip-button-modern, #movie_player [class*="skip-button"], .html5-video-player [class*="skip-button"]');
    if (skip && skip.offsetParent !== null) skip.click();
  }
  setInterval(skipFallback, 250);
  // 6. Hide ad overlays with CSS. One-time style rule, so there is no polling or DOM observer
  //    (the old script ran both and could slow D-pad scrolling on weak TVs).
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
    } catch (e) {}
  }
  if (document.documentElement) addHideStyle();
  else document.addEventListener('DOMContentLoaded', addHideStyle);
})();
