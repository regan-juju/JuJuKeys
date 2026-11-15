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
    @Volatile var dark = true

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
        val k = "${app.key}@$px@$dark"
        cache.get(k)?.let { return it }
        val bmp = render(ctx, app, px).asImageBitmap()
        cache.put(k, bmp); return bmp
    }

    fun peek(app: AppInfo, px: Int): ImageBitmap? = cache.get("${app.key}@$px@$dark")

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
        } else if (dark) {
            drawDark(ctx, app, c, s, paint)
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

    /** Dark tile + round badge with a thin blue ring (matches the phone's current dark theme). */
    private fun drawDark(ctx: Context, app: AppInfo, c: Canvas, s: Int, paint: Paint) {
        val f = s.toFloat()
        paint.shader = LinearGradient(0f, 0f, 0f, f, 0xFF1D2438.toInt(), 0xFF0E121E.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, f, f, paint); paint.shader = null
        val d: Drawable? = try { ctx.packageManager.getActivityIcon(android.content.ComponentName(app.pkg, app.cls)) } catch (_: Exception) { null }
        val dia = f * 0.66f; val cx = f / 2; val cy = f / 2
        val circle = Path().apply { addCircle(cx, cy, dia / 2, Path.Direction.CW) }
        val full = d is AdaptiveIconDrawable && d.background != null
        c.save()
        if (d is AdaptiveIconDrawable) {
            c.clipPath(circle)
            val half = dia * 0.75f   // 108dp layer, 72dp visible
            val l = (cx - half).toInt(); val t = (cy - half).toInt(); val r = (cx + half).toInt(); val b = (cy + half).toInt()
            d.background?.apply { setBounds(l, t, r, b); draw(c) }
            d.foreground?.apply { setBounds(l, t, r, b); draw(c) }
        } else if (d != null) {
            val tmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, s, s); d.draw(Canvas(tmp))
            val fullBleed = corner(tmp, 2) > 235 && corner(tmp, s - 3) > 235
            if (fullBleed) {   // big square logo: show it on the tile without a ring
                c.restore(); c.save()
                val pad = f * 0.17f
                c.clipPath(Path().apply { addRoundRect(RectF(pad, pad, f - pad, f - pad), f * 0.16f, f * 0.16f, Path.Direction.CW) })
                c.drawBitmap(tmp, null, RectF(pad, pad, f - pad, f - pad), paint)
                c.restore(); return
            }
            c.clipPath(circle)
            paint.color = 0xFFFFFFFF.toInt(); c.drawCircle(cx, cy, dia / 2, paint)
            val g = dia * 0.14f
            d.setBounds((cx - dia / 2 + g).toInt(), (cy - dia / 2 + g).toInt(), (cx + dia / 2 - g).toInt(), (cy + dia / 2 - g).toInt()); d.draw(c)
        }
        c.restore()
        if (d is AdaptiveIconDrawable || d != null) {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = f * 0.028f; color = 0xFF3F5FD8.toInt() }
            c.drawCircle(cx, cy, dia / 2, ring)
        }
    }

    private fun corner(b: Bitmap, p: Int): Int = Color.alpha(b.getPixel(p, p))
}
