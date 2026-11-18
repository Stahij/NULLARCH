package com.stahij.jarvis

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
    private lateinit var workspaceRoot: FrameLayout
    private var developerOverlay: View? = null
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
            onAppsChanged = { apps -> coreView.bridge.setNodes(apps) },
            onOpenDeveloperPanel = { showDeveloperPanel() }
        )
        coreView.bridge.setNodes(workspace.getCoreApps())
        applySavedDeveloperSettings()
        coreView.bridge.setRotationEnabled(
            getSharedPreferences("jarvis_launcher_gestures", MODE_PRIVATE).getBoolean("core_rotate", true)
        )
        workspaceRoot = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(3, 8, 20))
            addView(coreView, FrameLayout.LayoutParams(-1, -1))
            addView(workspace, FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(workspaceRoot)
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


    private fun applySavedDeveloperSettings() {
        val p = getSharedPreferences("jarvis_developer_visuals", MODE_PRIVATE)
        coreView.bridge.setDeveloperSettings(
            p.getInt("core_scale", 100) / 100f, p.getInt("icon_scale", 100) / 100f,
            p.getInt("orbit_scale", 100) / 100f, p.getInt("orbit_speed", 100) / 100f,
            p.getInt("intensity", 100) / 100f, p.getInt("hue", 0).toFloat()
        )
    }

    private fun showDeveloperPanel() {
        if (developerOverlay != null) return
        val prefs = getSharedPreferences("jarvis_developer_visuals", MODE_PRIVATE)
        val overlay = FrameLayout(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(246, 5, 15, 31), Color.argb(238, 8, 24, 43))
            ).apply {
                cornerRadii = floatArrayOf(dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), 0f, 0f, 0f, 0f)
                setStroke(dp(1), Color.argb(180, 53, 166, 205))
            }
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "JARVIS  /  DEVELOPER HUD"
            textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cyan)
        }, LinearLayout.LayoutParams(0, dp(36), 1f))
        val collapse = TextView(this).apply {
            text = "Свернуть  ×"; textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(4), 0)
            setOnClickListener { workspaceRoot.removeView(overlay); developerOverlay = null }
        }
        header.addView(collapse)
        panel.addView(header)

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = android.widget.ScrollView(this).apply { isFillViewport = true; addView(content) }
        panel.addView(tabs)
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        fun applySettings() {
            coreView.bridge.setDeveloperSettings(
                prefs.getInt("core_scale", 100) / 100f,
                prefs.getInt("icon_scale", 100) / 100f,
                prefs.getInt("orbit_scale", 100) / 100f,
                prefs.getInt("orbit_speed", 100) / 100f,
                prefs.getInt("intensity", 100) / 100f,
                prefs.getInt("palette_hue", 190).toFloat(),
                prefs.getInt("glow", 100) / 100f,
                prefs.getInt("pulse", 100) / 100f,
                prefs.getInt("particles", 100) / 100f
            )
        }
        fun addSlider(title: String, key: String, min: Int, max: Int, default: Int, suffix: String = "") {
            val label = TextView(this).apply { textSize = 13f; setTextColor(Color.WHITE); setPadding(0, dp(5), 0, 0) }
            content.addView(label)
            val seek = android.widget.SeekBar(this).apply {
                if (key == "palette_hue") progressDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED)).apply { cornerRadius = dp(5).toFloat() }
                this.max = max - min
                progress = (prefs.getInt(key, default) - min).coerceIn(0, this.max)
            }
            fun show(v: Int, save: Boolean) {
                label.text = "$title: $v$suffix"
                if (save) prefs.edit().putInt(key, v).apply()
                applySettings()
            }
            show(seek.progress + min, false)
            seek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) = show(progress + min, fromUser)
                override fun onStartTrackingTouch(bar: android.widget.SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: android.widget.SeekBar?) = Unit
            })
            content.addView(seek)
        }
        fun showTab(tab: String) {
            content.removeAllViews()
            when (tab) {
                "Ядро" -> {
                    addSlider("Размер ядра", "core_scale", 70, 140, 100, "%")
                    addSlider("Пульсация / активность", "intensity", 40, 180, 100, "%")
                }
                "Иконки" -> addSlider("Размер иконок", "icon_scale", 60, 190, 100, "%")
                "Орбиты" -> {
                    addSlider("Радиус орбит", "orbit_scale", 70, 145, 100, "%")
                    addSlider("Скорость вращения", "orbit_speed", 0, 250, 100, "%")
                }
                "Эффекты" -> {
                    addSlider("Сила свечения", "glow", 0, 200, 100, "%")
                    addSlider("Пульсация", "pulse", 0, 200, 100, "%")
                    addSlider("Плотность частиц", "particles", 0, 150, 100, "%")
                    addSlider("Цветовая палитра", "palette_hue", 0, 360, 190, "")
                    content.addView(TextView(this).apply {
                        text = "Ползунок цветового спектра меняет общую палитру сцены. Белые энергетические блики сохраняются."
                        textSize = 11f; setTextColor(Color.LTGRAY); setPadding(0, dp(5), 0, dp(8))
                    })
                    val save = TextView(this).apply {
                        text = "Сохранить текущий профиль"; textSize = 13f; setTextColor(cyan)
                        setPadding(0, dp(10), 0, dp(10))
                        setOnClickListener {
                            prefs.edit().putBoolean("profile_saved", true).apply()
                            Toast.makeText(this@LauncherActivity, "Профиль визуала сохранён", Toast.LENGTH_SHORT).show()
                        }
                    }
                    content.addView(save)
                }
            }
        }
        listOf("Ядро", "Иконки", "Орбиты", "Эффекты").forEach { tab ->
            val item = TextView(this).apply {
                text = tab; textSize = 12f; gravity = Gravity.CENTER
                setTextColor(if (tab == "Ядро") cyan else Color.LTGRAY)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener {
                    for (i in 0 until tabs.childCount) (tabs.getChildAt(i) as? TextView)?.setTextColor(Color.LTGRAY)
                    setTextColor(cyan)
                    showTab(tab)
                }
            }
            tabs.addView(item, LinearLayout.LayoutParams(0, dp(38), 1f))
        }
        showTab("Ядро")

        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        val reset = TextView(this).apply {
            text = "Сброс"; textSize = 12f; setTextColor(cyan); setPadding(dp(12), dp(8), dp(12), dp(6))
            setOnClickListener {
                prefs.edit().clear().apply()
                applySettings()
                showTab("Ядро")
                Toast.makeText(this@LauncherActivity, "Параметры сброшены", Toast.LENGTH_SHORT).show()
            }
        }
        val save = TextView(this).apply {
            text = "Сохранить"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            setPadding(dp(12), dp(8), dp(4), dp(6))
            setOnClickListener {
                prefs.edit().putBoolean("profile_saved", true).apply()
                Toast.makeText(this@LauncherActivity, "Настройки сохранены", Toast.LENGTH_SHORT).show()
            }
        }
        footer.addView(reset); footer.addView(save); panel.addView(footer)
        overlay.addView(panel, FrameLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.42f).toInt(), Gravity.BOTTOM))
        workspaceRoot.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        developerOverlay = overlay
    }

    private fun showCoreMenu() {
        val options = arrayOf("Plasma Core", "Жесты рабочего стола", "Настройки и инструменты")
        AlertDialog.Builder(this)
            .setTitle("Визуальное ядро Jarvis")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (::coreView.isInitialized) coreView.bridge.setTheme(selectedCore)
                    }
                    1 -> showGestureSettings()
                    2 -> startActivity(Intent(this, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_TOOLS_ONLY, true)
                    })
                }
            }
            .show()
    }

    private fun showGestureSettings() {
        val prefs = getSharedPreferences("jarvis_launcher_gestures", MODE_PRIVATE)
        val keys = arrayOf("page_swipe", "drawer_swipe", "core_double_tap", "core_long_press", "core_tap", "core_rotate")
        val labels = arrayOf(
            "Смена рабочих столов — свайп в нижней полосе",
            "Каталог приложений — свайп вверх из левого нижнего угла",
            "Открыть чат Jarvis — двойное касание ядра",
            "Настройки ядра — долгое нажатие на ядро",
            "Остановить / возобновить анимацию — одиночное касание ядра",
            "Вращать ядро — перетаскивание по центральной области"
        )
        val checked = BooleanArray(keys.size) { i -> prefs.getBoolean(keys[i], true) }
        AlertDialog.Builder(this)
            .setTitle("Жесты рабочего стола")
            .setMultiChoiceItems(labels, checked) { _, which, enabled ->
                checked[which] = enabled
            }
            .setPositiveButton("Сохранить") { _, _ ->
                prefs.edit().apply {
                    keys.forEachIndexed { index, key -> putBoolean(key, checked[index]) }
                }.apply()
                workspace.reloadGestureSettings()
                coreView.bridge.setRotationEnabled(prefs.getBoolean("core_rotate", true))
                Toast.makeText(this, "Жесты обновлены", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Сбросить") { _, _ ->
                prefs.edit().clear().apply()
                workspace.reloadGestureSettings()
                coreView.bridge.setRotationEnabled(true)
                Toast.makeText(this, "Восстановлены жесты по умолчанию", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .setMessage("Верхний свайп от края экрана, шторка уведомлений, быстрые настройки и системный экран последних приложений остаются за Android/HyperOS — Jarvis не перехватывает эти системные зоны.")
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
