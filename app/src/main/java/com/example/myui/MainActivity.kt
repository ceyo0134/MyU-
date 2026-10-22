package com.example.myui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.GestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView

class AppItem(val label: String, val pkg: String, val icon: Drawable, val intent: Intent)

class Th(
    val name: String, val top: Int, val bottom: Int, val text: Int,
    val tile: Int, val accent: Int, val lightBar: Boolean
)

object Themes {
    val all = listOf(
        Th("Neon", 0xFF0F0C29.toInt(), 0xFF302B63.toInt(), Color.WHITE, 0x33FFFFFF, 0xFF00E5FF.toInt(), false),
        Th("Minimal", 0xFFF5F5F7.toInt(), 0xFFDADAE0.toInt(), 0xFF111111.toInt(), 0x1A000000, 0xFF0A84FF.toInt(), true),
        Th("Gun Batimi", 0xFFFF7E5F.toInt(), 0xFF6A3093.toInt(), Color.WHITE, 0x33FFFFFF, 0xFFFFD166.toInt(), false)
    )
}

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var root: FrameLayout
    private lateinit var gestures: GestureDetector
    private var apps: List<AppItem> = emptyList()
    private var themeIdx = 0
    private var drawer: View? = null
    private var registered = false

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val wasOpen = drawer != null
            loadApps(); rebuild()
            if (wasOpen) openDrawer()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun alpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)
    private fun barH(name: String): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("launcher", MODE_PRIVATE)

        // Guvenli mod: art arda 3 coktuyse sade ekrani ac
        if (prefs.getInt("crashes", 0) >= 3) {
            startActivity(Intent(this, SafeModeActivity::class.java))
            finish()
            return
        }
        Thread.setDefaultUncaughtExceptionHandler { _, _ ->
            prefs.edit().putInt("crashes", prefs.getInt("crashes", 0) + 1).commit()
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        themeIdx = prefs.getInt("theme", 0).coerceIn(0, Themes.all.lastIndex)
        root = FrameLayout(this)
        setContentView(root)

        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (vy < -800 && drawer == null) { openDrawer(); return true }
                return false
            }
        })

        loadApps()
        rebuild()

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED)
        else registerReceiver(pkgReceiver, f)
        registered = true

        // 5 sn sorunsuz calistiysa cokme sayacini sifirla
        root.postDelayed({ prefs.edit().putInt("crashes", 0).apply() }, 5000)
    }

    override fun onDestroy() {
        if (registered) unregisterReceiver(pkgReceiver)
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::gestures.isInitialized) gestures.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        closeDrawer()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawer != null) closeDrawer()
    }

    private fun loadApps() {
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = pm.queryIntentActivities(q, 0).mapNotNull { r ->
            val p = r.activityInfo.packageName
            if (p == packageName) return@mapNotNull null
            val li = pm.getLaunchIntentForPackage(p) ?: return@mapNotNull null
            li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            AppItem(r.loadLabel(pm).toString(), p, r.loadIcon(pm), li)
        }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
    }

    // Tema hatasinda varsayilan temaya don
    private fun rebuild() {
        try {
            build(Themes.all[themeIdx])
        } catch (t: Throwable) {
            themeIdx = 0
            prefs.edit().putInt("theme", 0).apply()
            build(Themes.all[0])
        }
    }

    private fun build(t: Th) {
        root.removeAllViews()
        drawer = null
        root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(t.top, t.bottom))
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        if (t.lightBar) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.decorView.systemUiVisibility = flags

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), barH("status_bar_height") + dp(56), dp(24), barH("navigation_bar_height") + dp(16))
        }
        col.addView(TextClock(this).apply {
            format24Hour = "HH:mm"; format12Hour = "HH:mm"
            textSize = 76f
            setTextColor(t.text)
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            setOnLongClickListener { openHomeSettings(); true }
        })
        col.addView(TextClock(this).apply {
            format24Hour = "EEEE, d MMMM"; format12Hour = "EEEE, d MMMM"
            textSize = 18f
            setTextColor(t.accent)
        })
        col.addView(View(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        col.addView(TextView(this).apply {
            text = "⌃  Tüm uygulamalar"
            setTextColor(t.text)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(14))
            setOnClickListener { openDrawer() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(30).toFloat(); setColor(t.tile) }
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        dockApps().forEach { a ->
            dock.addView(iconView(a, t, 58, false), LinearLayout.LayoutParams(dp(58), dp(58)).apply {
                setMargins(dp(8), 0, dp(8), 0)
            })
        }
        col.addView(dock, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL })

        root.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Ayarlar kisayolu her zaman gorunur
        root.addView(TextView(this).apply {
            text = "⚙"
            textSize = 26f
            setTextColor(t.text)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener { openHomeSettings() }
        }, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END
        ).apply { topMargin = barH("status_bar_height") })
    }

    private fun iconView(a: AppItem, t: Th, size: Int, tile: Boolean): ImageView = ImageView(this).apply {
        setImageDrawable(a.icon)
        if (tile) {
            val p = dp(size / 8)
            setPadding(p, p, p, p)
            background = GradientDrawable().apply { cornerRadius = dp(size / 3).toFloat(); setColor(t.tile) }
        }
        setOnClickListener { launch(a) }
        setOnLongClickListener { toggleDock(a.pkg); true }
    }

    private fun launch(a: AppItem) {
        try { startActivity(a.intent) } catch (e: Exception) { }
    }

    private fun dockApps(): List<AppItem> {
        val pkgs = prefs.getString("dock", null)?.split(",")?.filter { it.isNotEmpty() }
        val list = pkgs?.mapNotNull { p -> apps.find { it.pkg == p } }
        return if (list.isNullOrEmpty()) apps.take(4) else list
    }

    private fun toggleDock(pkg: String) {
        val cur = dockApps().map { it.pkg }.toMutableList()
        if (cur.contains(pkg)) cur.remove(pkg) else cur.add(pkg)
        while (cur.size > 5) cur.removeAt(0)
        prefs.edit().putString("dock", cur.joinToString(",")).apply()
        val wasOpen = drawer != null
        rebuild()
        if (wasOpen) openDrawer()
    }

    private fun openHomeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (e2: Exception) { }
        }
    }

    private fun closeDrawer() {
        drawer?.let { root.removeView(it) }
        drawer = null
    }

    private fun openDrawer() {
        if (drawer != null) return
        val t = Themes.all[themeIdx]
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(alpha(t.bottom, 0xF2))
            setPadding(dp(16), barH("status_bar_height") + dp(12), dp(16), barH("navigation_bar_height"))
        }

        var shown = apps
        val grid = GridView(this).apply {
            numColumns = 4
            verticalSpacing = dp(16)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            setSelector(android.R.color.transparent)
        }
        val adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(p: Int): Any = shown[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, cv: View?, parent: ViewGroup?): View {
                val a = shown[p]
                val cell = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setOnClickListener { launch(a) }
                    setOnLongClickListener { toggleDock(a.pkg); true }
                }
                cell.addView(iconView(a, t, 60, true), LinearLayout.LayoutParams(dp(60), dp(60)))
                cell.addView(TextView(this@MainActivity).apply {
                    text = a.label
                    setTextColor(t.text)
                    textSize = 12f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    setPadding(0, dp(6), 0, 0)
                })
                return cell
            }
        }
        grid.adapter = adapter

        val search = EditText(this).apply {
            hint = "Ara…"
            setHintTextColor(alpha(t.text, 0x99))
            setTextColor(t.text)
            setSingleLine(true)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(t.tile) }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().trim().lowercase()
                shown = if (q.isEmpty()) apps else apps.filter { it.label.lowercase().contains(q) }
                adapter.notifyDataSetChanged()
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(search, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(TextView(this).apply {
            text = "🎨"
            textSize = 24f
            setPadding(dp(14), dp(8), dp(8), dp(8))
            setOnClickListener {
                themeIdx = (themeIdx + 1) % Themes.all.size
                prefs.edit().putInt("theme", themeIdx).apply()
                rebuild()
                openDrawer()
            }
        })
        top.addView(TextView(this).apply {
            text = "⚙"
            textSize = 24f
            setTextColor(t.text)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { openHomeSettings() }
        })

        box.addView(top)
        box.addView(grid, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ).apply { topMargin = dp(16) })

        box.alpha = 0f
        root.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        box.animate().alpha(1f).setDuration(180).start()
        drawer = box
    }
}   try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (e2: Exception) { }
        }
    }

    private fun closeDrawer() {
        drawer?.let { root.removeView(it) }
        drawer = null
    }

    private fun openDrawer() {
        if (drawer != null) return
        val t = Themes.all[themeIdx]
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(alpha(t.bottom, 0xF2))
            setPadding(dp(16), barH("status_bar_height") + dp(12), dp(16), barH("navigation_bar_height"))
        }

        var shown = apps
        val grid = GridView(this).apply {
            numColumns = 4
            verticalSpacing = dp(16)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            setSelector(android.R.color.transparent)
        }
        val adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(p: Int): Any = shown[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, cv: View?, parent: ViewGroup?): View {
                val a = shown[p]
                val cell = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setOnClickListener { launch(a) }
                    setOnLongClickListener { toggleDock(a.pkg); true }
                }
                cell.addView(iconView(a, t, 60, true), LinearLayout.LayoutParams(dp(60), dp(60)))
                cell.addView(TextView(this@MainActivity).apply {
                    text = a.label
                    setTextColor(t.text)
                    textSize = 12f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    setPadding(0, dp(6), 0, 0)
                })
                return cell
            }
        }
        grid.adapter = adapter

        val search = EditText(this).apply {
            hint = "Ara…"
            setHintTextColor(alpha(t.text, 0x99))
            setTextColor(t.text)
            isSingleLine = true
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(t.tile) }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().trim().lowercase()
                shown = if (q.isEmpty()) apps else apps.filter { it.label.lowercase().contains(q) }
                adapter.notifyDataSetChanged()
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(search, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(TextView(this).apply {
            text = "🎨"
            textSize = 24f
            setPadding(dp(14), dp(8), dp(8), dp(8))
            setOnClickListener {
                themeIdx = (themeIdx + 1) % Themes.all.size
                prefs.edit().putInt("theme", themeIdx).apply()
                rebuild()
                openDrawer()
            }
        })
        top.addView(TextView(this).apply {
            text = "⚙"
            textSize = 24f
            setTextColor(t.text)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { openHomeSettings() }
        })

        box.addView(top)
        box.addView(grid, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ).apply { topMargin = dp(16) })

        box.alpha = 0f
        root.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        box.animate().alpha(1f).setDuration(180).start()
        drawer = box
    }
}
