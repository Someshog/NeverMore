package com.bravetv.app

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import android.graphics.Color
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

class TvWebViewActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var rootLayout: FrameLayout

    // Holds the custom view shown during fullscreen video playback (e.g. YouTube TV player)
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUi()

        val url = intent.getStringExtra(EXTRA_URL) ?: "https://www.youtube.com/tv"
        title = intent.getStringExtra(EXTRA_TITLE) ?: "NeverMore"

        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString = TV_USER_AGENT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            // setInitialScale(100)
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            // Full WebChromeClient so YouTube TV fullscreen video doesn't black-screen/crash
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    // Hide the WebView, show the fullscreen video surface
                    fullscreenCallback?.onCustomViewHidden()
                    fullscreenView = view
                    fullscreenCallback = callback
                    rootLayout.addView(
                        view,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    webView.visibility = View.GONE
                    hideSystemUi()
                }

                override fun onHideCustomView() {
                    fullscreenView?.let { rootLayout.removeView(it) }
                    fullscreenView = null
                    fullscreenCallback = null
                    webView.visibility = View.VISIBLE
                    webView.requestFocus() // otherwise the D-pad can land on nothing after fullscreen
                    hideSystemUi()
                }
            }

            webViewClient = AdBlockWebViewClient()
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
        }

        rootLayout.addView(webView)
        setContentView(rootLayout)
        BraveEngine.initializeBraveShields(applicationContext)

        // Inject the ad-pruning script BEFORE any YouTube script runs. evaluateJavascript in
        // onPageStarted is too late: the player has already requested/parsed its ad data by then.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            val adPruneJs = assets.open("yt_adblock.js").bufferedReader().use { it.readText() }
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                adPruneJs,
                setOf("https://www.youtube.com", "https://youtube.com", "https://m.youtube.com")
            )
        } else {
            android.util.Log.w("NeverMore", "DOCUMENT_START_SCRIPT unsupported on this WebView; update Android System WebView")
        }
        webView.loadUrl(url)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
        webView.requestFocus() // keep D-pad input going to the page after returning to the app
    }

    override fun onPause() {
        webView.pauseTimers()
        webView.onPause()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun hideSystemUi() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                runJs("{const v=document.querySelector('video');if(v){if(v.paused)v.play();else v.pause()}}")
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                // Act on key-up so that a long press can exit the app instead (see onKeyLongPress).
                event.startTracking()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.isTracking && !event.isCanceled) {
            handleBack()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /** Long-press Back always leaves the app, since a normal press is now handled by the page. */
    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    private fun handleBack() {
        // 1. A fullscreen video surface is showing: dismiss it first.
        if (fullscreenView != null) {
            (webView.webChromeClient as? WebChromeClient)?.onHideCustomView()
            return
        }
        // 2. YouTube TV is a single-page app that pushes a browser-history entry for almost every
        //    screen, so WebView.goBack() takes many presses to get anywhere. Send Back to the page
        //    instead and let YouTube navigate on its own, like a normal TV remote would.
        val host = android.net.Uri.parse(webView.url.orEmpty()).host.orEmpty()
        if (host == "youtube.com" || host.endsWith(".youtube.com")) {
            sendBackKeyToPage()
            return
        }
        // 3. Other sites behave like a normal browser.
        if (webView.canGoBack()) webView.goBack() else finish()
    }

    /** Dispatches a Back key press into the page (keydown + keyup). */
    private fun sendBackKeyToPage() {
        runJs(
            """
            (function(c){
              var t=document.activeElement||document.body;
              ['keydown','keyup'].forEach(function(type){
                var e=new KeyboardEvent(type,{key:'Escape',code:'Escape',bubbles:true,cancelable:true});
                Object.defineProperty(e,'keyCode',{get:function(){return c}});
                Object.defineProperty(e,'which',{get:function(){return c}});
                t.dispatchEvent(e);
              });
            })($BACK_KEY_CODE);
            """.trimIndent()
        )
    }

    private fun runJs(script: String) { webView.evaluateJavascript(script, null) }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TITLE = "service_title"
        const val EXTRA_URL = "service_url"

        /**
         * Key code sent to YouTube when Back is pressed. 27 is Escape, which YouTube TV accepts as
         * Back in browsers. With the Samsung/Tizen user agent below, some devices may expect the
         * Tizen Back code 10009 instead; if Back does nothing on your TV, try changing this to 10009.
         */
        const val BACK_KEY_CODE = 27
         const val TV_USER_AGENT = "Mozilla/5.0 (SMART-TV; Linux; Tizen 10.0) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/9.0 Chrome/130.0.0.0 TV Safari/537.36"
    }
}

