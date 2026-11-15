package com.reganbarua.jujulauncher

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

data class AppInfo(val pkg: String, val cls: String, val label: String, val category: Int) {
    val key get() = "$pkg/$cls"
}

class LauncherState(val app: Application) {
    private val prefs = app.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var apps by mutableStateOf(listOf<AppInfo>()); private set
    var byKey by mutableStateOf(mapOf<String, AppInfo>()); private set
    var layout by mutableStateOf(LayoutState(listOf(emptyList()), emptyList())); private set
    var iconScale by mutableFloatStateOf(prefs.getFloat("iconScale", 1f)); private set
    var darkIcons by mutableStateOf(prefs.getBoolean("dark", true)); private set
    var showLabels by mutableStateOf(prefs.getBoolean("labels", true)); private set
    var wallpaper by mutableStateOf<ImageBitmap?>(null); private set
    var iconVersion by mutableIntStateOf(0); private set
    var homeSignal by mutableIntStateOf(0)
    var message by mutableStateOf<String?>(null)

    // set by MainActivity (file pickers)
    var pickWallpaper: () -> Unit = {}
    var pickIcons: () -> Unit = {}
    var pickIconFor: (String) -> Unit = {}

    init {
        Icons.dark = darkIcons
        loadWallpaper()
        loadLayout()
        refreshApps()
    }

    fun refreshApps() {
        val pm = app.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(i, 0)
            .filter { it.activityInfo.packageName != app.packageName }
            .map {
                AppInfo(it.activityInfo.packageName, it.activityInfo.name, it.loadLabel(pm).toString(),
                    if (android.os.Build.VERSION.SDK_INT >= 26) it.activityInfo.applicationInfo.category else -1)
            }
            .distinctBy { it.key }
            .sortedBy { it.label.lowercase() }
        apps = list
        byKey = list.associateBy { it.key }
        setLayout(Ops.reconcile(layout, list.map { it.key }, defaultDockKeys()), save = true)
    }

