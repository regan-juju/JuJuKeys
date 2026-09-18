package com.reganbarua.jujukeys.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.reganbarua.jujukeys.keyboard.KeyboardLanguage

sealed class VoiceState {
    object Idle : VoiceState()
    object Listening : VoiceState()
    data class Result(val text: String) : VoiceState()
    data class Error(val message: String) : VoiceState()
}

/**
 * Wraps Android's on-device SpeechRecognizer. Listening only ever starts from an explicit
 * call to [start] (i.e. the user tapping the mic button) and always stops on [cancel] or
 * when a result/error arrives. Nothing is recorded to disk; audio goes straight to the
 * platform recognizer, same as it would for any other app using this API.
 */
class VoiceInputHelper(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(language: KeyboardLanguage, onState: (VoiceState) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onState(VoiceState.Error("unavailable"))
            return
        }
        cancel()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r

        val locale = when (language) {
            KeyboardLanguage.BENGALI -> "bn-BD"
            KeyboardLanguage.ENGLISH -> "en-US"
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onState(VoiceState.Listening)
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (text != null) onState(VoiceState.Result(text)) else onState(VoiceState.Error("no_match"))
                stopInternal()
            }

            override fun onError(error: Int) {
                onState(VoiceState.Error("recognizer_error_$error"))
                stopInternal()
            }

            override fun onEndOfSpeech() { /* no-op, onResults/onError follow */ }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        r.startListening(intent)
    }

    fun cancel() {
        recognizer?.let {
            runCatching { it.stopListening() }
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
    }

    private fun stopInternal() {
        recognizer?.let { runCatching { it.destroy() } }
        recognizer = null
    }
}
