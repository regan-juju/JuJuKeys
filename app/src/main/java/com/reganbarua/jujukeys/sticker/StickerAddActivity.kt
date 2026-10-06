package com.reganbarua.jujukeys.sticker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "স্টিকার যোগ করুন": pick any number of pictures from the gallery (or share them to JuJuKeys
 * from the gallery). Each becomes a sticker on the phone — see [StickerMaker].
 */
class StickerAddActivity : ComponentActivity() {

    private var cutOut by mutableStateOf(true)
    private var border by mutableStateOf(true)
    private var split by mutableStateOf(true)
    private var busy by mutableStateOf(false)
    private var done by mutableIntStateOf(0)
    private var total by mutableIntStateOf(0)
    private var status by mutableStateOf("")
    private var summary by mutableStateOf("")
    private var count by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        cutOut = sp.getBoolean(KEY_CUT, true)
        border = sp.getBoolean(KEY_BORDER, true)
        split = sp.getBoolean(KEY_SPLIT, true)
        Thread { StickerStore.seed(this); val n = StickerStore.count(this); runOnUiThread { count = n } }.start()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Color(0xFF2B2B2B))) {
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
                    if (uris.isNotEmpty()) process(uris)
                }
                Screen(onPick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
            }
        }
        sharedUris(intent).takeIf { it.isNotEmpty() }?.let { process(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedUris(intent).takeIf { it.isNotEmpty() }?.let { process(it) }
    }

    @Suppress("DEPRECATION")
    private fun sharedUris(i: Intent?): List<Uri> = when (i?.action) {
        Intent.ACTION_SEND -> listOfNotNull(
            if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra(Intent.EXTRA_STREAM)
        )
        Intent.ACTION_SEND_MULTIPLE -> (
            if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        ).orEmpty()
        else -> emptyList()
    }

    private fun process(uris: List<Uri>) {
        if (busy) return
        busy = true; done = 0; total = uris.size; summary = ""
        status = "Starting…"
        val wantCut = cutOut; val wantBorder = border; val wantSplit = split
        Thread {
            val maker = StickerMaker(applicationContext)
            var cut = 0; var kept = 0; var failed = 0; var bad = 0; var sheets = 0; var fromSheets = 0
            uris.forEachIndexed { i, u ->
                runOnUiThread { status = "Making ${i + 1} / ${uris.size}…" + if (wantCut && maker.modelOk == null) " (the first time, the background-removal part may download)" else "" }
                val made = runCatching { maker.make(u, wantCut, wantBorder, wantSplit) }
                    .getOrElse { StickerMaker.Made(StickerMaker.Result.BAD_IMAGE, emptyList()) }
                when (made.result) {
                    StickerMaker.Result.CUT_OUT -> cut++
                    StickerMaker.Result.SPLIT -> { sheets++; fromSheets += made.names.size }
                    StickerMaker.Result.KEPT_TRANSPARENT, StickerMaker.Result.KEPT_AS_IS -> kept++
                    StickerMaker.Result.CUT_FAILED -> failed++
                    StickerMaker.Result.BAD_IMAGE -> bad++
                }
                runOnUiThread { done = i + 1 }
            }
            val modelFailed = wantCut && maker.modelOk == false
            maker.close()
            val n = StickerStore.count(applicationContext)
            val made = cut + kept + failed + fromSheets
            val parts = buildList {
                add("$made stickers added")
                if (sheets > 0) add("$sheets sticker sheet(s) split into $fromSheets separate stickers")
                if (cut > 0) add("Background removed from $cut")
                if (failed > 0) add("$failed could not have the background removed — the original picture was kept (hold it in the keyboard to delete)")
                if (modelFailed) add("The background-removal part could not be downloaded — turn on the internet and try again")
                if (bad > 0) add("$bad picture(s) could not be opened")
            }
            runOnUiThread { busy = false; status = ""; summary = parts.joinToString("\n• ", prefix = "• "); count = n }
        }.start()
    }

    @Composable
    private fun Screen(onPick: () -> Unit) {
        Column(
            Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Add stickers", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("You have $count stickers · add as many as you like", color = Sub, fontSize = 14.sp)
            Toggle("Remove background", "Cuts the people / main subject out of an ordinary photo (Google ML Kit, on the phone)", cutOut) {
                cutOut = it; save(KEY_CUT, it)
            }
            Toggle("White border", "A sticker-style white outline and soft shadow around the cut-out", border) {
                border = it; save(KEY_BORDER, it)
            }
            Toggle("Split sticker sheets", "A picture with many stickers (e.g. 3×3) becomes separate stickers — black or see-through background", split) {
                split = it; save(KEY_SPLIT, it)
            }
            Text(
                "Ready-made stickers (see-through PNG, or on a black background) are kept as they are — the black goes, nothing else is cut or bordered.",
                color = Sub, fontSize = 13.sp
            )
            Button(
                onClick = onPick, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) { Text("Choose pictures from the gallery", color = Color(0xFF062E6F), fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            Text("You can pick many at once. You can also Share pictures from the gallery to JuJuKeys.", color = Sub, fontSize = 13.sp)
            if (busy) {
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else done / total.toFloat() },
                    modifier = Modifier.fillMaxWidth(), color = Accent
                )
                Text(status, color = Color.White, fontSize = 15.sp)
            }
            if (summary.isNotEmpty()) {
                Text(summary, color = Color.White, fontSize = 15.sp, lineHeight = 22.sp)
                OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Done — back to the keyboard", color = Accent, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "• Everything happens on the phone; pictures are never sent anywhere.\n" +
                    "• In the keyboard: tap the photo key → your stickers. Tap to send; hold to share / save / move to top / delete.\n" +
                    "• Stickers are not in Google backups (so the 25 MB backup limit is not used up).",
                color = Sub, fontSize = 13.sp, lineHeight = 20.sp
            )
        }
    }

    @Composable
    private fun Toggle(title: String, sub: String, on: Boolean, set: (Boolean) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, color = Color.White, fontSize = 17.sp)
                Text(sub, color = Sub, fontSize = 13.sp)
            }
            Switch(checked = on, onCheckedChange = set, enabled = !busy)
        }
    }

    private fun save(key: String, v: Boolean) =
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, v).apply()

    companion object {
        const val PREFS = "jujukeys_stickers"
        const val KEY_CUT = "cut_out"
        const val KEY_BORDER = "border"
        const val KEY_SPLIT = "split_sheet"
        private val Accent = Color(0xFFA8C7FA)
        private val Bg = Color(0xFF1B1B1B)
        private val Sub = Color(0xFFB0B0B0)

        fun start(ctx: Context) = ctx.startActivity(
            Intent(ctx, StickerAddActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

        fun bn(n: Int): String = n.toString().map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("")
    }
}
