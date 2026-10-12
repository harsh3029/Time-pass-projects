package com.timepass.anytranslate.translate

import android.util.LruCache
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.timepass.anytranslate.data.Languages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TranslateException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class TranslationResult(val text: String, val sourceLanguage: String)

/**
 * On-device translation with Google ML Kit. Each language needs a ~30 MB model, downloaded once;
 * after that everything works offline and no text ever leaves the phone.
 */
class TranslationEngine private constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val modelManager = RemoteModelManager.getInstance()
    private val identifier = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.4f).build()
    )
    private val translators = object : LruCache<String, Translator>(8) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Translator, newValue: Translator?) {
            if (evicted) oldValue.close()
        }
    }
    private val results = LruCache<String, String>(1_000)
    private val inFlight = mutableMapOf<String, Deferred<Unit>>()

    private val _downloaded = MutableStateFlow(setOf(TranslateLanguage.ENGLISH))
    /** Languages whose model is on the device (English is built in). */
    val downloaded: StateFlow<Set<String>> = _downloaded.asStateFlow()

    private val _downloading = MutableStateFlow(emptySet<String>())
    val downloading: StateFlow<Set<String>> = _downloading.asStateFlow()

    init {
        scope.launch { refreshDownloaded() }
    }

    suspend fun refreshDownloaded() {
        try {
            val models = modelManager.getDownloadedModels(TranslateRemoteModel::class.java).await()
            _downloaded.value = models.map { it.language }.toSet() + TranslateLanguage.ENGLISH
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Keep whatever we knew before.
        }
    }

    /** Returns the language code of [text], or null if it can't be identified confidently. */
    suspend fun identify(text: String): String? = try {
        Languages.fromTag(identifier.identifyLanguage(text).await())
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** Makes sure the model for [code] is on the device, downloading it if needed. */
    suspend fun ensureModel(code: String) {
        if (code in _downloaded.value) return
        val download = synchronized(inFlight) {
            inFlight.getOrPut(code) {
                // Lazy so the block only runs after it is registered in inFlight.
                scope.async(start = CoroutineStart.LAZY) {
                    try {
                        download(code)
                    } finally {
                        synchronized(inFlight) { inFlight.remove(code) }
                    }
                }
            }
        }
        download.await()
    }

    private suspend fun download(code: String) {
        _downloading.update { it + code }
        try {
            val model = TranslateRemoteModel.Builder(code).build()
            if (!modelManager.isModelDownloaded(model).await()) {
                modelManager.download(model, DownloadConditions.Builder().build()).await()
            }
            _downloaded.update { it + code }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw TranslateException(
                "Couldn't download the ${Languages.displayName(code)} language pack. Check your internet connection.",
                e,
            )
        } finally {
            _downloading.update { it - code }
        }
    }

    suspend fun deleteModel(code: String) {
        if (code == TranslateLanguage.ENGLISH) return
        modelManager.deleteDownloadedModel(TranslateRemoteModel.Builder(code).build()).await()
        _downloaded.update { it - code }
        synchronized(translators) { translators.evictAll() }
    }

    /** Translates [text]; [source] may be [Languages.AUTO] to detect the language first. */
    suspend fun translate(text: String, source: String, target: String): TranslationResult {
        val from = if (source == Languages.AUTO) {
            identify(text) ?: throw TranslateException("Couldn't detect the language. Choose it manually.")
        } else {
            source
        }
        if (from == target || text.isBlank()) return TranslationResult(text, from)
        return TranslationResult(translateText(text, from, target), from)
    }

    /** Translates [text] between two known languages, downloading models when needed. */
    suspend fun translateText(text: String, source: String, target: String): String {
        if (source == target) return text
        val key = "$source>$target>$text"
        results.get(key)?.let { return it }
        ensureModel(source)
        ensureModel(target)
        val translated = try {
            translator(source, target).translate(text).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw TranslateException("Translation failed: ${e.message ?: e.javaClass.simpleName}", e)
        }
        results.put(key, translated)
        return translated
    }

    private suspend fun translator(source: String, target: String): Translator {
        val key = "$source>$target"
        synchronized(translators) { translators.get(key) }?.let { return it }
        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build()
        )
        // Models are already on disk at this point, so this only binds them to the client.
        translator.downloadModelIfNeeded().await()
        synchronized(translators) { translators.put(key, translator) }
        return translator
    }

    companion object {
        @Volatile
        private var instance: TranslationEngine? = null

        fun get(): TranslationEngine =
            instance ?: synchronized(this) { instance ?: TranslationEngine().also { instance = it } }
    }
}
