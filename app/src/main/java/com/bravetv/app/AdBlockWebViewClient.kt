package com.bravetv.app

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Uses the native adblock engine and cleans ad markers in accessible HLS playlists. */
class AdBlockWebViewClient : WebViewClient() {
    @Volatile private var sourcePageUrl: String? = null

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        if (!url.isNullOrBlank()) sourcePageUrl = url
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (view == null || url.isNullOrBlank()) return
        sourcePageUrl = url

        runCatching { BraveEngine.getCosmeticResources(url) }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?.let { cosmeticJs -> view.evaluateJavascript(cosmeticJs, null) }

        val collectPageTokens = """JSON.stringify((()=>{const c=new Set(),i=new Set();for(const e of document.querySelectorAll('*')){for(const n of e.classList||[])c.add(n);if(e.id)i.add(e.id);if(c.size>=2000&&i.size>=2000)break}return{classes:[...c].slice(0,2000),ids:[...i].slice(0,2000)}})())"""
        view.evaluateJavascript(collectPageTokens) { encoded ->
            runCatching {
                val decoded = JSONTokener(encoded ?: "{}").nextValue()
                val payload = if (decoded is String) decoded else encoded ?: "{}"
                val page = JSONObject(payload)
                val classes = page.optJSONArray("classes").toStringArray()
                val ids = page.optJSONArray("ids").toStringArray()
                val selectorJson = BraveEngine.getGenericCosmeticSelectors(url, classes, ids) ?: return@runCatching
                val selectors = JSONArray(selectorJson).toStringArray()
                if (selectors.isNotEmpty()) {
                    val css = selectors.joinToString(",") + "{display:none!important;}"
                    view.evaluateJavascript(
                        "(()=>{const s=document.createElement('style');s.textContent=${JSONObject.quote(css)};(document.head||document.documentElement).appendChild(s)})()",
                        null
                    )
                }
            }
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme == "http" || scheme == "https") {
            return false
        }
        return runCatching {
            val intent = android.content.Intent.parseUri(uri.toString(), android.content.Intent.URI_INTENT_SCHEME)
            view?.context?.startActivity(intent)
            true
        }.getOrDefault(true)
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
        super.onReceivedError(view, request, error)
        android.util.Log.e("NeverMore", "WebView error loading ${request?.url}: ${error?.description}")
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        if (request.isForMainFrame) sourcePageUrl = url
        // NOTE: view?.url must NOT be called here — shouldInterceptRequest runs on a background
        // thread and WebView enforces main-thread-only access, throwing on every call and causing ANR.
        // Use sourcePageUrl (a @Volatile field updated safely on any thread) instead.
        val sourceUrl = if (request.isForMainFrame) url else sourcePageUrl ?: url
        val resourceType = resourceType(request)
        if (!request.isForMainFrame && isYouTubeAdUrl(url)) {
            return emptyResponse()
        }
        if (!request.isForMainFrame && !isYouTubeEssentialUrl(url) && BraveEngine.shouldBlockUrl(url, sourceUrl, resourceType)) {
            android.util.Log.d("NeverMore", "BLOCKED: $url")
            return emptyResponse()
        }
        val accept = request.requestHeaders.entries.firstOrNull { it.key.equals("Accept", true) }
            ?.value.orEmpty()
        val isManifest = url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) ||
            accept.contains("application/x-mpegURL", ignoreCase = true)
        if (request.method != "GET" || !isManifest) {
            return super.shouldInterceptRequest(view, request)
        }

        val connection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                instanceFollowRedirects = true
                request.requestHeaders.forEach { (name, value) ->
                    if (!name.equals("Host", true) && !name.equals("Connection", true) && !name.equals("Accept-Encoding", true)) {
                        setRequestProperty(name, value)
                    }
                }
                connect()
            }
        } catch (_: Exception) {
            return null // Fall back to WebView's normal fetch if manifest interception fails.
        }

        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) {
                val manifest = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                ByteArrayInputStream(cleanManifest(manifest).toByteArray(Charsets.UTF_8))
            } else {
                connection.errorStream ?: ByteArrayInputStream(ByteArray(0))
            }
            val mime = "application/x-mpegURL"
            val reason = connection.responseMessage ?: "OK"
            WebResourceResponse(mime, "UTF-8", status, reason, responseHeaders(connection), stream)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /** YouTube's own ad serving/tracking endpoints. These live on youtube.com so generic lists miss them. */
    private fun isYouTubeAdUrl(url: String): Boolean =
        "youtube.com/pagead/" in url ||
            "youtube.com/api/stats/ads" in url ||
            "youtube.com/ptracking" in url ||
            "youtube.com/api/stats/atr" in url ||
            "doubleclick.net" in url ||
            "googleadservices.com" in url ||
            "googlesyndication.com" in url

    private fun emptyResponse() = WebResourceResponse(
        "text/plain", "UTF-8", 204, "Blocked",
        mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0))
    )

    /**
     * Returns true for URLs that are essential to YouTube playback and must never be blocked,
     * even if the native filter engine would otherwise match them.
     *
     * Rules:
     *  - googlevideo.com  — all actual video/audio segments (cdn)
     *  - youtube.com/s/   — player JS bundles (e.g. /s/player/…/tv-player.js)
     *  - youtube.com/generate_204 — connectivity probe; blocking it stalls player init
     *  - youtubei/v1/ API calls EXCEPT those that are purely ad-reporting
     *  - ytimg.com thumbnails / player assets
     *  - yt3.ggpht.com   — channel art, thumbnails
     */
    private fun isYouTubeEssentialUrl(url: String): Boolean {
        return when {
            // Video/audio CDN — never block
            "googlevideo.com" in url -> true
            // Player script bundles — never block
            "youtube.com/s/player/" in url -> true
            // Connectivity probe — needed for player init
            "youtube.com/generate_204" in url -> true
            // ytimg thumbnails / player assets
            "ytimg.com" in url -> true
            // Channel art
            "yt3.ggpht.com" in url -> true
            // YouTube API — allow most, block only pure ad-reporting calls
            "youtube.com/youtubei/v1/" in url -> {
                // These are used for ad impression reporting — block them
                val adPaths = listOf("/log_event", "/log_impression", "/log_latency")
                adPaths.none { path -> url.contains(path) }
            }
            // YouTube own scripts/assets
            "youtube.com/s/" in url -> true
            else -> false
        }
    }

    private fun resourceType(request: WebResourceRequest): String {
        if (request.isForMainFrame) return "document"
        val headers = request.requestHeaders
        val fetchDestination = headers.entries.firstOrNull { it.key.equals("Sec-Fetch-Dest", true) }
            ?.value?.lowercase().orEmpty()
        when (fetchDestination) {
            "document" -> return "document"
            "iframe", "frame" -> return "subdocument"
            "image" -> return "image"
            "style" -> return "stylesheet"
            "script" -> return "script"
            "font" -> return "font"
            "audio", "video", "track" -> return "media"
            "empty" -> return "xmlhttprequest"
        }
        val accept = headers.entries.firstOrNull { it.key.equals("Accept", true) }
            ?.value?.lowercase().orEmpty()
        val path = request.url.path?.lowercase().orEmpty()
        return when {
            "image/" in accept -> "image"
            "text/css" in accept || path.endsWith(".css") -> "stylesheet"
            "javascript" in accept || "ecmascript" in accept -> "script"
            "font/" in accept || listOf(".woff", ".woff2", ".ttf", ".otf").any { path.endsWith(it) } -> "font"
            "audio/" in accept || "video/" in accept || listOf(".m3u8", ".mpd", ".mp4", ".m4s", ".ts").any { path.endsWith(it) } -> "media"
            path.endsWith(".js") || path.endsWith(".mjs") -> "script"
            "text/html" in accept -> "subdocument"
            else -> "xmlhttprequest"
        }
    }

    private fun responseHeaders(connection: HttpURLConnection): Map<String, String> =
        connection.headerFields.entries.mapNotNull { (name, values) ->
            name?.takeUnless {
                it.equals("Content-Length", true) || it.equals("Content-Encoding", true) || it.equals("Transfer-Encoding", true)
            }?.let { it to values.joinToString(",") }
        }.toMap()

    /** Removes segments bracketed by HLS cue/SCTE-35 markers, or by a cue-out duration. */
    private fun cleanManifest(input: String): String {
        val output = mutableListOf<String>()
        var inAdBreak = false
        var remainingAdDuration: Double? = null
        var pendingSegmentDuration: Double? = null
        var preserveDiscontinuity = false

        for (rawLine in input.lineSequence()) {
            val line = rawLine.trim()
            val upper = line.uppercase()

            val daterangeAttributes = if (upper.startsWith("#EXT-X-DATERANGE:")) parseAttributes(line.substringAfter(':')) else emptyMap()
            val scteAttributes = if (upper.startsWith("#EXT-X-SCTE35:")) parseAttributes(tagValue(line)) else emptyMap()
            val scteEventType = scteAttributes["TYPE"]?.uppercase()
                ?: tagValue(line).substringBefore(',').trim().uppercase()
            val daterangeClass = daterangeAttributes["CLASS"].orEmpty().lowercase()
            val daterangeAd = daterangeAttributes.keys.any { it.equals("SCTE35-OUT", true) } ||
                daterangeClass.contains("ad-break") || daterangeClass.contains("interstitial")
            val scteOut = upper.startsWith("#EXT-OATCLS-SCTE35") ||
                upper.startsWith("#EXT-X-SCTE35-OUT") ||
                (upper.startsWith("#EXT-X-SCTE35:") && scteEventType == "OUT") ||
                daterangeAd
            val scteIn = upper.startsWith("#EXT-X-SCTE35-IN") ||
                (upper.startsWith("#EXT-X-SCTE35:") && scteEventType == "IN") ||
                daterangeAttributes.keys.any { it.equals("SCTE35-IN", true) }

            val isCueOut = upper.startsWith("#EXT-X-CUE-OUT") && !upper.startsWith("#EXT-X-CUE-OUT-CONT")
            val isCueOutContinuation = upper.startsWith("#EXT-X-CUE-OUT-CONT")
            if (isCueOut || isCueOutContinuation || scteOut) {
                inAdBreak = true
                remainingAdDuration = if (isCueOut || isCueOutContinuation) {
                    parseCueDuration(tagValue(line), isCueOutContinuation)
                } else {
                    (daterangeAttributes["DURATION"] ?: scteAttributes["DURATION"])?.toDoubleOrNull()
                }
                pendingSegmentDuration = null
                if (remainingAdDuration == 0.0) {
                    inAdBreak = false
                    remainingAdDuration = null
                }
                continue
            }
            if (upper.startsWith("#EXT-X-CUE-IN") || scteIn) {
                inAdBreak = false
                remainingAdDuration = null
                pendingSegmentDuration = null
                continue
            }

            if (upper.startsWith("#EXT-OATCLS-SCTE35") || upper.startsWith("#EXT-X-SCTE35")) continue
            if (upper.startsWith("#EXT-X-DATERANGE:") && (daterangeAd || scteIn)) continue

            if (inAdBreak) {
                if (upper.startsWith("#EXT-X-DISCONTINUITY")) preserveDiscontinuity = true
                if (upper.startsWith("#EXTINF:")) pendingSegmentDuration = parseDuration(tagValue(line))
                if (!line.startsWith("#")) {
                    val duration = pendingSegmentDuration
                    if (duration != null && remainingAdDuration != null) {
                        remainingAdDuration = (remainingAdDuration!! - duration).coerceAtLeast(0.0)
                        if (remainingAdDuration == 0.0) {
                            inAdBreak = false
                            remainingAdDuration = null
                        }
                    }
                    pendingSegmentDuration = null
                }
                continue
            }

            if (preserveDiscontinuity) {
                if (!upper.startsWith("#EXT-X-DISCONTINUITY")) output.add("#EXT-X-DISCONTINUITY")
                preserveDiscontinuity = false
            }
            output.add(rawLine)
        }
        if (preserveDiscontinuity && output.none { it.trim().equals("#EXT-X-DISCONTINUITY", true) }) {
            output.add("#EXT-X-DISCONTINUITY")
        }
        return output.joinToString("\n", postfix = "\n")
    }

    private fun tagValue(line: String): String = line.substringAfter(':', "").trim()

    private fun parseDuration(value: String): Double? {
        val first = value.substringBefore(',').trim().toDoubleOrNull()
        if (first != null) return first
        return parseAttributes(value)["DURATION"]?.toDoubleOrNull()
    }

    private fun parseCueDuration(value: String, continuation: Boolean): Double? {
        val attributes = parseAttributes(value)
        val duration = parseDuration(value) ?: attributes["DURATION"]?.toDoubleOrNull() ?: return null
        if (!continuation) return duration
        val elapsed = attributes["ELAPSEDTIME"]?.toDoubleOrNull() ?: 0.0
        return (duration - elapsed).coerceAtLeast(0.0)
    }

    /** Parses HLS attribute lists while respecting commas inside quoted values. */
    private fun parseAttributes(value: String): Map<String, String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        value.forEach { char ->
            when (char) {
                '"' -> { quoted = !quoted; current.append(char) }
                ',' -> if (quoted) current.append(char) else { fields.add(current.toString()); current.setLength(0) }
                else -> current.append(char)
            }
        }
        fields.add(current.toString())
        return fields.mapNotNull { field ->
            val separator = field.indexOf('=')
            if (separator < 0) null else {
                field.substring(0, separator).trim().uppercase() to
                    field.substring(separator + 1).trim().trim('"')
            }
        }.toMap()
    }

    private fun JSONArray?.toStringArray(): Array<String> =
        if (this == null) emptyArray() else Array(length()) { index -> optString(index) }
}
