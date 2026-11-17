package com.stahij.jarvis

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.stahij.jarvis.core.JarvisCoreBridge
import com.stahij.jarvis.core.JarvisCoreView
import java.util.Locale

class LauncherActivity : Activity() {
    private lateinit var workspace: AtomWorkspaceView
    private lateinit var coreView: JarvisCoreView
    private val cyan = Color.rgb(99, 224, 255)
    private val bg = Color.rgb(3, 8, 20)
    private var installedApps: List<LauncherApp> = emptyList()
    private val selectedCore = JarvisCoreBridge.CoreTheme.PLASMA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.navigationBarDividerColor = Color.TRANSPARENT
        loadInstalledApps()
        buildWorkspace()
    }

    override fun onResume() {
        super.onResume()
        if (::coreView.isInitialized) coreView.onResume()
        if (::workspace.isInitialized) workspace.resumeMotion()
    }

    override fun onPause() {
        if (::workspace.isInitialized) workspace.pauseMotion()
        if (::coreView.isInitialized) coreView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::coreView.isInitialized) coreView.onDestroy()
        super.onDestroy()
    }

    private fun loadInstalledApps() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = packageManager.queryIntentActivities(launcher, 0)
        installedApps = resolved
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName + "/" + it.activityInfo.name }
            .map { info ->
                LauncherApp(
                    label = info.loadLabel(packageManager).toString(),
                    packageName = info.activityInfo.packageName,
                    className = info.activityInfo.name,
                    icon = info.loadIcon(packageManager)
                )
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun buildWorkspace() {
        coreView = JarvisCoreView(this, onNodeClick = { id ->
            installedApps.firstOrNull { it.packageName + "/" + it.className == id }?.let { launchApp(it) }
        }).apply {
            bridge.setTheme(selectedCore)
            bridge.setQuality(JarvisCoreBridge.CoreQuality.AUTO)
            bridge.setAssistantState(JarvisCoreBridge.AssistantState.IDLE)
        }
        workspace = AtomWorkspaceView(
            this,
            installedApps,
            onOpenChat = { startActivity(Intent(this, ChatHubActivity::class.java)) },
            onOpenSettings = { showCoreMenu() },
            onOpenDrawer = { showAppDrawer() },
            onForwardTouch = { event -> coreView.dispatchPointerEvent(event) },
            onAppsChanged = { apps -> coreView.bridge.setNodes(apps) }
        )
        coreView.bridge.setNodes(workspace.getCoreApps())
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(3, 8, 20))
            addView(coreView, FrameLayout.LayoutParams(-1, -1))
            addView(workspace, FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(root)
    }

    private fun launchApp(app: LauncherApp) {
        try {
            startActivity(Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(app.packageName, app.className))
        } catch (_: Exception) {
            Toast.makeText(this, "Не удалось открыть ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCoreMenu() {
        val options = arrayOf("Plasma Core", "Настройки и инструменты")
        AlertDialog.Builder(this)
            .setTitle("Визуальное ядро Jarvis")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (::coreView.isInitialized) coreView.bridge.setTheme(selectedCore)
                    }
                    1 -> startActivity(Intent(this, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_TOOLS_ONLY, true)
                    })
                }
            }
            .show()
    }

    private fun showAppDrawer() {
        val dialog = android.app.Dialog(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(18))
            setBackground(GradientDrawable().apply {
                setColor(Color.rgb(8, 17, 31))
                cornerRadii = floatArrayOf(dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(), 0f, 0f, 0f, 0f)
                setStroke(dp(1), Color.rgb(28, 73, 99))
            })
        }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(TextView(this).apply {
            text = "Приложения"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        val close = TextView(this).apply {
            text = "✕"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(cyan)
            setOnClickListener { dialog.dismiss() }
        }
        val help = TextView(this).apply {
            text = "?"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(cyan)
            setOnClickListener { showControlsGuide() }
        }
        heading.addView(help, LinearLayout.LayoutParams(dp(42), dp(48)))
        heading.addView(close, LinearLayout.LayoutParams(dp(42), dp(48)))
        panel.addView(heading)

        val search = EditText(this).apply {
            hint = "Поиск приложений"
            setSingleLine(true)
            textSize = 15f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(125, 151, 170))
            setPadding(dp(14), 0, dp(14), 0)
            background = GradientDrawable().apply {
                setColor(Color.rgb(18, 34, 51))
                cornerRadius = dp(15).toFloat()
            }
        }
        panel.addView(search, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10); bottomMargin = dp(14) })

        val grid = android.widget.GridLayout(this).apply { columnCount = 4; useDefaultMargins = false }
        val scroll = android.widget.ScrollView(this).apply { isFillViewport = true }
        scroll.addView(grid)
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        fun render(query: String) {
            grid.removeAllViews()
            installedApps.filter {
                it.label.contains(query, true) || it.packageName.contains(query, true)
            }.forEach { app ->
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(4), dp(10), dp(4), dp(10))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        val added = workspace.addApp(app)
                        Toast.makeText(
                            this@LauncherActivity,
                            if (added) "${app.label} добавлено на рабочий стол" else "Приложение уже добавлено",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (added) dialog.dismiss()
                    }
                }
                val icon = android.widget.ImageView(this).apply {
                    setImageDrawable(app.icon)
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                }
                tile.addView(icon, LinearLayout.LayoutParams(dp(46), dp(46)))
                tile.addView(TextView(this).apply {
                    text = app.label
                    textSize = 10f
                    maxLines = 2
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(224, 237, 246))
                    setPadding(0, dp(6), 0, 0)
                }, LinearLayout.LayoutParams(-1, dp(34)))
                grid.addView(tile, android.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(92)
                    columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                })
            }
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = render(s?.toString().orEmpty())
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        render("")
        dialog.setContentView(panel)
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setLayout(-1, -1)
                setGravity(Gravity.BOTTOM)
                addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                attributes = attributes.apply { dimAmount = .68f }
            }
        }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(-1, -1)
            setGravity(Gravity.BOTTOM)
            attributes = attributes.apply { dimAmount = .68f }
        }
    }

    private fun showControlsGuide() {
        AlertDialog.Builder(this)
            .setTitle("Управление Jarvis Launcher")
            .setMessage(
                "ЯДРО\n" +
                "• Одинарное нажатие — вернуть приложения к сохранённым позициям / возобновить движение\n" +
                "• Двойное нажатие — открыть чат с Jarvis\n" +
                "• Долгое нажатие — настройки ядра\n\n" +
                "РАБОЧИЙ СТОЛ\n" +
                "• Свайп вверх — каталог приложений\n" +
                "• Свайп влево или вправо — смена страницы\n" +
                "• Нажатие на значок — запустить приложение\n" +
                "• Перетаскивание значка — изменить его положение\n\n" +
                "Системные жесты Android остаются за самой системой."
            )
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
