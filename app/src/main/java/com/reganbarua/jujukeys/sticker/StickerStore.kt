package com.reganbarua.jujukeys.sticker

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import androidx.core.content.FileProvider
import java.io.File

/**
 * The user's stickers: 512×512 WebP files in the app's private storage (files/stickers/).
 * No limit on how many — only the phone's free space. The 18 starting stickers are copied
 * from assets/stickers once; after that they are ordinary stickers (can be deleted).
 * Thumbnails are decoded only for the stickers on screen.
 */
object StickerStore {
    const val SIZE = 512
    private const val DIR = "stickers"
    private const val ORDER = "order.txt"
    private const val OUT = "sticker_out"
    private const val SEEDED = "stickers_seeded_v1"

    private val lock = Any()
    private val thumbs = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun dir(ctx: Context): File = File(ctx.filesDir, DIR).apply { mkdirs() }
    fun file(ctx: Context, name: String): File = File(dir(ctx), name)
    fun authority(ctx: Context) = ctx.packageName + ".stickers"

    /** Copies the starting stickers the first time (never again, so deleted ones stay deleted). */
    fun seed(ctx: Context) = synchronized(lock) {
        val sp = ctx.getSharedPreferences("jujukeys_stickers", Context.MODE_PRIVATE)
        if (sp.getBoolean(SEEDED, false)) return
        val names = runCatching { ctx.assets.list(DIR)?.filter { it.endsWith(".webp") }?.sorted() }.getOrNull().orEmpty()
        val added = ArrayList<String>()
        for (n in names) runCatching {
            val out = file(ctx, "b_$n")
            if (!out.exists()) ctx.assets.open("$DIR/$n").use { i -> out.outputStream().use { i.copyTo(it) } }
            added += out.name
        }
        // starting stickers go AFTER any the user already made
        writeOrder(ctx, readOrder(ctx).filter { it !in added } + added)
        sp.edit().putBoolean(SEEDED, true).apply()
    }

    fun list(ctx: Context): List<String> = synchronized(lock) {
        val files = dir(ctx).listFiles { f -> f.isFile && f.name.endsWith(".webp") }.orEmpty()
            .associate { it.name to it.lastModified() }
        val order = StickerOrder.reconcile(readOrder(ctx), files)
        if (order != readOrder(ctx)) writeOrder(ctx, order)
        order
    }

    /** Saves a finished 512×512 sticker; it goes to the front. Returns its name. */
    fun add(ctx: Context, bmp: Bitmap): String = synchronized(lock) {
        var name: String
        var n = 0
        do { name = "u_${System.currentTimeMillis()}_${n++}.webp" } while (file(ctx, name).exists())
        val tmp = File(dir(ctx), "$name.tmp")
        tmp.outputStream().use { bmp.compress(webpFormat(), 86, it) }
        if (!tmp.renameTo(file(ctx, name))) { tmp.delete(); error("could not save sticker") }
        writeOrder(ctx, StickerOrder.addFront(readOrder(ctx), listOf(name)))
        name
    }

    fun moveTop(ctx: Context, name: String) = synchronized(lock) { writeOrder(ctx, StickerOrder.moveTop(list(ctx), name)) }

    fun delete(ctx: Context, name: String) = synchronized(lock) {
        file(ctx, name).delete()
        thumbs.remove(name)
        writeOrder(ctx, StickerOrder.remove(readOrder(ctx), name))
    }

    fun count(ctx: Context): Int = list(ctx).size

    /** Small picture for the keyboard grid (decoded at half size, cached). */
    fun thumb(ctx: Context, name: String): Bitmap? {
        thumbs.get(name)?.let { return it }
        val f = file(ctx, name)
        if (!f.exists()) return null
        val b = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 2 }) ?: return null
        thumbs.put(name, b)
        return b
    }

    /** content:// link to the sticker itself (WebP) for apps that take keyboard images. */
    fun webpUri(ctx: Context, name: String): Uri = FileProvider.getUriForFile(ctx, authority(ctx), file(ctx, name))

    /** PNG copy (transparent) for apps that do not take WebP, for sharing and the gallery. */
    fun pngFile(ctx: Context, name: String): File? {
        val outDir = File(ctx.cacheDir, OUT).apply { mkdirs() }
        val out = File(outDir, name.removeSuffix(".webp") + ".png")
        if (out.exists() && out.length() > 0) return out
        val b = BitmapFactory.decodeFile(file(ctx, name).path) ?: return null
        out.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return out
    }

    fun pngUri(ctx: Context, name: String): Uri? = pngFile(ctx, name)?.let { FileProvider.getUriForFile(ctx, authority(ctx), it) }

    /** Saves a PNG into the phone's gallery: Pictures/JuJuKeys Stickers. Android 10+ only. */
    fun saveToGallery(ctx: Context, name: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val png = pngFile(ctx, name) ?: return false
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "JuJuKeys_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/JuJuKeys Stickers")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val r = ctx.contentResolver
        val uri = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            r.openOutputStream(uri)!!.use { o -> png.inputStream().use { it.copyTo(o) } }
            r.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            true
        }.getOrElse { r.delete(uri, null, null); false }
    }

    @Suppress("DEPRECATION")
    private fun webpFormat() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    private fun readOrder(ctx: Context): List<String> =
        runCatching { StickerOrder.parse(File(dir(ctx), ORDER).readText()) }.getOrDefault(emptyList())

    private fun writeOrder(ctx: Context, order: List<String>) {
        val f = File(dir(ctx), ORDER)
        val tmp = File(dir(ctx), "$ORDER.tmp")
        tmp.writeText(StickerOrder.format(order))
        if (!tmp.renameTo(f)) { f.writeText(StickerOrder.format(order)); tmp.delete() }
    }
}