    private fun defaultDockKeys(): List<String> {
        val pm = app.packageManager
        fun first(intent: Intent): String? = pm.queryIntentActivities(intent, 0)
            .firstOrNull { it.activityInfo.packageName != app.packageName && it.activityInfo.exported }
            ?.activityInfo?.let { "${it.packageName}/${it.name}" }
        val pref = listOfNotNull(
            first(Intent(Intent.ACTION_DIAL)),
            first(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)),
            first(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)),
            first(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
            first(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)),
            first(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY)),
        )
        return pref.distinct().filter { it in byKey }.take(4)
    }

    fun setLayout(l: LayoutState, save: Boolean = true) {
        layout = Ops.normalize(l)
        if (save) saveLayout()
    }

    // ---- storage
    private fun saveLayout() {
        val pages = JSONArray()
        layout.pages.forEach { p ->
            val a = JSONArray()
            p.forEach {
                when (it) {
                    is AppItem -> a.put(JSONObject().put("t", "a").put("k", it.key))
                    is FolderItem -> a.put(JSONObject().put("t", "f").put("id", it.id).put("n", it.name).put("k", JSONArray(it.keys)))
                }
            }
            pages.put(a)
        }
        val o = JSONObject().put("pages", pages).put("dock", JSONArray(layout.dock)).put("hidden", JSONArray(layout.hidden.toList()))
        prefs.edit().putString("layout", o.toString()).apply()
    }

    private fun loadLayout() {
        val s = prefs.getString("layout", null) ?: return
        try {
            val o = JSONObject(s)
            val pages = (0 until o.getJSONArray("pages").length()).map { pi ->
                val a = o.getJSONArray("pages").getJSONArray(pi)
                (0 until a.length()).map { i ->
                    val e = a.getJSONObject(i)
                    if (e.getString("t") == "a") AppItem(e.getString("k")) as Item
                    else FolderItem(e.getString("id"), e.getString("n"), e.getJSONArray("k").let { k -> (0 until k.length()).map { k.getString(it) } })
                }
            }
            val dock = o.getJSONArray("dock").let { d -> (0 until d.length()).map { d.getString(it) } }
            val hidden = o.optJSONArray("hidden")?.let { d -> (0 until d.length()).map { d.getString(it) }.toSet() } ?: emptySet()
            layout = LayoutState(pages, dock, hidden)
        } catch (_: Exception) { /* bad data → start fresh */ }
    }

    fun resetLayout() {
        prefs.edit().remove("layout").apply()
        layout = LayoutState(listOf(emptyList()), emptyList())
        refreshApps()
    }

    fun updateIconScale(v: Float) { iconScale = v; prefs.edit().putFloat("iconScale", v).apply() }
    fun updateDark(v: Boolean) { darkIcons = v; Icons.dark = v; prefs.edit().putBoolean("dark", v).apply(); Icons.clear(); iconVersion = Icons.version }
    fun updateLabels(v: Boolean) { showLabels = v; prefs.edit().putBoolean("labels", v).apply() }

    // ---- wallpaper
    private fun wpFile() = File(app.filesDir, "wallpaper.jpg")

    private fun loadWallpaper() {
        val f = wpFile()
        if (!f.exists()) { wallpaper = null; return }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= 1500 && o.outHeight / (sample * 2) >= 2400) sample *= 2
        val b = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
        wallpaper = b?.asImageBitmap()
    }

    fun setWallpaperFrom(uri: Uri) {
        try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var sample = 1
            while (maxOf(o.outWidth, o.outHeight) / sample > 3600) sample *= 2
            val b = app.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return
            wpFile().outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 94, it) }
            loadWallpaper(); message = "Wallpaper set"
        } catch (e: Exception) { message = "Could not use that picture" }
    }

    fun resetWallpaper() { wpFile().delete(); wallpaper = null; message = "System wallpaper" }

    // ---- icons
    private fun displayName(uri: Uri): String {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0) ?: ""
        }
        return uri.lastPathSegment ?: ""
    }

    /** Files named <package>.png, or a .zip full of them. */
    fun importIcons(uris: List<Uri>) {
        var n = 0
        val dir = Icons.customDir(app)
        for (u in uris) {
            try {
                val name = displayName(u)
                if (name.endsWith(".zip", true)) {
                    ZipInputStream(app.contentResolver.openInputStream(u)!!).use { z ->
                        var e = z.nextEntry
                        while (e != null) {
                            val base = e.name.substringAfterLast('/')
                            if (!e.isDirectory && base.endsWith(".png", true) && !base.startsWith(".")) {
                                File(dir, base).outputStream().use { z.copyTo(it) }; n++
                            }
                            e = z.nextEntry
                        }
                    }
                } else if (name.endsWith(".png", true) || name.endsWith(".webp", true) || name.endsWith(".jpg", true)) {
                    app.contentResolver.openInputStream(u)!!.use { i ->
                        val b = BitmapFactory.decodeStream(i)
                        File(dir, name.substringBeforeLast('.') + ".png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }; n++
                }
            } catch (_: Exception) {}
        }
        Icons.clear(); iconVersion = Icons.version
        message = if (n > 0) "$n icon(s) added" else "No icon files found (name them <package>.png or lbl_<appname>.png)"
    }

    fun setIconFor(pkg: String, uri: Uri) {
        try {
            app.contentResolver.openInputStream(uri)!!.use { i ->
                val b = BitmapFactory.decodeStream(i)
                File(Icons.customDir(app), "$pkg.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            Icons.clear(); iconVersion = Icons.version; message = "Icon changed"
        } catch (_: Exception) { message = "Could not use that picture" }
    }

    fun resetIcons() {
        Icons.customDir(app).listFiles()?.forEach { it.delete() }
        Icons.clear(); iconVersion = Icons.version; message = "Icons reset"
    }

    fun customIconCount() = Icons.customDir(app).listFiles()?.size ?: 0

    // ---- actions
    fun launch(a: AppInfo) {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(ComponentName(a.pkg, a.cls)))
        } catch (_: Exception) { message = "Cannot open ${a.label}" }
    }

    fun uninstall(a: AppInfo) = startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")))
    fun appInfo(a: AppInfo) = startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))
    fun defaultHomeSettings() = startActivity(Intent(Settings.ACTION_HOME_SETTINGS))

    private fun startActivity(i: Intent) {
        try { app.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)) }
        catch (_: Exception) { message = "Cannot open" }
    }

    fun installTime(a: AppInfo): Long = try { app.packageManager.getPackageInfo(a.pkg, 0).firstInstallTime } catch (_: Exception) { 0L }

    fun categoryName(c: Int): String = when (c) {
        ApplicationInfo.CATEGORY_GAME -> "Games"
        ApplicationInfo.CATEGORY_AUDIO -> "Music"
        ApplicationInfo.CATEGORY_VIDEO -> "Entertainment"
        ApplicationInfo.CATEGORY_IMAGE -> "Photo & Video"
        ApplicationInfo.CATEGORY_SOCIAL -> "Social"
        ApplicationInfo.CATEGORY_NEWS -> "News"
        ApplicationInfo.CATEGORY_MAPS -> "Travel"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
        else -> "Utilities"
    }
}
