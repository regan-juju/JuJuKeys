package com.reganbarua.jujukeys

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.reganbarua.jujukeys.security.DataMigration
import com.reganbarua.jujukeys.settings.SettingsApp

/** The app = Gboard-style settings (opened from the launcher or the keyboard's 🌐 button). */
class MainActivity : ComponentActivity() {

    private var enabled by mutableStateOf(false)
    private var selected by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread { DataMigration.run(applicationContext) }.start()   // old plain data → encrypted
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFA8C7FA),
                    surface = Color(0xFF2B2B2B),
                    background = Color(0xFF1B1B1B),
                )
            ) {
                SettingsApp(enabled = enabled, selected = selected, onClose = { finish() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refresh()   // the keyboard picker dialog just closed
    }

    private fun refresh() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        selected = current?.startsWith("$packageName/") == true
    }
}
