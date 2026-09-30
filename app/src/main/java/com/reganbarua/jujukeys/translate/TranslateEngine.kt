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
 * Bangla ⇄ English translation.
 *
 *  • Offline: Google ML Kit (the on-device engine behind Google Translate's offline mode).
 *    The Bangla model (~30 MB) downloads once, then works with no internet.
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

    /** Remembered after the first check, so each translation does not ask again (faster). */
    @Volatile private var modelReady = false

    /** Calls back true when the Bangla offline model is already on the phone. */
    fun checkOfflineModel(callback: (Boolean) -> Unit) {
        if (modelReady) { callback(true); return }
        RemoteModelManager.getInstance()
            .getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                modelReady = models.any { it.language == TranslateLanguage.BENGALI }
                callback(modelReady)
            }
            .addOnFailureListener { callback(false) }
    }

    /** Loads both translators into memory ahead of time so the first result comes fast. */
    fun warmUp() {
        checkOfflineModel { ready ->
            if (!ready) return@checkOfflineModel
            translator(TranslateLanguage.BENGALI, TranslateLanguage.ENGLISH).translate("আমি")
            translator(TranslateLanguage.ENGLISH, TranslateLanguage.BENGALI).translate("hi")
        }
    }

    /** Downloads the offline model (needs internet once). */
    fun downloadOfflineModel(onDone: (Boolean, String?) -> Unit) {
        val t = translator(TranslateLanguage.BENGALI, TranslateLanguage.ENGLISH)
        t.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { modelReady = true; onDone(true, null) }
            .addOnFailureListener { onDone(false, it.message) }
    }

    /**
     * Translates [text] from Bangla to English ([toEnglish] = true) or the other way.
     * [status] receives short progress messages in Bangla.
     */
    fun translate(
        text: String,
        toEnglish: Boolean,
        status: (String) -> Unit,
        onResult: (Result?, String?) -> Unit,
    ) {
        val key = Prefs.cloudApiKey(context)
        if (key.isNotBlank() && isOnline()) {
            thread(name = "jujukeys-translate") {
                val r = runCatching { cloudTranslate(text, toEnglish, key) }
                main.post {
                    val value = r.getOrNull()
                    if (value != null) onResult(Result(value, true), null)
                    else offline(text, toEnglish, status, onResult)   // fall back
                }
            }
        } else {
            offline(text, toEnglish, status, onResult)
        }
    }

    private fun offline(
        text: String,
        toEnglish: Boolean,
        status: (String) -> Unit,
        onResult: (Result?, String?) -> Unit,
    ) {
        val t = if (toEnglish) translator(TranslateLanguage.BENGALI, TranslateLanguage.ENGLISH)
        else translator(TranslateLanguage.ENGLISH, TranslateLanguage.BENGALI)
        checkOfflineModel { ready ->
            if (!ready) {
                if (!isOnline()) {
                    onResult(null, "অফলাইন মডেল নেই — একবার ইন্টারনেট চালু করে অনুবাদ করুন")
                    return@checkOfflineModel
                }
                status("অফলাইন মডেল নামানো হচ্ছে (একবারই, ~৩০MB)…")
            }
            t.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener {
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
                TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build()
            )
        }

    private fun cloudTranslate(text: String, toEnglish: Boolean, key: String): String? {
        val url = URL("https://translation.googleapis.com/language/translate/v2?key=" + URLEncoder.encode(key, "UTF-8"))
        val body = "q=" + URLEncoder.encode(text, "UTF-8") +
            "&source=" + (if (toEnglish) "bn" else "en") +
            "&target=" + (if (toEnglish) "en" else "bn") +
            "&format=text"
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != 200) return null
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(json).getJSONObject("data").getJSONArray("translations")
                .getJSONObject(0).getString("translatedText")
        } finally {
            conn.disconnect()
        }
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
