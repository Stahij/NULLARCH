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
        coreView.bridge.setNativeTouchOwner(true)
        workspace = AtomWorkspaceView(
            this,
            installedApps,
            onOpenChat = { startActivity(Intent(this, ChatHubActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            onOpenSettings = { showCoreMenu() },
            onOpenDrawer = { showAppDrawer() },
            onOpenLab = { showCoreLab() },
            onAppOptions = { showAppOptions(it) },
            onRotateCore = { dx, dy -> coreView.bridge.rotateCore(dx, dy) },
            onWorkspaceChanged = {
                coreView.bridge.setWorkspaceApps(workspace.workspaceAppsJson())
                coreView.bridge.setWorkspacePage(workspace.currentPage)
            },
            onWorkspaceNodeDragged = { id, angle, orbit, page ->
                coreView.bridge.setWorkspaceNodeLayout(id, angle, orbit, page)
            }
        )
        coreView.setNodePositionsListener { workspace.updateWebGlNodePositions(it) }
        coreView.bridge.setWorkspaceApps(workspace.workspaceAppsJson())
        coreView.bridge.setWorkspacePage(workspace.currentPage)
        applyLabSettings(loadLabSettings())
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(3, 8, 20))
            addView(coreView, FrameLayout.LayoutParams(-1, -1))
            addView(workspace, FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(root)
    }

    private data class CoreLabSettings(
        val coreSize: Int = 500, val brightness: Int = 700, val intensity: Int = 700,
        val iconSize: Int = 500, val orbitScale: Int = 500, val glow: Int = 650,
        val hue: Float = 0.56f, val saturation: Float = 0.9f
    )

    private val labPrefs by lazy { getSharedPreferences("nullarch_core_lab", MODE_PRIVATE) }

    private fun encodeLabSettings(s: CoreLabSettings) = org.json.JSONObject()
        .put("coreSize", s.coreSize).put("brightness", s.brightness).put("intensity", s.intensity)
        .put("iconSize", s.iconSize).put("orbitScale", s.orbitScale).put("glow", s.glow)
        .put("hue", s.hue.toDouble()).put("saturation", s.saturation.toDouble())

    private fun decodeLabSettings(j: org.json.JSONObject): CoreLabSettings {
        val d = CoreLabSettings()
        return CoreLabSettings(
            j.optInt("coreSize", d.coreSize).coerceIn(0, 1000),
            j.optInt("brightness", d.brightness).coerceIn(0, 1000),
            j.optInt("intensity", d.intensity).coerceIn(0, 1000),
            j.optInt("iconSize", d.iconSize).coerceIn(0, 1000),
            j.optInt("orbitScale", d.orbitScale).coerceIn(0, 1000),
            j.optInt("glow", d.glow).coerceIn(0, 1000),
            j.optDouble("hue", d.hue.toDouble()).toFloat().coerceIn(0f, 1f),
            j.optDouble("saturation", d.saturation.toDouble()).toFloat().coerceIn(.15f, 1f)
        )
    }

    private fun loadLabSettings(): CoreLabSettings = try {
        val raw = labPrefs.getString("current_settings", null)
        if (raw.isNullOrBlank()) CoreLabSettings() else decodeLabSettings(org.json.JSONObject(raw))
    } catch (_: Exception) { CoreLabSettings() }

    private fun saveLabSettings(s: CoreLabSettings) {
        labPrefs.edit().putString("current_settings", encodeLabSettings(s).toString()).apply()
    }

    private fun loadLabProfiles(): LinkedHashMap<String, CoreLabSettings> {
        val result = linkedMapOf<String, CoreLabSettings>()
        try {
            val array = org.json.JSONArray(labPrefs.getString("profiles", "[]"))
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val name = item.optString("name").trim()
                val settings = item.optJSONObject("settings") ?: continue
                if (name.isNotBlank()) result[name] = decodeLabSettings(settings)
            }
        } catch (_: Exception) { }
        return result
    }

    private fun saveLabProfiles(profiles: Map<String, CoreLabSettings>) {
        val array = org.json.JSONArray()
        profiles.forEach { (name, settings) ->
            array.put(org.json.JSONObject().put("name", name).put("settings", encodeLabSettings(settings)))
        }
        labPrefs.edit().putString("profiles", array.toString()).apply()
    }

    private fun applyLabSettings(s: CoreLabSettings) {
        coreView.bridge.setVisualSettings(
            .65f + s.coreSize / 1000f * .75f,
            .25f + s.brightness / 1000f * 1.25f,
            .25f + s.intensity / 1000f * 1.45f,
            s.hue,
            .25f + s.glow / 1000f * 1.5f,
            s.saturation
        )
        val iconScale = .65f + s.iconSize / 1000f * .85f
        val orbitScale = .65f + s.orbitScale / 1000f * .8f
        workspace.setLabIconScale(iconScale)
        workspace.setLabOrbitScale(orbitScale)
        coreView.bridge.setWorkspaceScales(iconScale, orbitScale)
    }

    private fun promptSaveLabProfile(settings: CoreLabSettings) {
        val nameInput = EditText(this).apply {
            hint = "Например: Холодная плазма"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val wrapper = LinearLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(nameInput, LinearLayout.LayoutParams(-1, dp(48)))
        }
        AlertDialog.Builder(this)
            .setTitle("Сохранить профиль Plasma Core")
            .setView(wrapper)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить") { _, _ ->
                val name = nameInput.text.toString().trim().take(40)
                if (name.isBlank()) {
                    Toast.makeText(this, "Укажи название профиля", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val profiles = loadLabProfiles()
                profiles[name] = settings
                saveLabProfiles(profiles)
                Toast.makeText(this, "Профиль «${name}» сохранён", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showLabProfiles(onApply: (CoreLabSettings) -> Unit) {
        val profiles = loadLabProfiles()
        if (profiles.isEmpty()) {
            Toast.makeText(this, "Пока нет сохранённых профилей", Toast.LENGTH_SHORT).show()
            return
        }
        val names = profiles.keys.toList()
        AlertDialog.Builder(this)
            .setTitle("Профили Plasma Core")
            .setItems(names.toTypedArray()) { _, index ->
                val name = names[index]
                AlertDialog.Builder(this)
                    .setTitle(name)
                    .setItems(arrayOf("Применить профиль", "Удалить профиль")) { _, action ->
                        if (action == 0) {
                            profiles[name]?.let(onApply)
                        } else {
                            AlertDialog.Builder(this)
                                .setMessage("Удалить профиль «${name}»?")
                                .setNegativeButton("Отмена", null)
                                .setPositiveButton("Удалить") { _, _ ->
                                    val updated = loadLabProfiles()
                                    updated.remove(name)
                                    saveLabProfiles(updated)
                                    Toast.makeText(this, "Профиль удалён", Toast.LENGTH_SHORT).show()
                                }
                                .show()
                        }
                    }
                    .setNegativeButton("Назад", null)
                    .show()
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun showCoreLab() {
        val initial = loadLabSettings()
        val dialog = android.app.Dialog(this)
        val bars = mutableListOf<android.widget.SeekBar>()
        var selectedHue = initial.hue
        var selectedSaturation = initial.saturation
        var palette: View? = null
        fun snapshot() = CoreLabSettings(
            bars.getOrNull(0)?.progress ?: initial.coreSize,
            bars.getOrNull(1)?.progress ?: initial.brightness,
            bars.getOrNull(2)?.progress ?: initial.intensity,
            bars.getOrNull(3)?.progress ?: initial.iconSize,
            bars.getOrNull(4)?.progress ?: initial.orbitScale,
            bars.getOrNull(5)?.progress ?: initial.glow,
            selectedHue, selectedSaturation
        )
        fun apply() {
            val settings = snapshot()
            applyLabSettings(settings)
            saveLabSettings(settings)
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.rgb(7, 17, 32))
                cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f)
                setStroke(dp(1), Color.rgb(35, 105, 139))
            }
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "PLASMA CORE LAB"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, dp(30), 1f))
        header.addView(TextView(this).apply {
            text = "ПРОФИЛИ"; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cyan); gravity = Gravity.CENTER
            setOnClickListener {
                showLabProfiles { profile ->
                    selectedHue = profile.hue
                    selectedSaturation = profile.saturation
                    bars.forEachIndexed { index, bar -> bar.progress = profile.toValues()[index] }
                    palette?.invalidate()
                    apply()
                }
            }
        }, LinearLayout.LayoutParams(dp(64), dp(30)))
        header.addView(TextView(this).apply {
            text = "✕"; textSize = 17f; setTextColor(cyan); gravity = Gravity.CENTER
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(34), dp(30)))
        panel.addView(header)

        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        actionRow.addView(TextView(this).apply {
            text = "Настройки сохраняются автоматически"; textSize = 9f; setTextColor(Color.rgb(132, 160, 181))
        }, LinearLayout.LayoutParams(0, dp(24), 1f))
        actionRow.addView(TextView(this).apply {
            text = "СОХРАНИТЬ ПРОФИЛЬ"; textSize = 9f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cyan)
            gravity = Gravity.CENTER; setPadding(dp(5), 0, dp(5), 0)
            setOnClickListener { promptSaveLabProfile(snapshot()) }
        }, LinearLayout.LayoutParams(-2, dp(24)))
        panel.addView(actionRow)

        val content = android.widget.ScrollView(this).apply { isFillViewport = false }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(controls)
        panel.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        val labels = listOf("Размер ядра", "Яркость ядра", "Интенсивность", "Размер иконок", "Масштаб орбит", "Свечение")
        val values = initial.toValues()
        labels.forEachIndexed { index, label ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply {
                text = label; textSize = 10f; setTextColor(Color.rgb(210, 228, 241))
            }, LinearLayout.LayoutParams(0, dp(22), 1f))
            val value = TextView(this).apply { text = "${values[index] / 10}%"; textSize = 10f; setTextColor(cyan) }
            row.addView(value)
            controls.addView(row)
            val bar = android.widget.SeekBar(this).apply { max = 1000; progress = values[index] }
            bar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    value.text = "${progress / 10}%"
                    apply()
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            })
            bars.add(bar)
            controls.addView(bar, LinearLayout.LayoutParams(-1, dp(28)))
        }

        controls.addView(TextView(this).apply {
            text = "ЦВЕТ И ОТТЕНОК"; textSize = 10f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(170, 205, 225)); setPadding(0, dp(3), 0, dp(3))
        })
        palette = object : View(this) {
            private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            private val hues = floatArrayOf(0f, 45f, 90f, 150f, 195f, 225f, 270f, 315f)
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val cellW = width.toFloat() / hues.size
                hues.forEachIndexed { index, hue ->
                    p.color = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
                    canvas.drawRect(index * cellW, 0f, (index + 1) * cellW + 1f, height.toFloat(), p)
                }
                val selected = hues.indices.minByOrNull { kotlin.math.abs(hues[it] / 360f - selectedHue) } ?: 0
                p.color = Color.WHITE
                p.style = android.graphics.Paint.Style.STROKE
                p.strokeWidth = dp(2).toFloat()
                canvas.drawCircle((selected + .5f) * cellW, height / 2f, dp(8).toFloat(), p)
                p.style = android.graphics.Paint.Style.FILL
            }
            override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
                if (event.action == android.view.MotionEvent.ACTION_DOWN || event.action == android.view.MotionEvent.ACTION_MOVE) {
                    val index = ((event.x / width.coerceAtLeast(1)) * hues.size).toInt().coerceIn(0, hues.lastIndex)
                    selectedHue = hues[index] / 360f
                    selectedSaturation = 1f
                    invalidate()
                    apply()
                    return true
                }
                return true
            }
        }
        controls.addView(palette, LinearLayout.LayoutParams(-1, dp(54)))
        controls.addView(TextView(this).apply {
            text = "8 отдельных цветов без промежуточных оттенков"
            textSize = 9f; setTextColor(Color.rgb(132, 160, 181))
        })

        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        footer.addView(TextView(this).apply {
            text = "СБРОСИТЬ"; textSize = 10f; setTextColor(Color.rgb(150, 190, 210))
            setPadding(dp(8), dp(5), dp(8), dp(5))
            setOnClickListener {
                val defaults = CoreLabSettings()
                selectedHue = defaults.hue
                selectedSaturation = defaults.saturation
                bars.forEachIndexed { index, bar -> bar.progress = defaults.toValues()[index] }
                palette?.invalidate()
                apply()
            }
        }, LinearLayout.LayoutParams(0, dp(28), 1f))
        footer.addView(TextView(this).apply {
            text = "ГОТОВО"; textSize = 10f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan); gravity = Gravity.CENTER; setPadding(dp(8), dp(5), dp(8), dp(5))
            setOnClickListener { dialog.dismiss() }
        })
        panel.addView(footer)
        dialog.setContentView(panel)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(-1, minOf(dp(230), (resources.displayMetrics.heightPixels * .62f).toInt()))
            setGravity(Gravity.BOTTOM)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = .12f }
        }
        apply()
    }

    private fun CoreLabSettings.toValues() = intArrayOf(coreSize, brightness, intensity, iconSize, orbitScale, glow)

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
            .setTitle("Управление NULLARCH")
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
