package com.stahij.jarvis.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.AttributeSet
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

@SuppressLint("SetJavaScriptEnabled")
class JarvisCoreView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {
    private val webView = WebView(context)
    val bridge = JarvisCoreBridge(webView)
    private var destroyed = false

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setWillNotDraw(true)
        webView.apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    bridge.onPageReady()
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setRendererPriorityPolicy(RENDERER_PRIORITY_BOUND, true)
        }
        addView(webView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        webView.loadUrl("file:///android_asset/jarvis_core/index.html")
    }

    fun onResume() { if (!destroyed) { webView.onResume(); bridge.resume() } }
    fun onPause() { if (!destroyed) { bridge.pause(); webView.onPause() } }

    fun onDestroy() {
        if (destroyed) return
        destroyed = true
        bridge.dispose {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            removeView(webView)
            webView.destroy()
        }
    }
}