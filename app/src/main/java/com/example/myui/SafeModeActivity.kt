package com.example.myui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class SafeModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding((24 * d).toInt(), (24 * d).toInt(), (24 * d).toInt(), (24 * d).toInt())
        }
        col.addView(TextView(this).apply {
            text = "Güvenli mod\nLauncher art arda çöktü."
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
        })
        fun btn(label: String, action: () -> Unit) {
            col.addView(Button(this).apply {
                text = label
                setOnClickListener { action() }
            })
        }
        btn("Varsayılan ana ekranı değiştir") {
            try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
            catch (e: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
        btn("Sistem ayarları") { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        btn("Sıfırla ve tekrar dene") {
            getSharedPreferences("launcher", MODE_PRIVATE).edit()
                .putInt("crashes", 0).putInt("theme", 0).commit()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        setContentView(col)
    }
}
