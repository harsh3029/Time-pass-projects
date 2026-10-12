package com.timepass.anytranslate.data

import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

/** Languages supported by the on-device translator, plus helpers for speech and text-to-speech locales. */
object Languages {
    /** Pseudo-language meaning "detect the source language automatically". */
    const val AUTO = "auto"

    private val supported: Set<String> by lazy { TranslateLanguage.getAllLanguages().toSet() }

    /** ML Kit language codes, sorted by their name in the device language. */
    val all: List<String> by lazy { supported.sortedBy { displayName(it) } }

    fun displayName(code: String): String {
        if (code == AUTO) return "Detect language"
        val locale = Locale.forLanguageTag(code)
        val name = locale.getDisplayName(Locale.getDefault()).ifBlank { code }
        return name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    /** The language's name written in that language, e.g. "हिन्दी" for Hindi. */
    fun nativeName(code: String): String {
        if (code == AUTO) return displayName(code)
        val locale = Locale.forLanguageTag(code)
        return locale.getDisplayName(locale).ifBlank { code }.replaceFirstChar { it.titlecase(locale) }
    }

    /** BCP-47 tag that speech recognizers and TTS engines understand best for [code]. */
    fun speechTag(code: String): String = SPEECH_TAGS[code] ?: code

    fun locale(code: String): Locale = Locale.forLanguageTag(speechTag(code))

    /**
     * Maps a tag returned by ML Kit language identification (or a device locale) to a translation
     * language code, or null if it's undetermined, romanized (e.g. "hi-Latn") or unsupported.
     */
    fun fromTag(tag: String): String? {
        if (tag == "und" || tag.endsWith("-Latn", ignoreCase = true)) return null
        val base = when (val lang = tag.substringBefore('-').lowercase(Locale.ROOT)) {
            "iw" -> "he"
            "in" -> "id"
            "fil" -> "tl"
            "nb", "nn" -> "no"
            else -> lang
        }
        return base.takeIf { it in supported }
    }

    /** The device language when it is supported, otherwise English. */
    fun deviceLanguage(): String = fromTag(Locale.getDefault().language) ?: TranslateLanguage.ENGLISH

    /** A sensible second language for the live translator. */
    fun defaultPartner(): String =
        if (deviceLanguage() == TranslateLanguage.ENGLISH) TranslateLanguage.HINDI else TranslateLanguage.ENGLISH

    private val SPEECH_TAGS = mapOf(
        "ar" to "ar-SA", "bn" to "bn-IN", "cs" to "cs-CZ", "da" to "da-DK", "de" to "de-DE",
        "el" to "el-GR", "en" to "en-US", "es" to "es-ES", "fa" to "fa-IR", "fi" to "fi-FI",
        "fr" to "fr-FR", "gu" to "gu-IN", "he" to "he-IL", "hi" to "hi-IN", "hu" to "hu-HU",
        "id" to "id-ID", "it" to "it-IT", "ja" to "ja-JP", "kn" to "kn-IN", "ko" to "ko-KR",
        "mr" to "mr-IN", "ms" to "ms-MY", "nl" to "nl-NL", "no" to "nb-NO", "pl" to "pl-PL",
        "pt" to "pt-BR", "ro" to "ro-RO", "ru" to "ru-RU", "sv" to "sv-SE", "sw" to "sw-KE",
        "ta" to "ta-IN", "te" to "te-IN", "th" to "th-TH", "tl" to "fil-PH", "tr" to "tr-TR",
        "uk" to "uk-UA", "ur" to "ur-PK", "vi" to "vi-VN", "zh" to "zh-CN",
    )
}
