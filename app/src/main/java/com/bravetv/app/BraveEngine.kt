package com.bravetv.app

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

/** Kotlin facade for Brave's adblock-rust network and cosmetic filtering engine. */
object BraveEngine {
    private const val EASYLIST_URL = "https://easylist.to/easylist/easylist.txt"
    private const val UBLOCK_FILTERS_URL = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt"
    private const val BRAVE_FILTERS_URL = "https://raw.githubusercontent.com/brave/adblock-lists/master/brave-lists/brave-specific.txt"
    private const val BRAVE_UNBREAK_URL = "https://raw.githubusercontent.com/brave/adblock-lists/master/brave-unbreak.txt"
    private const val BRAVE_RESOURCES_URL = "https://raw.githubusercontent.com/brave/adblock-resources/master/dist/resources.json"
    private const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L
    private const val MAX_DOWNLOAD_BYTES = 32 * 1024 * 1024

    private const val TAG = "BraveEngine"

    private val nativeAvailable = runCatching {
        System.loadLibrary("adblock_jni")
    }.also { result ->
        Log.i(TAG, "Native library load: success=${result.isSuccess}, error=${result.exceptionOrNull()?.message}")
    }.isSuccess
    private val ioExecutor = Executors.newFixedThreadPool(6) { runnable ->
        Thread(runnable, "BraveShieldsInit").apply { isDaemon = true }
    }
    private val initializedLatch = CountDownLatch(1)
    private val initializationCallbacks = mutableListOf<(Long) -> Unit>()

    @Volatile private var engineHandle: Long = 0L
    @Volatile private var initializationStarted = false
    @Volatile private var initializationFinished = false

    /**
     * Loads fresh cached or downloaded EasyList and Brave's scriptlet/redirect resources.
     * Call this at app startup. It loads data and builds the engine off the UI thread.
     */
    @Synchronized
    fun initializeBraveShields(context: Context) {
        startInitialization(context.applicationContext)
    }

    fun initializeBraveShields(context: Context, onInitialized: (Long) -> Unit) {
        var completed = false
        var completedHandle = 0L
        synchronized(this) {
            if (initializationFinished) {
                completed = true
                completedHandle = engineHandle
            } else {
                initializationCallbacks.add(onInitialized)
                startInitialization(context.applicationContext)
                if (initializationFinished) {
                    initializationCallbacks.remove(onInitialized)
                    completed = true
                    completedHandle = engineHandle
                }
            }
        }
        if (completed) onInitialized(completedHandle)
    }

    private fun startInitialization(context: Context) {
        if (initializationStarted) {
            Log.d(TAG, "startInitialization: already started, skipping")
            return
        }
        initializationStarted = true
        Log.i(TAG, "startInitialization: nativeAvailable=$nativeAvailable")
        if (!nativeAvailable) {
            Log.e(TAG, "Native library NOT available — ad blocking disabled!")
            initializationFinished = true
            initializedLatch.countDown()
            return
        }
        ioExecutor.execute {
            val easyList = ioExecutor.submit<String?> {
                readCachedOrFetch(context, "easylist.txt", EASYLIST_URL, "easylist.txt")
            }
            val ublock = ioExecutor.submit<String?> {
                readCachedOrFetch(context, "ublock-filters.txt", UBLOCK_FILTERS_URL, "ublock-filters.txt")
            }
            val braveSpecific = ioExecutor.submit<String?> {
                readCachedOrFetch(context, "brave-specific.txt", BRAVE_FILTERS_URL, "brave-specific.txt")
            }
            val braveUnbreak = ioExecutor.submit<String?> {
                readCachedOrFetch(context, "brave-unbreak.txt", BRAVE_UNBREAK_URL, "brave-unbreak.txt")
            }
            val resources = ioExecutor.submit<String?> {
                readCachedOrFetch(context, "resources.json", BRAVE_RESOURCES_URL, "resources.json")
            }
            val filterTexts = listOf(easyList, ublock, braveSpecific, braveUnbreak).map { it.safeGet() }
            val nonNullCount = filterTexts.count { !it.isNullOrBlank() }
            Log.i(TAG, "Filter lists fetched: $nonNullCount/4 non-empty")
            filterTexts.forEachIndexed { i, text ->
                Log.d(TAG, "  list[$i]: ${if (text.isNullOrBlank()) "EMPTY/NULL" else "${text.length} chars"}")
            }
            val resourcesJson = resources.safeGet().orEmpty()
            Log.d(TAG, "Resources JSON: ${if (resourcesJson.isBlank()) "EMPTY" else "${resourcesJson.length} chars"}")
            val encoded = encodeFilterLists(filterTexts)
            val handle = runCatching {
                createEngine(encoded, resourcesJson)
            }.also { result ->
                Log.i(TAG, "createEngine result: success=${result.isSuccess}, handle=${result.getOrNull()}, error=${result.exceptionOrNull()?.message}")
            }.getOrDefault(0L)
            engineHandle = handle
            Log.i(TAG, "Engine initialized: handle=$handle (${if (handle != 0L) "ACTIVE" else "FAILED"})")
            val callbacks: List<(Long) -> Unit>
            synchronized(this) {
                initializationFinished = true
                callbacks = initializationCallbacks.toList()
                initializationCallbacks.clear()
                initializedLatch.countDown()
            }
            callbacks.forEach { callback -> runCatching { callback(handle) } }
        }
    }

