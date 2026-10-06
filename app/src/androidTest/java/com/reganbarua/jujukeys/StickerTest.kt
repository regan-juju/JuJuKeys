package com.reganbarua.jujukeys

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reganbarua.jujukeys.sticker.StickerMaker
import com.reganbarua.jujukeys.sticker.StickerStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Stickers on a real Android: starting set, add / order / delete, PNG export, ready-made PNG input. */
@RunWith(AndroidJUnit4::class)
class StickerTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before fun clean() {
        StickerStore.dir(ctx).deleteRecursively()
        ctx.getSharedPreferences("jujukeys_stickers", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun dot(color: Int): Bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
        Canvas(this).drawCircle(256f, 256f, 200f, Paint().apply { this.color = color })
    }

    @Test fun addOrderDelete() {
        StickerStore.seed(ctx)                                                 // no bundled pictures: nothing, no crash
        val base = StickerStore.count(ctx)
        val a = StickerStore.add(ctx, dot(Color.RED))
        val b = StickerStore.add(ctx, dot(Color.BLUE))
        val c = StickerStore.add(ctx, dot(Color.CYAN))
        assertEquals(listOf(c, b, a), StickerStore.list(ctx).take(3))         // newest first
        StickerStore.moveTop(ctx, a)
        assertEquals(listOf(a, c, b), StickerStore.list(ctx).take(3))
        StickerStore.delete(ctx, c)
        assertFalse(StickerStore.list(ctx).contains(c))
        assertFalse(StickerStore.file(ctx, c).exists())
        assertEquals(base + 2, StickerStore.count(ctx))
    }

    /** A 3×3 sheet on black (like a JPG) → 9 stickers, newest first in the keyboard. */
    @Test fun darkStickerSheetIsSplit() {
        val sheet = Bitmap.createBitmap(330, 330, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet); c.drawColor(Color.BLACK)
        for (gy in 0 until 3) for (gx in 0 until 3) {
            val cx = gx * 110f + 55f; val cy = gy * 110f + 55f
            c.drawCircle(cx, cy, 47f, Paint().apply { color = Color.WHITE })
            c.drawCircle(cx, cy, 40f, Paint().apply { color = Color.rgb(220, 50, 50) })
        }
        val f = File(ctx.cacheDir, "sheet.jpg")
        f.outputStream().use { sheet.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        val made = StickerMaker(ctx).make(Uri.fromFile(f), cutOut = false, border = false, split = true)
        assertEquals(StickerMaker.Result.SPLIT, made.result)
        assertEquals(9, made.names.size)
        val one = BitmapFactory.decodeFile(StickerStore.file(ctx, made.names[0]).path)
        assertEquals(0, Color.alpha(one.getPixel(1, 1)))                      // black became see-through
    }

    @Test fun pngExportKeepsTransparencyAndUriWorks() {
        val n = StickerStore.add(ctx, dot(Color.GREEN))
        val png = StickerStore.pngFile(ctx, n)!!
        val b = BitmapFactory.decodeFile(png.path)
        assertEquals(512, b.width)
        assertEquals(0, Color.alpha(b.getPixel(2, 2)))                        // corner see-through
        assertTrue(Color.alpha(b.getPixel(256, 256)) > 250)
        val uri = StickerStore.webpUri(ctx, n)
        assertEquals("content", uri.scheme)
        assertNotNull(ctx.contentResolver.openInputStream(uri)?.use { it.read() })
    }

    @Test fun readyMadeTransparentPngIsKeptAsIs() {
        val f = File(ctx.cacheDir, "in.png")
        f.outputStream().use { dot(Color.YELLOW).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val made = StickerMaker(ctx).make(Uri.fromFile(f), cutOut = true, border = true, split = true)
        assertEquals(StickerMaker.Result.KEPT_TRANSPARENT, made.result)
        assertEquals(1, made.names.size)
        val name = made.names[0]
        assertEquals(name, StickerStore.list(ctx).first())
        val out = BitmapFactory.decodeFile(StickerStore.file(ctx, name).path)
        assertEquals(512, out.width); assertEquals(512, out.height)
    }
}
