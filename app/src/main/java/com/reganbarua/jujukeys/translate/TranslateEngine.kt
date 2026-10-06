package com.reganbarua.jujukeys.translate

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.reganbarua.jujukeys.settings.Prefs
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * Translation from Bangla / English into many languages ([TranslateLangs]).
 *
 *  • Offline: Google ML Kit (the on-device engine behind Google Translate's offline mode).
 *    Each language's model (~30 MB) downloads once, then works with no internet.
 *  • Online:  Google Cloud Translation API, only if the user saved their own API key
 *    in the app. Falls back to offline if that fails.
 *
 * Only the text typed in the translate box is sent — nothing else.
 */
class TranslateEngine(private val context: Context) {

    data class Result(val text: String, val online: Boolean)

    private val main = Handler(Looper.getMainLooper())
    private val translators = HashMap<String, Translator>()

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Languages whose offline model is known to be on the phone (English is built in). */
    private val ready = java.util.Collections.synchronizedSet(HashSet<String>().apply { add("en") })

    private fun mlkit(code: String): String? = TranslateLanguage.fromLanguageTag(code)

    /** True when this pair can be translated on the phone at all (both languages in ML Kit). */
    fun offlinePossible(from: String, to: String) = mlkit(from) != null && mlkit(to) != null

    /** Calls back true when the offline models for [from] → [to] are already on the phone. */
    fun checkOfflineModel(from: String, to: String, callback: (Boolean) -> Unit) {
        val need = setOf(from, to) - "en"
        if (!offlinePossible(from, to)) { callback(false); return }
        if (ready.containsAll(need)) { callback(true); return }
        RemoteModelManager.getInstance()
            .getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                models.forEach { ready.add(it.language) }
                callback(ready.containsAll(need))
            }
            .addOnFailureListener { callback(false) }
    }

    /** Loads the translator into memory ahead of time so the first result comes fast. */
    fun warmUp(from: String, to: String) {
        checkOfflineModel(from, to) { ok -> if (ok) runCatching { translator(from, to).translate("a") } }
    }

    /** Downloads the offline models for [from] → [to] (needs internet once; ~30 MB per language). */
    fun downloadOfflineModel(from: String, to: String, onDone: (Boolean, String?) -> Unit) {
        if (!offlinePossible(from, to)) { onDone(false, "এই ভাষা শুধু অনলাইনে (API key দিয়ে)"); return }
        translator(from, to).downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { ready.add(from); ready.add(to); onDone(true, null) }
            .addOnFailureListener { onDone(false, it.message) }
    }

    /**
     * Translates [text] from [from] to [to] (language codes, e.g. "bn", "en", "de").
     * Online (own API key) first; if that fails, the phone's offline model.
     * [status] receives short progress messages in Bangla.
     */
    fun translate(
        text: String,
        from: String,
        to: String,
        status: (String) -> Unit,
        onResult: (Result?, String?) -> Unit,
    ) {
        val key = Prefs.cloudApiKey(context)
        if (key.isNotBlank() && isOnline()) {
            thread(name = "jujukeys-translate") {
                val r = runCatching { cloudTranslate(text, from, to, key) }
                main.post {
                    val value = r.getOrNull()
                    if (value != null) onResult(Result(value, true), null)
                    else if (offlinePossible(from, to)) offline(text, from, to, status, onResult)   // fall back
                    else onResult(null, "অনলাইন অনুবাদ হয়নি — আবার চেষ্টা করুন")
                }
            }
        } else if (offlinePossible(from, to)) {
            offline(text, from, to, status, onResult)
        } else {
            onResult(null, "এই ভাষা শুধু অনলাইনে — ইন্টারনেট ও API key লাগবে")
        }
    }

    private fun offline(
        text: String,
        from: String,
        to: String,
        status: (String) -> Unit,
        onResult: (Result?, String?) -> Unit,
    ) {
        val t = translator(from, to)
        val need = setOf(from, to) - "en"
        // Model already on the phone → translate straight away (no extra checks = faster).
        if (ready.containsAll(need)) {
            t.translate(text)
                .addOnSuccessListener { onResult(Result(it, false), null) }
                .addOnFailureListener { onResult(null, "অনুবাদ হয়নি: ${it.message}") }
            return
        }
        checkOfflineModel(from, to) { ok ->
            if (!ok) {
                if (!isOnline()) {
                    onResult(null, "অফলাইন মডেল নেই — একবার ইন্টারনেট চালু করে অনুবাদ করুন")
                    return@checkOfflineModel
                }
                status("অফলাইন মডেল নামানো হচ্ছে (একবারই, ~৩০MB)…")
            }
            t.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener {
                    ready.add(from); ready.add(to)
                    t.translate(text)
                        .addOnSuccessListener { onResult(Result(it, false), null) }
                        .addOnFailureListener { onResult(null, "অনুবাদ হয়নি: ${it.message}") }
                }
                .addOnFailureListener { onResult(null, "মডেল নামানো যায়নি: ${it.message}") }
        }
    }

    private fun translator(from: String, to: String): Translator =
        translators.getOrPut("$from>$to") {
            Translation.getClient(
                TranslatorOptions.Builder().setSourceLanguage(mlkit(from)!!).setTargetLanguage(mlkit(to)!!).build()
            )
        }

    private fun cloudTranslate(text: String, from: String, to: String, key: String): String? {
        // The key goes in a request header, not in the web address (addresses can end up in
        // error messages or logs).
        val url = URL("https://translation.googleapis.com/language/translate/v2")
        val body = "q=" + URLEncoder.encode(text, "UTF-8") +
            "&source=" + from +
            "&target=" + to +
            "&format=text"
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            conn.setRequestProperty("X-goog-api-key", key)
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != 200) return null
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            return HtmlText.unescape(
                JSONObject(json).getJSONObject("data").getJSONArray("translations")
                    .getJSONObject(0).getString("translatedText")
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Lets go of the translation models (memory) when the translate box is closed. */
    fun release() {
        translators.values.forEach { runCatching { it.close() } }
        translators.clear()
    }

    fun close() {
        translators.values.forEach { it.close() }
        translators.clear()
    }

    companion object {
        const val TRANSLATE_PACKAGE = "com.google.android.apps.translate"

        /** Opens the Google Translate app with [text]. */
        fun openTranslateApp(context: Context, text: String) {
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text)
                .setPackage(TRANSLATE_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(send)
            } catch (e: ActivityNotFoundException) {
                val web = Uri.parse("https://translate.google.com/?sl=auto&tl=en&text=" + Uri.encode(text))
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, web).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }
}