    private fun <T> Future<T>.safeGet(): T? = runCatching { get(20, TimeUnit.SECONDS) }.getOrNull()

    private fun encodeFilterLists(lists: List<String?>): String {
        val encoded = JSONArray()
        lists.filterNot { it.isNullOrBlank() }.forEach { text ->
            // Remote lists are not verified Brave component payloads, so keep privileged
            // scriptlet permission bits disabled even when they originate upstream.
            encoded.put(JSONObject().put("filter_rules", text).put("permission_mask", 0))
        }
        return encoded.toString()
    }

    private fun readCachedOrFetch(context: Context, name: String, url: String, assetName: String): String? {
        val directory = File(context.filesDir, "adblock-lists")
        val cache = File(directory, name)
        val cached = runCatching { if (cache.isFile) cache.readText(Charsets.UTF_8) else null }.getOrNull()
        if (!cached.isNullOrBlank() && System.currentTimeMillis() - cache.lastModified() < CACHE_TTL_MS) {
            return cached
        }

        val fetched = downloadText(url)
        if (!fetched.isNullOrBlank()) {
            runCatching {
                directory.mkdirs()
                val temporary = File(directory, "$name.tmp")
                temporary.writeText(fetched, Charsets.UTF_8)
                if (!temporary.renameTo(cache)) {
                    cache.writeText(fetched, Charsets.UTF_8)
                    temporary.delete()
                }
            }
            return fetched
        }
        if (!cached.isNullOrBlank()) return cached
        return runCatching {
            context.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()
    }

    private fun downloadText(url: String): String? {
        val connection = runCatching { URL(url).openConnection() as HttpURLConnection }.getOrNull() ?: return null
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "text/plain, application/json")
            connection.connect()
            if (connection.responseCode !in 200..299) return null
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (bytes.size() + count > MAX_DOWNLOAD_BYTES) return null
                    bytes.write(buffer, 0, count)
                }
            }
            bytes.toString(Charsets.UTF_8.name())
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun ensureInitialized(): Long {
        if (engineHandle != 0L) return engineHandle
        synchronized(this) {
            if (!initializationStarted && engineHandle == 0L && nativeAvailable) {
                initializationStarted = true
                engineHandle = runCatching { createEngine("", "") }.getOrDefault(0L)
                initializationFinished = true
                initializedLatch.countDown()
            }
        }
        if (engineHandle == 0L && initializationStarted) {
            runCatching { initializedLatch.await(100, TimeUnit.MILLISECONDS) }
        }
        return engineHandle
    }

    @JvmStatic external fun createEngine(rules: String, resourcesJson: String): Long
    @JvmStatic external fun shouldBlock(
        enginePtr: Long,
        url: String,
        sourceUrl: String,
        resourceType: String
    ): Boolean
    @JvmStatic external fun getCosmeticResources(enginePtr: Long, url: String): String
    @JvmStatic external fun getGenericCosmeticSelectors(
        enginePtr: Long,
        url: String,
        classes: Array<String>,
        ids: Array<String>
    ): String

    fun shouldBlockUrl(url: String, sourceUrl: String, resourceType: String): Boolean {
        val handle = ensureInitialized()
        if (handle == 0L) {
            Log.w(TAG, "shouldBlockUrl: engine not initialized (handle=0), allowing: $url")
            return false
        }
        val blocked = runCatching {
            shouldBlock(handle, url, sourceUrl, resourceType)
        }.getOrDefault(false)
        if (blocked) Log.d(TAG, "BLOCKED: $url (type=$resourceType)")
        return blocked
    }

    fun getCosmeticResources(url: String): String? {
        val handle = ensureInitialized()
        return if (handle == 0L) null else runCatching {
            getCosmeticResources(handle, url)
        }.getOrNull()
    }

    fun getGenericCosmeticSelectors(url: String, classes: Array<String>, ids: Array<String>): String? {
        val handle = ensureInitialized()
        return if (handle == 0L) null else runCatching {
            getGenericCosmeticSelectors(handle, url, classes, ids)
        }.getOrNull()
    }
}
