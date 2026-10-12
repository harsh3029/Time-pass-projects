package com.timepass.anytranslate.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.timepass.anytranslate.data.Languages
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Text-to-speech in any language the phone has a voice for. */
class Speaker(context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val doneCallbacks = ConcurrentHashMap<String, () -> Unit>()
    private var ready = false
    private var pending: (() -> Unit)? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post {
            ready = status == TextToSpeech.SUCCESS
            if (ready) pending?.invoke()
            pending = null
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finish(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
        })
    }

    private fun finish(utteranceId: String?) {
        val callback = utteranceId?.let { doneCallbacks.remove(it) } ?: return
        main.post(callback)
    }

    /**
     * Speaks [text] in [languageCode]. Returns false when the phone has no voice for that language.
     * [onDone] runs on the main thread once speaking ends (or immediately if nothing is spoken).
     */
    fun speak(text: String, languageCode: String, onDone: (() -> Unit)? = null): Boolean {
        if (text.isBlank()) {
            onDone?.invoke()
            return true
        }
        if (!ready) {
            pending = { speakNow(text, languageCode, onDone) }
            return true
        }
        return speakNow(text, languageCode, onDone)
    }

    private fun speakNow(text: String, languageCode: String, onDone: (() -> Unit)?): Boolean {
        var result = tts.setLanguage(Languages.locale(languageCode))
        if (result < TextToSpeech.LANG_AVAILABLE) result = tts.setLanguage(Locale.forLanguageTag(languageCode))
        if (result < TextToSpeech.LANG_AVAILABLE) {
            onDone?.invoke()
            return false
        }
        val id = UUID.randomUUID().toString()
        if (onDone != null) doneCallbacks[id] = onDone
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        return true
    }

    fun stop() {
        pending = null
        tts.stop()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }
}
