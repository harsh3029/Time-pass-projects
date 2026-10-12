package com.timepass.anytranslate.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Thin wrapper around [SpeechRecognizer] that delivers partial and final results for one utterance.
 * Must be used from the main thread. A fresh recognizer is created per utterance, which avoids
 * stale callbacks and "recognizer busy" errors on many devices.
 */
class SpeechInput(private val context: Context) {

    interface Listener {
        fun onPartial(text: String) {}
        /** Microphone level in 0..1, handy for animating the mic button. */
        fun onLevel(level: Float) {}
        fun onFinal(text: String)
        fun onError(code: Int)
    }

    private var recognizer: SpeechRecognizer? = null
    private var session = 0

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(languageTag: String, listener: Listener) {
        release()
        val id = ++session
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            private var finished = false
            private fun live() = id == session && !finished

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onRmsChanged(rmsdB: Float) {
                if (live()) listener.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!live()) return
                val text = partialResults.firstResult()
                if (text.isNotBlank()) listener.onPartial(text)
            }

            override fun onResults(results: Bundle?) {
                if (!live()) return
                finished = true
                listener.onFinal(results.firstResult())
            }

            override fun onError(error: Int) {
                if (!live()) return
                finished = true
                listener.onError(error)
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        r.startListening(intent)
    }

    /** Stops listening; the final result for what was said so far is still delivered. */
    fun stop() {
        recognizer?.stopListening()
    }

    /** Stops listening and drops any pending result. */
    fun cancel() {
        session++
        release()
    }

    private fun release() {
        recognizer?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
    }

    private fun Bundle?.firstResult(): String =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    companion object {
        fun errorMessage(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that — try again."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Speech recognition needs a connection for this language."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed."
            SpeechRecognizer.ERROR_AUDIO -> "Couldn't access the microphone."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Try again in a moment."
            12, 13 -> "Speech recognition doesn't support this language on your phone yet."
            else -> "Speech recognition error ($code)."
        }
    }
}
