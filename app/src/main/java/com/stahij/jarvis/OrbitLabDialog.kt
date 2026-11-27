package com.stahij.jarvis

import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.text.InputType
import android.widget.EditText
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Editable orbital plane controls shown over the live launcher scene. */
class OrbitLabDialog(
    private val context: Context,
    private val initialJson: String,
    private val onConfigChanged: (String) -> Unit,
    private val onPreviewEnabled: (Boolean) -> Unit
) {
    private data class Orbit(
        val name: String,
        var x: Float,
        var y: Float,
        var z: Float,
        var radius: Float,
        var speed: Float,
        var visible: Boolean = false
    )

    private val prefs = context.getSharedPreferences("nullarch_orbit_lab", Context.MODE_PRIVATE)
    private val cyan = Color.rgb(99, 224, 255)
    private val defaults = listOf(
        Orbit("Вертикальная", 84f, 92f, 159f, 1.58f, .5625f, true),
        Orbit("Горизонтальная", 83f, 0f, 0f, 1.58f, .300f, true),
        Orbit("Диагональ вправо", 83f, 50f, 40f, 1.67f, .300f, true),
        Orbit("Диагональ влево", 83f, -45f, 19f, 1.62f, .481875f, true)
    )
    private val orbits = mutableListOf<Orbit>()
    private val dialog = Dialog(context)
    private lateinit var tabs: LinearLayout
    private lateinit var controls: LinearLayout
    private lateinit var coordinates: TextView
    private var selected = 0

    companion object {
        fun defaultConfigJson(): String {
            val defaults = arrayOf(
                floatArrayOf(84f, 92f, 159f, 1.58f, .5625f),
                floatArrayOf(83f, 0f, 0f, 1.58f, .300f),
                floatArrayOf(83f, 50f, 40f, 1.67f, .5625f),
                floatArrayOf(83f, -45f, 19f, 1.62f, .481875f)
            )
            val names = arrayOf("vertical", "horizontal", "diagonal-right", "diagonal-left")
            val array = JSONArray()
            defaults.forEachIndexed { i, v ->
                array.put(JSONObject().put("index", i).put("name", names[i])
                    .put("rotationX", v[0].toDouble()).put("rotationY", v[1].toDouble()).put("rotationZ", v[2].toDouble())
                    .put("radius", v[3].toDouble()).put("speedRadPerSec", v[4].toDouble()).put("visible", true))
            }
            return array.toString()
        }
    }

    fun show() {
        val upgraded = prefs.getString("configs_fast_2", null)
        if (upgraded != null) {
            load(upgraded)
        } else {
            val previous = prefs.getString("configs_fast_1", null)
            if (previous != null) {
                val migrated = JSONArray(previous)
                for (i in 0 until migrated.length()) {
                    val item = migrated.optJSONObject(i) ?: continue
                    val speed = item.optDouble("speedRadPerSec", 0.0) * 1.25
                    item.put("speedRadPerSec", speed.coerceIn(-0.5625, 0.5625))
                }
                val migratedJson = migrated.toString()
                load(migratedJson)
                prefs.edit().putString("configs_fast_2", migratedJson).apply()
            } else load(initialJson)
        }
        buildUi()
        renderOrbit(0)
        dialog.setContentView(buildRoot)
        dialog.setOnDismissListener { onPreviewEnabled(false) }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            val metrics = context.resources.displayMetrics
            setLayout(-1, minOf(dp(230), (metrics.heightPixels * .62f).toInt()))
            setGravity(Gravity.BOTTOM)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = .42f }
        }
        onConfigChanged(configArray().toString())
        onPreviewEnabled(true)
    }

    private fun initialJsonFallback() = defaultConfigJson()
    private lateinit var buildRoot: LinearLayout

    private fun load(raw: String) {
        orbits.clear()
        try {
            val array = JSONArray(raw)
            if (array.length() == 4) {
                defaults.forEachIndexed { i, d ->
                    val j = array.optJSONObject(i) ?: JSONObject()
                    orbits.add(d.copy(
                        x = j.optDouble("rotationX", d.x.toDouble()).toFloat().coerceIn(-180f, 180f),
                        y = j.optDouble("rotationY", d.y.toDouble()).toFloat().coerceIn(-180f, 180f),
                        z = j.optDouble("rotationZ", d.z.toDouble()).toFloat().coerceIn(-180f, 180f),
                        radius = j.optDouble("radius", d.radius.toDouble()).toFloat().coerceIn(.60f, 2.80f),
                        speed = j.optDouble("speedRadPerSec", d.speed.toDouble()).toFloat().coerceIn(-.5625f, .5625f),
                        visible = j.optBoolean("visible", false)
                    ))
                }
                return
            }
        } catch (_: Exception) { }
        orbits.addAll(defaults.map { it.copy() })
    }

    private fun configArray(): JSONArray {
        val array = JSONArray()
        orbits.forEachIndexed { i, o ->
            array.put(JSONObject().put("index", i).put("name", o.name)
                .put("rotationX", o.x.toDouble()).put("rotationY", o.y.toDouble()).put("rotationZ", o.z.toDouble())
                .put("radius", o.radius.toDouble()).put("speedRadPerSec", o.speed.toDouble())
                .put("visible", o.visible))
        }
        return array
    }

    private fun exportedJson(): String {
        val planes = JSONArray()
        orbits.forEachIndexed { i, o ->
            val x = Math.toRadians(o.x.toDouble())
            val y = Math.toRadians(o.y.toDouble())
            val z = Math.toRadians(o.z.toDouble())
            // Three.js Euler XYZ: rotate the local +Z normal by the same Euler matrix.
            val nx = sin(y)
            val ny = -sin(x) * cos(y)
            val nz = cos(x) * cos(y)
            planes.put(JSONObject().put("index", i + 1).put("name", o.name)
                .put("rotationEulerXYZ_deg", JSONObject().put("x", o.x.toDouble()).put("y", o.y.toDouble()).put("z", o.z.toDouble()))
                .put("planeNormal", JSONObject().put("x", nx).put("y", ny).put("z", nz))
                .put("radius", o.radius.toDouble()).put("speed_rad_per_sec", o.speed.toDouble())
                .put("direction", if (o.speed >= 0f) "positive" else "negative")
                .put("previewVisible", o.visible))
        }
        return JSONObject().put("coordinateSystem", "Three.js right-handed; origin at core center; Euler order XYZ; angles in degrees")
            .put("planes", planes).toString(2)
    }

    private fun buildUi() {
        buildRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.rgb(5, 13, 27)); cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.rgb(35, 105, 139))
            }
        }
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(context).apply {
            text = "ОРБИТАЛЬНАЯ ЛАБОРАТОРИЯ"
            textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        header.addView(TextView(context).apply {
            text = "✕"; textSize = 20f; gravity = Gravity.CENTER; setTextColor(cyan)
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(36), dp(40)))
        buildRoot.addView(header)
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        body.addView(TextView(context).apply {
            text = "Настрой плоскость • изменения сразу применяются • сохраняются автоматически"
            textSize = 10f; setTextColor(Color.rgb(132, 160, 181)); setPadding(0, 0, 0, dp(8))
        })
        tabs = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        body.addView(tabs, LinearLayout.LayoutParams(-1, dp(42)))
        controls = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
        body.addView(controls)
        body.addView(TextView(context).apply {
            text = "ЦЕНТР ЯДРА = (0, 0, 0) • Euler XYZ • правосторонняя система"
            textSize = 9f; setTextColor(Color.rgb(120, 154, 178)); setPadding(0, dp(5), 0, dp(5))
        })
        coordinates = TextView(context).apply {
            textSize = 9f; typeface = Typeface.MONOSPACE; setTextColor(Color.rgb(155, 213, 232))
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply { setColor(Color.rgb(9, 24, 40)); cornerRadius = dp(10).toFloat() }
        }
        body.addView(coordinates, LinearLayout.LayoutParams(-1, dp(64)))
        val footer = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        footer.addView(TextView(context).apply {
            text = "КОПИРОВАТЬ JSON"; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cyan)
            setPadding(dp(6), dp(10), dp(6), dp(10))
            setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("NULLARCH orbit coordinates", exportedJson()))
                Toast.makeText(context, "Координаты скопированы", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        footer.addView(TextView(context).apply {
            text = "СБРОС"; textSize = 10f; setTextColor(Color.rgb(170, 192, 210)); gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(10))
            setOnClickListener {
                orbits[selected] = defaults[selected].copy()
                changed(); renderOrbit(selected)
            }
        })
        body.addView(footer)
        val scroll = ScrollView(context).apply { isFillViewport = false; clipToPadding = false }
        scroll.addView(body)
        buildRoot.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun changed() {
        val json = configArray().toString()
        prefs.edit().putString("configs_fast_2", json).apply()
        onConfigChanged(json)
        updateCoordinates()
    }

    private fun renderOrbit(index: Int) {
        selected = index.coerceIn(0, 3)
        tabs.removeAllViews()
        defaults.indices.forEach { i ->
            tabs.addView(TextView(context).apply {
                text = (i + 1).toString(); textSize = 14f; gravity = Gravity.CENTER
                setTextColor(if (i == selected) Color.WHITE else Color.rgb(127, 165, 190))
                background = GradientDrawable().apply {
                    setColor(if (i == selected) Color.rgb(16, 68, 94) else Color.rgb(9, 25, 42))
                    cornerRadius = dp(10).toFloat()
                }
                setOnClickListener { renderOrbit(i) }
            }, LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                marginStart = dp(2); marginEnd = dp(2)
            })
        }
        controls.removeAllViews()
        val o = orbits[selected]
        controls.addView(TextView(context).apply {
            text = "ПЛОСКОСТЬ " + (selected + 1) + " • " + o.name.uppercase(Locale.ROOT)
            textSize = 11f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            setPadding(0, dp(4), 0, dp(6))
        })
        controls.addView(CheckBox(context).apply {
            text = "Показать траекторию в 3D (только лаборатория)"
            textSize = 10f; setTextColor(Color.rgb(210, 230, 242))
            buttonTintList = ColorStateList.valueOf(cyan)
            isChecked = o.visible
            setOnCheckedChangeListener { _, checked -> o.visible = checked; changed() }
        }, LinearLayout.LayoutParams(-1, dp(38)))
        angleControl("Наклон X", o.x) { o.x = it }
        angleControl("Наклон Y", o.y) { o.y = it }
        angleControl("Поворот Z", o.z) { o.z = it }
        floatControl("Радиус", .60f, 2.80f, o.radius, 220, .01f, "%.2f") { o.radius = it }
        floatControl("Скорость (рад/с)", -.5625f, .5625f, o.speed, 1125, .001f, "%+.3f") { o.speed = it }
        updateCoordinates()
    }

    private fun angleControl(label: String, initial: Float, setter: (Float) -> Unit) {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(context).apply {
            text = label; textSize = 10f; setTextColor(Color.rgb(218, 232, 244))
        }, LinearLayout.LayoutParams(0, dp(22), 1f))
        val value = TextView(context).apply {
            text = initial.toInt().toString() + "°"; textSize = 10f; setTextColor(cyan)
            setPadding(dp(8), dp(3), dp(4), dp(3))
            background = GradientDrawable().apply {
                setColor(Color.rgb(9, 25, 42)); cornerRadius = dp(6).toFloat()
            }
            contentDescription = "Точное значение угла. Нажми, чтобы ввести число"
        }
        row.addView(value); controls.addView(row)
        val bar = SeekBar(context).apply { max = 360; progress = (initial + 180f).toInt().coerceIn(0, 360) }
        fun setAngle(v: Float) {
            val exact = v.coerceIn(-180f, 180f)
            bar.progress = (exact + 180f).toInt()
            setter(exact)
            value.text = exact.toInt().toString() + "°"
            changed()
        }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                setAngle((progress - 180).toFloat())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        value.setOnClickListener {
            val input = EditText(context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                setText(value.text.toString().removeSuffix("°"))
                setSelection(text.length)
                hint = "-180…180"
            }
            AlertDialog.Builder(context)
                .setTitle("$label • точный угол")
                .setMessage("Введи целое число градусов от −180 до 180")
                .setView(input)
                .setNegativeButton("ОТМЕНА", null)
                .setPositiveButton("ПРИМЕНИТЬ") { _, _ ->
                    val parsed = input.text.toString().trim().toIntOrNull()
                    if (parsed == null || parsed !in -180..180) {
                        Toast.makeText(context, "Нужно число от −180 до 180", Toast.LENGTH_SHORT).show()
                    } else setAngle(parsed.toFloat())
                }
                .show()
        }
        controls.addView(bar, LinearLayout.LayoutParams(-1, dp(30)))
    }

    private fun floatControl(label: String, min: Float, max: Float, initial: Float, maxProgress: Int, step: Float, format: String, setter: (Float) -> Unit) {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(context).apply {
            text = label; textSize = 10f; setTextColor(Color.rgb(218, 232, 244))
        }, LinearLayout.LayoutParams(0, dp(22), 1f))
        val value = TextView(context).apply { text = String.format(Locale.US, format, initial); textSize = 10f; setTextColor(cyan) }
        row.addView(value); controls.addView(row)
        val bar = SeekBar(context).apply {
            this.max = maxProgress; progress = ((initial - min) / step).toInt().coerceIn(0, maxProgress)
        }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = (min + progress * step).coerceIn(min, max)
                setter(v); value.text = String.format(Locale.US, format, v); changed()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        controls.addView(bar, LinearLayout.LayoutParams(-1, dp(30)))
    }

    private fun updateCoordinates() {
        val o = orbits[selected]
        coordinates.text = "X " + o.x.toInt() + "°  Y " + o.y.toInt() + "°  Z " + o.z.toInt() +
            "°\nR " + String.format(Locale.US, "%.2f", o.radius) +
            "  ω " + String.format(Locale.US, "%+.3f", o.speed) + " rad/s\n" +
            "Плоскость " + (selected + 1) + "/4 • preview " + if (o.visible) "ON" else "OFF"
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
