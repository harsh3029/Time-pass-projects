package com.timepass.anytranslate.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A persisted value that can also be observed. Write it from the main thread. */
class Pref<T> internal constructor(read: () -> T, private val write: (T) -> Unit) {
    private val state = MutableStateFlow(read())
    val flow: StateFlow<T> = state.asStateFlow()

    var value: T
        get() = state.value
        set(newValue) {
            if (newValue == state.value) return
            write(newValue)
            state.value = newValue
        }
}

class AppPrefs private constructor(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("anytranslate", Context.MODE_PRIVATE)

    // Translate tab
    val textSource = string("text_source", Languages.AUTO)
    val textTarget = string("text_target", Languages.deviceLanguage())

    // Selected-text / share sheet
    val quickTarget = string("quick_target", Languages.deviceLanguage())

    // Whole-screen translation
    val screenSource = string("screen_source", Languages.AUTO)
    val screenTarget = string("screen_target", Languages.deviceLanguage())
    val showBubble = bool("show_bubble", true)
    val useOcr = bool("use_ocr", true)
    val bubbleX = int("bubble_x", -1)
    val bubbleY = int("bubble_y", -1)

    // Live translator
    val liveLangA = string("live_a", Languages.deviceLanguage())
    val liveLangB = string("live_b", Languages.defaultPartner())
    val autoSpeak = bool("auto_speak", true)
    val faceToFace = bool("face_to_face", false)

    private fun string(key: String, default: String) =
        Pref({ sp.getString(key, default) ?: default }, { sp.edit().putString(key, it).apply() })

    private fun bool(key: String, default: Boolean) =
        Pref({ sp.getBoolean(key, default) }, { sp.edit().putBoolean(key, it).apply() })

    private fun int(key: String, default: Int) =
        Pref({ sp.getInt(key, default) }, { sp.edit().putInt(key, it).apply() })

    companion object {
        @Volatile
        private var instance: AppPrefs? = null

        fun get(context: Context): AppPrefs =
            instance ?: synchronized(this) {
                instance ?: AppPrefs(context.applicationContext).also { instance = it }
            }
    }
}
