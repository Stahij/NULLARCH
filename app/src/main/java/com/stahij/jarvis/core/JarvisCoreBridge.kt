package com.stahij.jarvis.core

import android.webkit.WebView

class JarvisCoreBridge(private val webView: WebView) {
    private val pending = ArrayDeque<String>()
    private var ready = false
    private var disposed = false

    fun onPageReady() {
        webView.post {
            if (disposed) return@post
            ready = true
            while (pending.isNotEmpty()) evaluateNow(pending.removeFirst())
        }
    }

    fun setTheme(theme: CoreTheme) = evaluate("JarvisCoreAPI.setTheme('${theme.value}')")
    fun setQuality(quality: CoreQuality) = evaluate("JarvisCoreAPI.setQuality('${quality.value}')")
    fun setVisualSettings(size: Float, brightness: Float, intensity: Float, hue: Float, glow: Float) = evaluate("JarvisCoreAPI.setVisualSettings($size,$brightness,$intensity,$hue,$glow)")
    fun setAssistantState(state: AssistantState) = evaluate("JarvisCoreAPI.setState('${state.value}')")
    fun pause() = evaluate("JarvisCoreAPI.pause()")
    fun resume() = evaluate("JarvisCoreAPI.resume()")

    fun dispose(onComplete: (() -> Unit)? = null) {
        webView.post {
            if (disposed) { onComplete?.invoke(); return@post }
            disposed = true
            ready = false
            pending.clear()
            if (webView.isAttachedToWindow) {
                webView.evaluateJavascript("window.JarvisCoreAPI && window.JarvisCoreAPI.dispose()") { onComplete?.invoke() }
            } else onComplete?.invoke()
        }
    }

    private fun evaluate(script: String) {
        webView.post {
            if (disposed) return@post
            if (!ready) pending.addLast(script) else evaluateNow(script)
        }
    }

    private fun evaluateNow(script: String) { if (!disposed) webView.evaluateJavascript(script, null) }

    enum class CoreTheme(val value: String) { ATOMIC("atomic"), PLASMA("plasma") }
    enum class CoreQuality(val value: String) { AUTO("auto"), HIGH("high"), MEDIUM("medium"), LOW("low") }
    enum class AssistantState(val value: String) { IDLE("idle"), LISTENING("listening"), PROCESSING("processing"), SPEAKING("speaking") }
}