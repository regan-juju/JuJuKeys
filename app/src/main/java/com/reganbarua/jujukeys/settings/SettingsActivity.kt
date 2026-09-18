package com.reganbarua.jujukeys.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.reganbarua.jujukeys.theme.JuJuKeysTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = PreferencesRepository(applicationContext)
        setContent {
            JuJuKeysTheme {
                SettingsScreen(preferences = preferences, onBack = { finish() })
            }
        }
    }
}
