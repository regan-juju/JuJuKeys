package com.reganbarua.jujukeys

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import com.reganbarua.jujukeys.settings.Prefs

/** Debug only: colourful screen + a text field, so the screenshot shows what the keyboard blurs. */
class BlurDemoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra("theme")?.let { Prefs.setString(this, "theme", it) }
        val root = FrameLayout(this)
        root.addView(Colourful(this), ViewGroup.LayoutParams(-1, -1))
        val et = EditText(this).apply { setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); hint = "type here"; setBackgroundColor(0x66000000) }
        root.addView(et, FrameLayout.LayoutParams(-1, -2).apply { topMargin = 80 })
        setContentView(root)
        et.requestFocus()
    }

    private class Colourful(c: Context) : View(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(cv: Canvas) {
            cv.drawColor(0xFF202833.toInt())
            val cols = intArrayOf(0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(), 0xFFFDD835.toInt(), 0xFF8E24AA.toInt(), 0xFFFB8C00.toInt())
            val w = width.toFloat(); val h = height.toFloat()
            // big colour tiles across the lower half, where the keyboard sits
            for (i in 0 until 12) {
                p.color = cols[i % cols.size]
                val x = (i % 4) * w / 4f; val y = h * 0.35f + (i / 4) * h * 0.2f
                cv.drawRoundRect(x + 10, y, x + w / 4f - 10, y + h * 0.17f, 30f, 30f, p)
            }
        }
    }
}
