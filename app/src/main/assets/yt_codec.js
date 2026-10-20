/*
 * Lets you choose which high-efficiency codec YouTube may use:
 *   PREFERRED = 'none' -> allows BOTH VP9 and AV1 (YouTube chooses freely; max 1080p/4K availability)
 *   PREFERRED = 'vp9'  -> blocks AV1 (locks to hardware VP9)
 *   PREFERRED = 'av1'  -> blocks VP9 (locks to hardware AV1)
 */
(function () {
  if (window.__ytCodecInstalled) return;
  window.__ytCodecInstalled = true;

  var PREFERRED = 'vp9'; // 'none' allows both VP9 and AV1

  var VP9 = /vp09|vp9/i;
  var AV1 = /av01|av1/i;
  var BLOCKED = PREFERRED === 'av1' ? VP9 : PREFERRED === 'vp9' ? AV1 : null;

  function isBlocked(type) {
    return !!BLOCKED && BLOCKED.test(String(type));
  }

  function isAllowedHW(type) {
    var s = String(type);
    return (VP9.test(s) && PREFERRED !== 'av1') || (AV1.test(s) && PREFERRED !== 'vp9');
  }

  try {
    if (window.MediaSource && MediaSource.isTypeSupported) {
      var origIsTypeSupported = MediaSource.isTypeSupported.bind(MediaSource);
      MediaSource.isTypeSupported = function (type) {
        if (isBlocked(type)) return false;
        if (isAllowedHW(type)) return true;
        return origIsTypeSupported(type);
      };
    }
  } catch (e) { }

  try {
    var origCanPlayType = HTMLMediaElement.prototype.canPlayType;
    HTMLMediaElement.prototype.canPlayType = function (type) {
      if (isBlocked(type)) return '';
      if (isAllowedHW(type)) return 'probably';
      return origCanPlayType.call(this, type);
    };
  } catch (e) { }

  try {
    if (navigator.mediaCapabilities && navigator.mediaCapabilities.decodingInfo) {
      var origDecodingInfo = navigator.mediaCapabilities.decodingInfo.bind(navigator.mediaCapabilities);
      navigator.mediaCapabilities.decodingInfo = function (config) {
        var ct = config && config.video && config.video.contentType;
        if (ct && isBlocked(ct)) {
          return Promise.resolve({ supported: false, smooth: false, powerEfficient: false });
        }
        if (ct && isAllowedHW(ct)) {
          return Promise.resolve({ supported: true, smooth: true, powerEfficient: true });
        }
        return origDecodingInfo(config);
      };
    }
  } catch (e) { }
})();