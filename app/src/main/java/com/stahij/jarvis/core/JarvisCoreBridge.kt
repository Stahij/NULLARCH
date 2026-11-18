package com.stahij.jarvis.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.stahij.jarvis.LauncherApp
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.PI

class JarvisCoreBridge(
    private val webView: WebView,
    private val nodeClickHandler: (String) -> Unit
) {
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

    @JavascriptInterface
    fun onNodeClick(id: String) {
        webView.post {
            if (!disposed) nodeClickHandler.invoke(id)
        }
    }

    fun setTheme(theme: CoreTheme) = evaluate("JarvisCoreAPI.setTheme('${theme.value}')")
    fun setQuality(quality: CoreQuality) = evaluate("JarvisCoreAPI.setQuality('${quality.value}')")
    fun setRotationEnabled(enabled: Boolean) = evaluate("JarvisCoreAPI.setRotationEnabled($enabled)")
    fun setAssistantState(state: AssistantState) = evaluate("JarvisCoreAPI.setState('${state.value}')")
    fun setDeveloperSettings(core: Float, icon: Float, orbit: Float, speed: Float, intensity: Float, hue: Float, glow: Float, pulse: Float, particles: Float) =
        evaluate("JarvisCoreAPI.setDevSettings($core,$icon,$orbit,$speed,$intensity,$hue,$glow,$pulse,$particles)")

    fun setNodes(apps: List<LauncherApp>) {
        val array = JSONArray()
        apps.take(16).forEachIndexed { index, app ->
            val seed = app.packageName.hashCode().toLong().and(0xffffffffL)
            val plane = (seed % 4L).toInt()
            val tilts = when (plane) {
                0 -> 0.12 to 0.12
                1 -> 0.72 to 0.08
                2 -> 0.42 to 0.62
                else -> -0.42 to -0.62
            }
            array.put(JSONObject().apply {
                put("id", app.packageName + "/" + app.className)
                put("label", app.label)
                put("radius", 1.28 + (index % 3) * 0.09)
                put("angle", index.toDouble() / maxOf(1, apps.size) * PI * 2.0)
                put("speed", 0.12 + (index % 5) * 0.018)
                put("tiltX", tilts.first)
                put("tiltZ", tilts.second)
                put("glyphIndex", index % 8)
                put("iconData", encodeIcon(app))
            })
        }
        evaluate("JarvisCoreAPI.setNodes($array)")
    }

    private fun encodeIcon(app: LauncherApp): String {
        return try {
            val size = 96
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val drawable = app.icon
            val old = drawable.bounds
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            drawable.bounds = old
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            "data:image/png;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }

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

    private fun evaluateNow(script: String) {
        if (!disposed) webView.evaluateJavascript(script, null)
    }

    enum class CoreTheme(val value: String) { PLASMA("plasma") }
    enum class CoreQuality(val value: String) { AUTO("auto"), HIGH("high"), MEDIUM("medium"), LOW("low") }
    enum class AssistantState(val value: String) { IDLE("idle"), LISTENING("listening"), PROCESSING("processing"), SPEAKING("speaking") }
}