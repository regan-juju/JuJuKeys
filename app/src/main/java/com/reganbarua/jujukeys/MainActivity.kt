package com.reganbarua.jujukeys

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reganbarua.jujukeys.settings.SettingsActivity
import com.reganbarua.jujukeys.theme.JuJuKeysTheme

/** Launcher screen: real, working instructions to enable and select JuJuKeys as the active keyboard. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JuJuKeysTheme {
                OnboardingScreen(
                    onOpenKeyboardSettings = {
                        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    },
                    onOpenJuJuKeysSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                )
            }
        }
    }
}

@Composable
private fun OnboardingScreen(onOpenKeyboardSettings: () -> Unit, onOpenJuJuKeysSettings: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.onboarding_step1_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(text = stringResource(R.string.onboarding_step1_body))
            Button(onClick = onOpenKeyboardSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.open_keyboard_settings))
            }

            Text(
                text = stringResource(R.string.onboarding_step2_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(text = stringResource(R.string.onboarding_step2_body))

            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleMedium
            )
            Button(onClick = onOpenJuJuKeysSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.open_jujukeys_settings))
            }
        }
    }
}
