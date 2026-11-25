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
        coreView = JarvisCoreView(this).apply {
            bridge.setTheme(selectedCore)
            bridge.setQuality(JarvisCoreBridge.CoreQuality.AUTO)
            bridge.setAssistantState(JarvisCoreBridge.AssistantState.IDLE)
        }
        workspace = AtomWorkspaceView(
            this,
            installedApps,
            onOpenChat = { startActivity(Intent(this, ChatHubActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            onOpenSettings = { showCoreMenu() },
            onOpenDrawer = { showAppDrawer() },
            onOpenLab = { showCoreLab() },
            onAppOptions = { showAppOptions(it) },
            onRotateCore = { dx, dy -> coreView.bridge.rotateCore(dx, dy) }
        )
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(3, 8, 20))
            addView(coreView, FrameLayout.LayoutParams(-1, -1))
            addView(workspace, FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(root)
    }

    private fun showCoreLab() {
        val dialog = android.app.Dialog(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.rgb(7, 17, 32))
                cornerRadii = floatArrayOf(dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), 0f, 0f, 0f, 0f)
                setStroke(dp(1), Color.rgb(35, 105, 139))
            }
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "CORE LAB"; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, dp(34), 1f))
        header.addView(TextView(this).apply {
            text = "ГОТОВО"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cyan); gravity = Gravity.CENTER
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(72), dp(34)))
        panel.addView(header)

        val content = android.widget.ScrollView(this)
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(controls)
        panel.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        val labels = listOf("Размер ядра", "Яркость ядра", "Интенсивность ядра", "Размер иконок", "Масштаб орбит", "Свечение")
        val defaults = intArrayOf(500, 700, 700, 500, 500, 650)
        val bars = mutableListOf<android.widget.SeekBar>()
        var selectedHue = 0.56f
        var selectedSaturation = 0.9f
        fun apply() {
            coreView.bridge.setVisualSettings(
                .65f + bars[0].progress / 1000f * .75f,
                .25f + bars[1].progress / 1000f * 1.25f,
                .25f + bars[2].progress / 1000f * 1.45f,
                selectedHue,
                .25f + bars[5].progress / 1000f * 1.5f,
                selectedSaturation
            )
            workspace.setLabIconScale(.65f + bars[3].progress / 1000f * .85f)
            workspace.setLabOrbitScale(.65f + bars[4].progress / 1000f * .8f)
        }
        labels.forEachIndexed { index, label ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply { text = label; textSize = 12f; setTextColor(Color.rgb(210, 228, 241)) },
                LinearLayout.LayoutParams(0, dp(28), 1f))
            val value = TextView(this).apply { text = "${defaults[index] / 10}%"; textSize = 11f; setTextColor(cyan) }
            row.addView(value)
            controls.addView(row)
            val bar = android.widget.SeekBar(this).apply { max = 1000; progress = defaults[index] }
            bar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    value.text = "${progress / 10}%"; apply()
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            })
            bars.add(bar)
            controls.addView(bar, LinearLayout.LayoutParams(-1, dp(34)))
        }

        controls.addView(TextView(this).apply {
            text = "ЦВЕТ И ОТТЕНОК"; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(170, 205, 225)); setPadding(0, dp(5), 0, dp(5))
        })
        val palette = object : View(this) {
            private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val rows = 4; val cols = 48
                val cellW = width.toFloat() / cols; val cellH = height.toFloat() / rows
                for (y in 0 until rows) for (x in 0 until cols) {
                    val hue = x.toFloat() / (cols - 1) * 360f
                    val sat = 1f - y * .22f; val value = 1f - y * .12f
                    p.color = Color.HSVToColor(floatArrayOf(hue, sat, value))
                    canvas.drawRect(x * cellW, y * cellH, (x + 1) * cellW + 1f, (y + 1) * cellH + 1f, p)
                }
                val markerX = selectedHue * width
                val markerY = (1f - (selectedSaturation - .15f) / .85f) * height
                p.color = Color.WHITE; p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = dp(2).toFloat()
                canvas.drawCircle(markerX, markerY, dp(7).toFloat(), p)
                p.style = android.graphics.Paint.Style.FILL
            }
            override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
                if (event.action == android.view.MotionEvent.ACTION_DOWN || event.action == android.view.MotionEvent.ACTION_MOVE) {
                    selectedHue = (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    selectedSaturation = (1f - event.y / height.coerceAtLeast(1) * .85f).coerceIn(.15f, 1f)
                    invalidate(); apply(); return true
                }
                return true
            }
        }
        controls.addView(palette, LinearLayout.LayoutParams(-1, dp(76)))
        controls.addView(TextView(this).apply {
            text = "Цвет — по горизонтали; оттенки — по вертикали"
            textSize = 10f; setTextColor(Color.rgb(132, 160, 181))
        })
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        footer.addView(TextView(this).apply {
            text = "СБРОСИТЬ"; textSize = 11f; setTextColor(Color.rgb(150, 190, 210))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener {
                bars.forEachIndexed { i, bar -> bar.progress = defaults[i] }
                selectedHue = .56f; selectedSaturation = .9f; palette.invalidate(); apply()
            }
        }, LinearLayout.LayoutParams(0, dp(38), 1f))
        footer.addView(TextView(this).apply {
            text = "ЗАКРЫТЬ"; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan); gravity = Gravity.CENTER; setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { dialog.dismiss() }
        })
        panel.addView(footer)
        dialog.setContentView(panel)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(-1, dp(430)); setGravity(Gravity.BOTTOM)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = .16f }
        }
        apply()
    }

    private fun showAppOptions(app: LauncherApp) {
        AlertDialog.Builder(this).setTitle(app.label)
            .setItems(arrayOf("Информация о приложении", "Удалить ярлык с рабочего стола")) { _, which ->
                when (which) {
                    0 -> try { startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:" + app.packageName))) } catch (_: Exception) { }
                    1 -> workspace.removeApp(app)
                }
            }.show()
    }

    private fun showCoreMenu() {
        val options = arrayOf("Plasma Core", "Настройки и инструменты")
        AlertDialog.Builder(this)
            .setTitle("Визуальное ядро Jarvis")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { if (::coreView.isInitialized) coreView.bridge.setTheme(JarvisCoreBridge.CoreTheme.PLASMA) }
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
