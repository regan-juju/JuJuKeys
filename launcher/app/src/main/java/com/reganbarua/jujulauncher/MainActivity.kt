package com.reganbarua.jujulauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private lateinit var st: LauncherState
    private var iconTarget: String? = null

    private val wallpaperPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) st.setWallpaperFrom(u) }
    private val iconsPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { l -> if (l.isNotEmpty()) st.importIcons(l) }
    private val oneIconPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { u ->
        val p = iconTarget; if (u != null && p != null) st.setIconFor(p, u)
    }

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { st.refreshApps() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false
        }
        st = LauncherState(application)
        st.pickWallpaper = { wallpaperPicker.launch("image/*") }
        st.pickIcons = { iconsPicker.launch(arrayOf("image/*", "application/zip", "application/x-zip-compressed", "application/octet-stream")) }
        st.pickIconFor = { pkg -> iconTarget = pkg; oneIconPicker.launch("image/*") }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED); addDataScheme("package")
        }
        androidx.core.content.ContextCompat.registerReceiver(this, pkgReceiver, f, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        setContent { LauncherRoot(st) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) st.homeSignal++
    }

    override fun onDestroy() {
        unregisterReceiver(pkgReceiver)
        super.onDestroy()
    }
}
