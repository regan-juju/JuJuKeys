package com.reganbarua.jujulauncher

import android.content.Context
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** iOS-style rounded-square ("squircle") icons. Our own drawing, no Apple artwork. */
object Icons {
    private val cache = object : LruCache<String, ImageBitmap>(160) {}
    @Volatile var version = 0

    fun clear() { cache.evictAll(); version++ }

    fun customDir(ctx: Context) = File(ctx.filesDir, "icons").apply { mkdirs() }

    /** superellipse (n = 5) similar to the iOS shape */
    fun squircle(s: Float): Path {
        val p = Path(); val n = 5.0; val r = s / 2f
        val steps = 120
        for (i in 0..steps) {
            val t = i * 2 * Math.PI / steps
            val c = cos(t); val sn = sin(t)
            val x = r + r * (sign(c) * abs(c).pow(2 / n)).toFloat()
            val y = r + r * (sign(sn) * abs(sn).pow(2 / n)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close(); return p
    }

    fun get(ctx: Context, app: AppInfo, px: Int): ImageBitmap {
        val k = "${app.key}@$px"
        cache.get(k)?.let { return it }
        val bmp = render(ctx, app, px).asImageBitmap()
        cache.put(k, bmp); return bmp
    }

    fun peek(app: AppInfo, px: Int): ImageBitmap? = cache.get("${app.key}@$px")

    private fun customBitmap(ctx: Context, pkg: String): Bitmap? {
        val f = File(customDir(ctx), "$pkg.png")
        if (f.exists()) BitmapFactory.decodeFile(f.path)?.let { return it }
        return try { ctx.assets.open("icons/$pkg.png").use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }
    }

    private fun render(ctx: Context, app: AppInfo, s: Int): Bitmap {
        val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        c.save(); c.clipPath(squircle(s.toFloat()))
        val custom = customBitmap(ctx, app.pkg)
        if (custom != null) {
            c.drawBitmap(custom, null, Rect(0, 0, s, s), paint)
        } else {
            val d: Drawable? = try { ctx.packageManager.getActivityIcon(android.content.ComponentName(app.pkg, app.cls)) } catch (_: Exception) { null }
            when {
                d == null -> { paint.color = 0xFF3A3A3C.toInt(); c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), paint) }
                d is AdaptiveIconDrawable -> {
                    val m = (s * 0.25f).toInt()
                    d.background?.apply { setBounds(-m, -m, s + m, s + m); draw(c) }
                    d.foreground?.apply { setBounds(-m, -m, s + m, s + m); draw(c) }
                }
                else -> {
                    val tmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
                    d.setBounds(0, 0, s, s); d.draw(Canvas(tmp))
                    val fullBleed = corner(tmp, 2) > 235 && corner(tmp, s - 3) > 235
                    if (fullBleed) c.drawBitmap(tmp, 0f, 0f, paint)
                    else {
                        paint.shader = LinearGradient(0f, 0f, 0f, s.toFloat(), 0xFFFFFFFF.toInt(), 0xFFD9D9DE.toInt(), Shader.TileMode.CLAMP)
                        c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), paint); paint.shader = null
                        val pad = (s * 0.16f).toInt()
                        d.setBounds(pad, pad, s - pad, s - pad); d.draw(c)
                    }
                }
            }
        }
        c.restore()
        return out
    }

    private fun corner(b: Bitmap, p: Int): Int = Color.alpha(b.getPixel(p, p))
}
