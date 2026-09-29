package com.reganbarua.jujukeys

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Setup screen: turn JuJuKeys on, pick it, and try typing. */
class MainActivity : ComponentActivity() {

    private var enabled by mutableStateOf(false)
    private var selected by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF1A73E8))) {
                SetupScreen(
                    enabled = enabled,
                    selected = selected,
                    onEnable = { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                    onSelect = { imm().showInputMethodPicker() },
                )
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

    private fun imm() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    private fun refresh() {
        enabled = imm().enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        selected = current?.startsWith("$packageName/") == true
    }
}

@Composable
private fun SetupScreen(
    enabled: Boolean,
    selected: Boolean,
    onEnable: () -> Unit,
    onSelect: () -> Unit,
) {
    var test by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF11151B))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("JuJuKeys", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("বাংলা (অভ্র) ও ENGLISH কীবোর্ড", color = Color(0xFFB9BDC5), fontSize = 16.sp)

        StepCard(
            number = "১", title = "কীবোর্ড চালু করুন",
            body = "তালিকায় JuJuKeys চালু (ON) করুন। একটি সতর্কবার্তা আসবে — এটি সব কীবোর্ডের জন্য Android-এর সাধারণ বার্তা। JuJuKeys আপনার লেখা কোথাও জমা রাখে না বা পাঠায় না।",
            done = enabled, button = "সেটিংস খুলুন", onClick = onEnable
        )
        StepCard(
            number = "২", title = "JuJuKeys বেছে নিন",
            body = "তালিকা থেকে JuJuKeys নির্বাচন করুন।",
            done = selected, button = "কীবোর্ড বাছাই করুন", onClick = onSelect
        )

        Text("৩  লিখে দেখুন", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = test, onValueChange = { test = it },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            placeholder = { Text("এখানে টাইপ করুন…") }
        )

        GuideCard()
    }
}

@Composable
private fun StepCard(
    number: String, title: String, body: String,
    done: Boolean, button: String, onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E232B), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$number  $title", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            if (done) Text("✓ হয়েছে", color = Color(0xFF34A853), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Text(body, color = Color(0xFFB9BDC5), fontSize = 14.sp)
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8))
        ) { Text(button, color = Color.White) }
    }
}

@Composable
private fun GuideCard() {
    val rows = listOf(
        "ami" to "আমি", "bhalo" to "ভালো", "bangladesh" to "বাংলাদেশ",
        "t / T" to "ত / ট", "d / D" to "দ / ড", "n / N" to "ন / ণ",
        "sh / Sh" to "শ / ষ", "krom" to "ক্রম", "dhormo" to "ধর্ম",
        "krriShi" to "কৃষি", "ng" to "ং", "^" to "ঁ", ":" to "ঃ", "." to "।",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E232B), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("বাংলা লেখার নিয়ম (অভ্র)", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "গ্লোব বোতাম চাপলে বাংলা ⇄ ENGLISH বদলায়। বাংলায় Shift ছাড়া ছোট হাতের অক্ষর, Shift চেপে বড় হাতের অক্ষর (দুবার চাপলে লক)। ENGLISH সবসময় বড় হাতের।",
            color = Color(0xFFB9BDC5), fontSize = 14.sp
        )
        rows.forEach { (r, b) ->
            Row(Modifier.fillMaxWidth()) {
                Text(r, color = Color(0xFF8AB4F8), fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(b, color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
