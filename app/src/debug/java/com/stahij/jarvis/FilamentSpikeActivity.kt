package com.stahij.jarvis

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.Filament
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper

/**
 * Isolated Filament lifecycle spike. Debug variant only; the production launcher
 * remains untouched until this renderer passes device and thermal testing.
 */
class FilamentSpikeActivity : Activity() {
    companion object {
        init {
            Filament.init()
        }
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var uiHelper: UiHelper
    private lateinit var engine: Engine
    private lateinit var renderer: Renderer
    private lateinit var scene: Scene
    private lateinit var filamentView: com.google.android.filament.View
    private lateinit var camera: Camera
    private lateinit var skybox: Skybox
    private var swapChain: SwapChain? = null
    private var resumed = false

    private val choreographer by lazy { Choreographer.getInstance() }
    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        if (resumed && uiHelper.isReadyToRender) {
            val target = swapChain
            if (target != null && renderer.beginFrame(target, frameTimeNanos)) {
                renderer.render(filamentView)
                renderer.endFrame()
            }
            choreographer.postFrameCallback(frameCallback)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(3, 8, 20)
        window.navigationBarColor = Color.rgb(3, 8, 20)

        surfaceView = SurfaceView(this)
        setContentView(surfaceView)

        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        filamentView = engine.createView()
        camera = engine.createCamera(engine.entityManager.create())
        skybox = Skybox.Builder()
            .color(0.012f, 0.035f, 0.075f, 1.0f)
            .build(engine)

        camera.lookAt(
            0.0, 0.0, 3.0,
            0.0, 0.0, 0.0,
            0.0, 1.0, 0.0
        )
        scene.skybox = skybox
        filamentView.camera = camera
        filamentView.scene = scene

        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
            renderCallback = object : UiHelper.RendererCallback {
                override fun onNativeWindowChanged(surface: Surface) {
                    swapChain?.let { engine.destroySwapChain(it) }
                    swapChain = engine.createSwapChain(surface, swapChainFlags)
                }

                override fun onDetachedFromSurface() {
                    swapChain?.let {
                        engine.destroySwapChain(it)
                        engine.flushAndWait()
                    }
                    swapChain = null
                }

                override fun onResized(width: Int, height: Int) {
                    filamentView.viewport = Viewport(0, 0, width, height)
                }
            }
        }
        uiHelper.attachTo(surfaceView)
    }

    override fun onResume() {
        super.onResume()
        if (::uiHelper.isInitialized) {
            resumed = true
            choreographer.postFrameCallback(frameCallback)
        }
    }

    override fun onPause() {
        resumed = false
        if (::uiHelper.isInitialized) choreographer.removeFrameCallback(frameCallback)
        super.onPause()
    }

    override fun onDestroy() {
        resumed = false
        if (::uiHelper.isInitialized) uiHelper.detach()
        if (::engine.isInitialized) {
            engine.destroySkybox(skybox)
            engine.destroyView(filamentView)
            engine.destroyScene(scene)
            engine.destroyCameraComponent(camera.entity)
            engine.entityManager.destroy(camera.entity)
            engine.destroyRenderer(renderer)
            engine.destroy()
        }
        super.onDestroy()
    }
}
