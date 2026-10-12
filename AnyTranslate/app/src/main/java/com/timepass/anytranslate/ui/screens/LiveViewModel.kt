package com.timepass.anytranslate.ui.screens

import android.app.Application
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.timepass.anytranslate.data.AppPrefs
import com.timepass.anytranslate.speech.SpeechInput
import com.timepass.anytranslate.speech.Speaker
import com.timepass.anytranslate.translate.TranslationEngine
import com.timepass.anytranslate.data.Languages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LiveMode { CONVERSATION, SUBTITLES }

/** A = the phone owner (bottom of the screen), B = the person they're talking to. */
enum class Side { A, B }

data class Utterance(
    val id: Long,
    val side: Side,
    val original: String,
    val translated: String?,
    val from: String,
    val to: String,
)

data class LiveState(
    val mode: LiveMode = LiveMode.CONVERSATION,
    val listening: Side? = null,
    val subtitlesOn: Boolean = false,
    val partial: String = "",
    val partialTranslation: String = "",
    val level: Float = 0f,
    val messages: List<Utterance> = emptyList(),
    val error: String? = null,
)

class LiveViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = AppPrefs.get(app)
    private val engine = TranslationEngine.get()
    private val speech = SpeechInput(app)
    private val speaker = Speaker(app)

    val langA: StateFlow<String> = prefs.liveLangA.flow
    val langB: StateFlow<String> = prefs.liveLangB.flow
    val autoSpeak: StateFlow<Boolean> = prefs.autoSpeak.flow
    val faceToFace: StateFlow<Boolean> = prefs.faceToFace.flow
    val speechAvailable: Boolean = speech.isAvailable

    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state.asStateFlow()

    private var nextId = 0L
    private var partialJob: Job? = null
    private var restartJob: Job? = null
    private var subtitleErrors = 0

    fun setMode(mode: LiveMode) {
        if (mode == _state.value.mode) return
        stopAll()
        _state.update { LiveState(mode = mode) }
    }

    fun setLangA(code: String) {
        stopAll()
        prefs.liveLangA.value = code
    }

    fun setLangB(code: String) {
        stopAll()
        prefs.liveLangB.value = code
    }

    fun swapLanguages() {
        stopAll()
        val a = langA.value
        prefs.liveLangA.value = langB.value
        prefs.liveLangB.value = a
    }

    fun setAutoSpeak(enabled: Boolean) {
        prefs.autoSpeak.value = enabled
        if (!enabled) speaker.stop()
    }

    fun setFaceToFace(enabled: Boolean) {
        prefs.faceToFace.value = enabled
    }

    fun clear() {
        _state.update { it.copy(messages = emptyList(), error = null) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    /** Conversation mode: start listening for [side], or finish their sentence if they're already talking. */
    fun toggleSide(side: Side) {
        when (_state.value.listening) {
            side -> speech.stop()
            else -> {
                speaker.stop()
                listen(side, continuous = false)
            }
        }
    }

    /** Subtitle mode: keep listening to language A and show running translations in language B. */
    fun toggleSubtitles() {
        if (_state.value.subtitlesOn) {
            stopAll()
        } else {
            subtitleErrors = 0
            speaker.stop()
            _state.update { it.copy(subtitlesOn = true, error = null) }
            listen(Side.A, continuous = true)
        }
    }

    fun speak(utterance: Utterance): Boolean {
        val text = utterance.translated ?: return true
        return speaker.speak(text, utterance.to)
    }

    fun stopAll() {
        restartJob?.cancel()
        partialJob?.cancel()
        speech.cancel()
        speaker.stop()
        _state.update { it.copy(listening = null, subtitlesOn = false, partial = "", partialTranslation = "", level = 0f) }
    }

    private fun languageOf(side: Side) = if (side == Side.A) langA.value else langB.value

    private fun listen(side: Side, continuous: Boolean) {
        val from = languageOf(side)
        val to = languageOf(if (side == Side.A) Side.B else Side.A)
        _state.update { it.copy(listening = side, partial = "", partialTranslation = "", level = 0f, error = null) }

        // Start fetching language packs right away so the first translation isn't slow.
        viewModelScope.launch {
            runCatching {
                engine.ensureModel(from)
                engine.ensureModel(to)
            }
        }

        speech.start(Languages.speechTag(from), object : SpeechInput.Listener {
            override fun onLevel(level: Float) {
                _state.update { it.copy(level = level) }
            }

            override fun onPartial(text: String) {
                _state.update { it.copy(partial = text) }
                translatePartial(text, from, to)
            }

            override fun onFinal(text: String) {
                partialJob?.cancel()
                subtitleErrors = 0
                val keepGoing = continuous && _state.value.subtitlesOn
                _state.update {
                    it.copy(listening = if (keepGoing) side else null, partial = "", partialTranslation = "", level = 0f)
                }
                if (text.isNotBlank()) addUtterance(side, text, from, to, speakAloud = !continuous && autoSpeak.value)
                if (keepGoing) restart(side, 50)
            }

            override fun onError(code: Int) {
                partialJob?.cancel()
                val silence = code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (continuous && _state.value.subtitlesOn && code != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    // Silence is normal while captioning; anything else gets a few retries before we give up.
                    if (!silence) subtitleErrors++
                    if (subtitleErrors <= MAX_SUBTITLE_ERRORS) {
                        _state.update { it.copy(partial = "", partialTranslation = "", level = 0f) }
                        restart(side, if (silence) 50 else 800)
                        return
                    }
                }
                _state.update {
                    it.copy(
                        listening = null,
                        subtitlesOn = false,
                        partial = "",
                        partialTranslation = "",
                        level = 0f,
                        error = SpeechInput.errorMessage(code),
                    )
                }
            }
        })
    }

    private fun restart(side: Side, delayMs: Long) {
        restartJob?.cancel()
        restartJob = viewModelScope.launch {
            delay(delayMs)
            if (_state.value.subtitlesOn) listen(side, continuous = true)
        }
    }

    private fun translatePartial(text: String, from: String, to: String) {
        partialJob?.cancel()
        partialJob = viewModelScope.launch {
            delay(200)
            val translated = try {
                engine.translateText(text, from, to)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return@launch
            }
            _state.update { if (it.partial == text) it.copy(partialTranslation = translated) else it }
        }
    }

    private fun addUtterance(side: Side, text: String, from: String, to: String, speakAloud: Boolean) {
        val id = nextId++
        _state.update {
            it.copy(messages = (it.messages + Utterance(id, side, text, null, from, to)).takeLast(MAX_MESSAGES))
        }
        viewModelScope.launch {
            val translated = try {
                engine.translateText(text, from, to)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "⚠ ${e.message ?: "Translation failed"}"
            }
            _state.update { state ->
                state.copy(messages = state.messages.map { if (it.id == id) it.copy(translated = translated) else it })
            }
            if (speakAloud && !translated.startsWith("⚠")) speaker.speak(translated, to)
        }
    }

    override fun onCleared() {
        speech.cancel()
        speaker.shutdown()
    }

    private companion object {
        const val MAX_MESSAGES = 200
        const val MAX_SUBTITLE_ERRORS = 5
    }
}
