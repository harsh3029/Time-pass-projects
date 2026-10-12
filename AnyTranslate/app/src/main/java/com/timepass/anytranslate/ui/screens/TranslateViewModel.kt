package com.timepass.anytranslate.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.timepass.anytranslate.data.AppPrefs
import com.timepass.anytranslate.data.Languages
import com.timepass.anytranslate.translate.TranslationEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TextTranslation(
    val output: String = "",
    val detected: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

@OptIn(FlowPreview::class)
class TranslateViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = AppPrefs.get(app)
    private val engine = TranslationEngine.get()

    val source: StateFlow<String> = prefs.textSource.flow
    val target: StateFlow<String> = prefs.textTarget.flow
    val downloading: StateFlow<Set<String>> = engine.downloading

    /** Kept as Compose state (not a flow) so fast typing never loses characters. */
    var input by mutableStateOf("")
        private set

    private val _result = MutableStateFlow(TextTranslation())
    val result: StateFlow<TextTranslation> = _result.asStateFlow()

    init {
        viewModelScope.launch {
            combine(snapshotFlow { input }.debounce(300), source, target) { text, from, to -> Triple(text, from, to) }
                .collectLatest { (text, from, to) -> translate(text, from, to) }
        }
    }

    fun updateInput(text: String) {
        input = text
    }

    fun setSource(code: String) {
        prefs.textSource.value = code
    }

    fun setTarget(code: String) {
        prefs.textTarget.value = code
    }

    /** Swaps languages and moves the translation into the input box. */
    fun swap() {
        val from = source.value
        val to = target.value
        val newTarget = if (from == Languages.AUTO) result.value.detected ?: return else from
        result.value.output.takeIf { it.isNotBlank() }?.let { input = it }
        prefs.textSource.value = to
        prefs.textTarget.value = newTarget
    }

    private suspend fun translate(text: String, from: String, to: String) {
        if (text.isBlank()) {
            _result.value = TextTranslation()
            return
        }
        _result.update { it.copy(loading = true, error = null) }
        _result.value = try {
            val translated = engine.translate(text, from, to)
            TextTranslation(output = translated.text, detected = translated.sourceLanguage)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TextTranslation(error = e.message ?: "Translation failed")
        }
    }
}
