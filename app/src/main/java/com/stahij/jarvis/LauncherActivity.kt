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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class LauncherActivity : Activity() {
    private lateinit var workspace: AtomWorkspaceView
    private val cyan = Color.rgb(99, 224, 255)
    private val bg = Color.rgb(3, 8, 20)
    private var installedApps: List<LauncherApp> = emptyList()
    private lateinit var pageLabel: TextView

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
        if (::workspace.isInitialized) workspace.resumeMotion()
    }

    override fun onPause() {
        if (::workspace.isInitialized) workspace.pauseMotion()
        super.onPause()
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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(16), dp(4))
        }
        val brand = TextView(this).apply {
            text = "J A R V I S"
            textSize = 18f
            letterSpacing = .18f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(cyan)
        }
        header.addView(brand, LinearLayout.LayoutParams(0, dp(42), 1f))
        val assistant = action("◉", "Открыть Jarvis AI") {
            startActivity(Intent(this, ChatHubActivity::class.java))
        }
        header.addView(assistant, LinearLayout.LayoutParams(dp(46), dp(42)))
        val drawer = action("＋", "Добавить приложение на рабочий стол") { showAppDrawer() }
        header.addView(drawer, LinearLayout.LayoutParams(dp(46), dp(42)).apply { leftMargin = dp(8) })
        root.addView(header)

        pageLabel = TextView(this).apply {
            text = "ПУСТОЕ ПРОСТРАНСТВО • ДОБАВЬ СВОИ ПРИЛОЖЕНИЯ"
            textSize = 9f
            letterSpacing = .12f
            setTextColor(Color.rgb(104, 153, 184))
            setPadding(dp(22), 0, dp(22), dp(4))
        }
        root.addView(pageLabel)

        workspace = AtomWorkspaceView(this, installedApps) { page, count ->
            pageLabel.text = if (count == 1 && page == 0) {
                "МОЁ ПРОСТРАНСТВО • ДОБАВЛЯЙ ТОЛЬКО НУЖНОЕ"
            } else {
                "СТРАНИЦА %02d / %02d • СВАЙП ДЛЯ ПЕРЕКЛЮЧЕНИЯ".format(page + 1, count)
            }
        }
        root.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun action(symbol: String, description: String, click: () -> Unit) = TextView(this).apply {
        text = symbol
        contentDescription = description
        textSize = 21f
        gravity = Gravity.CENTER
        setTextColor(cyan)
        background = GradientDrawable().apply {
            setColor(Color.rgb(10, 25, 43))
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), Color.rgb(28, 83, 111))
        }
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
    }

    private fun showAppDrawer() {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(8))
        }
        val search = EditText(this).apply {
            hint = "Найти приложение"
            setSingleLine(true)
            textSize = 16f
        }
        column.addView(search)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = android.widget.ScrollView(this)
        scroll.addView(list)
        column.addView(scroll, LinearLayout.LayoutParams(-1, dp(440)))

        val dialog = AlertDialog.Builder(this)
            .setTitle("Выбери приложение для рабочего стола")
            .setView(column)
            .setNegativeButton("Закрыть", null)
            .create()

        fun render(query: String) {
            list.removeAllViews()
            installedApps
                .filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
                .forEach { app ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(8), dp(8), dp(8), dp(8))
                    }
                    val icon = TextView(this).apply {
                        setCompoundDrawablesWithIntrinsicBounds(app.icon, null, null, null)
                        compoundDrawablePadding = dp(10)
                        text = app.label
                        textSize = 15f
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    row.addView(icon, LinearLayout.LayoutParams(0, dp(48), 1f))
                    val add = TextView(this).apply {
                        text = "＋"
                        textSize = 22f
                        gravity = Gravity.CENTER
                        setTextColor(cyan)
                        setPadding(dp(14), 0, dp(14), 0)
                    }
                    row.addView(add, LinearLayout.LayoutParams(dp(48), dp(48)))
                    row.setOnClickListener {
                        val added = workspace.addApp(app)
                        Toast.makeText(
                            this,
                            if (added) "${app.label} добавлено на экран" else "Это приложение уже на рабочем столе",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (added) dialog.dismiss()
                    }
                    list.addView(row)
                    val divider = View(this).apply { setBackgroundColor(Color.rgb(24, 48, 68)) }
                    list.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
                }
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = render(s?.toString().orEmpty())
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        render("")
        dialog.show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
