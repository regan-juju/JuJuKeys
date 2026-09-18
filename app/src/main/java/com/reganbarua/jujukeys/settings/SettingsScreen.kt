package com.reganbarua.jujukeys.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(preferences: PreferencesRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    val keypressSound by preferences.keypressSound.collectAsState(initial = true)
    val hapticFeedback by preferences.hapticFeedback.collectAsState(initial = true)
    val keyPopup by preferences.keyPopup.collectAsState(initial = true)
    val suggestionsEnabled by preferences.suggestionsEnabled.collectAsState(initial = true)
    val autocorrectEnabled by preferences.autocorrectEnabled.collectAsState(initial = true)
    val emojiSuggestions by preferences.emojiSuggestionsEnabled.collectAsState(initial = true)
    val heightScale by preferences.keyboardHeightScale.collectAsState(initial = 1.0f)
    val keySizeScale by preferences.keySizeScale.collectAsState(initial = 1.0f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("JuJuKeys Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { SectionHeader("Languages") }
            item { InfoRow("Bengali", "Avro-style offline phonetic typing") }
            item { InfoRow("English", "QWERTY, always uppercase output") }
            item { InfoRow("Switch languages", "Tap the globe key on the keyboard") }
            item { Divider() }

            item { SectionHeader("Keyboard preferences") }
            item {
                SwitchRow("Keypress sound", keypressSound) {
                    scope.launch { preferences.setKeypressSound(it) }
                }
            }
            item {
                SwitchRow("Haptic feedback", hapticFeedback) {
                    scope.launch { preferences.setHapticFeedback(it) }
                }
            }
            item {
                SwitchRow("Key popup preview", keyPopup) {
                    scope.launch { preferences.setKeyPopup(it) }
                }
            }
            item {
                SwitchRow("Suggestion strip", suggestionsEnabled) {
                    scope.launch { preferences.setSuggestionsEnabled(it) }
                }
            }
            item {
                SwitchRow("Emoji suggestions", emojiSuggestions) {
                    scope.launch { preferences.setEmojiSuggestionsEnabled(it) }
                }
            }
            item {
                SliderRow("Keyboard height", heightScale, 0.85f..1.2f) {
                    scope.launch { preferences.setKeyboardHeightScale(it) }
                }
            }
            item { Divider() }

            item { SectionHeader("Text correction") }
            item {
                SwitchRow("Autocorrection (English)", autocorrectEnabled) {
                    scope.launch { preferences.setAutocorrectEnabled(it) }
                }
            }
            item { InfoRow("Personal dictionary", "Extend EnglishDictionary.kt / BengaliDictionary.kt") }
            item { Divider() }

            item { SectionHeader("Appearance") }
            item { InfoRow("Theme", "Dark, iPhone-style (matches app design)") }
            item {
                SliderRow("Key size", keySizeScale, 0.85f..1.2f) {
                    scope.launch { preferences.setKeySizeScale(it) }
                }
            }
            item { Divider() }

            item { SectionHeader("Translation") }
            item { InfoRow("Offline dictionary", "Always available, common Bengali/English words") }
            item { InfoRow("Online translation", "Not configured — no API key is bundled with the app") }
            item { Divider() }

            item { SectionHeader("Privacy") }
            item { InfoRow("Keystrokes", "Never logged or uploaded") }
            item { InfoRow("Passwords", "Never collected, suggested, or stored") }
            item { InfoRow("Microphone", "Only records after you tap the mic button; nothing is saved") }
            item { InfoRow("Translation", "Only sent when you tap Translate; nothing is logged") }
            item { Divider() }

            item { SectionHeader("About") }
            item { InfoRow("JuJuKeys", "Version 1.0.0") }
            item { InfoRow("Built with", "Kotlin, Jetpack Compose, Android InputMethodService") }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
private fun InfoRow(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(text = "$title (${"%.0f".format(value * 100)}%)", style = MaterialTheme.typography.bodyLarge)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
