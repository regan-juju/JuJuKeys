package com.reganbarua.jujukeys.sticker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Turns a picture from the gallery into a sticker, on the phone (nothing is uploaded):
 * 1. already transparent (a ready sticker PNG), or a sticker sheet on a black background →
 *    kept as it is; a sheet of several stickers (e.g. 3×3) is cut into separate stickers;
 * 2. otherwise, if "পটভূমি সরান" is on → Google ML Kit cuts out the people / main subject,
 *    and a white sticker border is added (if "সাদা বর্ডার" is on);
 * 3. trimmed and fitted into 512×512.
 * Run on a background thread.
 */
class StickerMaker(private val ctx: Context) {

    enum class Result { CUT_OUT, KEPT_TRANSPARENT, SPLIT, KEPT_AS_IS, CUT_FAILED, BAD_IMAGE }

    private var seg: SubjectSegmenter? = null
    private val segmenter: SubjectSegmenter
        get() = seg ?: SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
        ).also { seg = it }

    /** Makes sure Google's cut-out model is on the phone (downloads it once). Blocking. */
    fun ensureModel(): Boolean {
        return try {
            val mi = ModuleInstall.getClient(ctx)
            if (Tasks.await(mi.areModulesAvailable(segmenter), 30, TimeUnit.SECONDS).areModulesAvailable()) return true
            Tasks.await(mi.installModules(ModuleInstallRequest.newBuilder().addApi(segmenter).build()), 180, TimeUnit.SECONDS)
            Tasks.await(mi.areModulesAvailable(segmenter), 30, TimeUnit.SECONDS).areModulesAvailable()
        } catch (e: Exception) {
            false
        }
    }

    /** What one picked picture became. [names] = the stickers saved (several for a sticker sheet). */
    class Made(val result: Result, val names: List<String>)

    /** False once the cut-out model could not be fetched (asked once per [StickerMaker]). */
    var modelOk: Boolean? = null; private set

    fun make(uri: Uri, cutOut: Boolean, border: Boolean, split: Boolean): Made {
        val src = decode(uri) ?: return Made(Result.BAD_IMAGE, emptyList())
        // 1) sticker sheet / ready-made sticker: see-through already, or on a black background
        var sheet: IntArray? = null
        if (hasTransparency(src)) sheet = pixels(src)
        else if (split) pixels(src).let { if (StickerSplit.darkBackgroundToAlpha(it, src.width, src.height)) sheet = it }
        sheet?.let { px ->
            val pieces = if (split) StickerSplit.split(px, src.width, src.height)
            else listOf(Piece(src.width, src.height, px))
            val names = pieces.map { p -> StickerStore.add(ctx, fit(Bitmap.createBitmap(p.px, p.w, p.h, Bitmap.Config.ARGB_8888))) }
            return Made(if (names.size > 1) Result.SPLIT else Result.KEPT_TRANSPARENT, names)
        }
        // 2) ordinary photo
        val result: Result
        val art: Bitmap
        if (cutOut && (modelOk ?: ensureModel().also { modelOk = it })) {
            val fg = runCatching {
                Tasks.await(segmenter.process(InputImage.fromBitmap(src, 0)), 60, TimeUnit.SECONDS).foregroundBitmap
            }.getOrNull()
            if (fg != null && trimRect(fg) != null) {
                result = Result.CUT_OUT
                art = if (border) addBorder(fg) else fg
            } else {
                result = Result.CUT_FAILED; art = src
            }
        } else {
            result = if (cutOut) Result.CUT_FAILED else Result.KEPT_AS_IS; art = src
        }
        return Made(result, listOf(StickerStore.add(ctx, fit(art))))
    }

    private fun pixels(b: Bitmap): IntArray = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

    fun close() { runCatching { seg?.close() }; seg = null }

    // ---------------------------------------------------------------- helpers

    /** Reads the picture at most ~2048 px, turned the right way up. */
    private fun decode(uri: Uri): Bitmap? = try { decodeOrNull(uri) } catch (e: Exception) { null } catch (e: OutOfMemoryError) { null }

    private fun decodeOrNull(uri: Uri): Bitmap? {
        val r = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        r.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2048) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        var b = r.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val rot = runCatching {
            r.openInputStream(uri)!!.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrDefault(0f)
        if (rot != 0f) b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(rot) }, true)
        if (!b.isMutable || b.config != Bitmap.Config.ARGB_8888) b = b.copy(Bitmap.Config.ARGB_8888, true)
        return b
    }

    /** True when a real part of the picture is see-through (a ready-made sticker). */
    private fun hasTransparency(b: Bitmap): Boolean {
        if (!b.hasAlpha()) return false
        val w = b.width; val h = b.height
        val row = IntArray(w)
        var clear = 0L; var total = 0L
        var y = 0
        val step = max(1, h / 200)
        while (y < h) {
            b.getPixels(row, 0, w, 0, y, w, 1)
            var x = 0
            val xs = max(1, w / 200)
            while (x < w) { if ((row[x] ushr 24) < 200) clear++; total++; x += xs }
            y += step
        }
        return clear * 50 > total          // more than 2 %
    }

    /** Smallest box around the visible pixels, or null if nothing is visible. */
    private fun trimRect(b: Bitmap): Rect? {
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        var l = w; var t = h; var r = -1; var btm = -1
        for (y in 0 until h) {
            val o = y * w
            for (x in 0 until w) if ((px[o + x] ushr 24) > 12) {
                if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > btm) btm = y
            }
        }
        return if (r < 0) null else Rect(l, t, r + 1, btm + 1)
    }

    /** Thin white outline (like a thread) all around the shape, a faint shadow, then the picture on top. */
    private fun addBorder(fg: Bitmap): Bitmap {
        val box = trimRect(fg) ?: return fg
        val cut = Bitmap.createBitmap(fg, box.left, box.top, box.width(), box.height())
        val r = max(2f, max(cut.width, cut.height) * 0.007f)   // thin, thread-like outline
        val pad = (r * 1.6f).roundToInt()
        val out = Bitmap.createBitmap(cut.width + pad * 2, cut.height + pad * 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val shape = cut.extractAlpha()                 // alpha-only: drawn in the paint's colour
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(35, 0, 0, 0) }
        for (k in 0 until 24) {
            val a = (k * Math.PI * 2 / 24).toFloat()
            c.drawBitmap(shape, pad + cos(a) * r, pad + sin(a) * r + r * 0.45f, shadow)
        }
        for (rr in floatArrayOf(r * 0.5f, r)) for (k in 0 until 32) {      // two radii: no gaps
            val a = (k * Math.PI * 2 / 32).toFloat()
            c.drawBitmap(shape, pad + cos(a) * rr, pad + sin(a) * rr, white)
        }
        c.drawBitmap(cut, pad.toFloat(), pad.toFloat(), null)
        return out
    }

    /** Trimmed and centred in a 512×512 transparent square. */
    private fun fit(b: Bitmap): Bitmap {
        val box = trimRect(b) ?: Rect(0, 0, b.width, b.height)
        val s = StickerStore.SIZE
        val scale = (s - 8f) / max(box.width(), box.height())
        val w = (box.width() * scale).roundToInt().coerceAtLeast(1)
        val h = (box.height() * scale).roundToInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(b, box, Rect((s - w) / 2, (s - h) / 2, (s - w) / 2 + w, (s - h) / 2 + h),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
