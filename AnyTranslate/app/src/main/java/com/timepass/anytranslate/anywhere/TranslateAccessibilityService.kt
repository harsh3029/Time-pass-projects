package com.timepass.anytranslate.anywhere

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.timepass.anytranslate.MainActivity
import com.timepass.anytranslate.R
import com.timepass.anytranslate.data.AppPrefs
import com.timepass.anytranslate.data.Languages
import com.timepass.anytranslate.translate.TranslationEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Translates whatever is on screen, in any app.
 *
 * When triggered (floating button, Quick Settings tile or accessibility shortcut) it reads the visible
 * text from the accessibility tree, optionally runs OCR on a screenshot for text inside images,
 * translates everything on-device and paints the translations over the original text.
 * Nothing is read in the background: the service only listens for window changes to close a stale overlay.
 */
class TranslateAccessibilityService : AccessibilityService() {

    private var scope = MainScope()
    private lateinit var windowManager: WindowManager
    private val prefs by lazy { AppPrefs.get(this) }
    private val engine get() = TranslationEngine.get()

    private var bubble: FloatingBubble? = null
    private var overlay: TranslationOverlayView? = null
    private var translateJob: Job? = null
    private var overlayShownAt = 0L
    private var buttonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.cancel()
        scope = MainScope()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        instance = this
        _running.value = true

        bubble = FloatingBubble(this, windowManager, prefs, onTap = { toggleTranslation() }, onLongPress = { openApp() })
        scope.launch {
            prefs.showBubble.flow.collect { show -> if (show) bubble?.show() else bubble?.hide() }
        }
        registerAccessibilityButton()
        TranslateTileService.requestUpdate(this)
    }

    private fun registerAccessibilityButton() {
        val callback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
            override fun onClicked(controller: AccessibilityButtonController) = toggleTranslation()
        }
        runCatching { accessibilityButtonController.registerAccessibilityButtonCallback(callback) }
            .onSuccess { buttonCallback = callback }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || overlay == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == SYSTEM_UI) return
        // The user navigated somewhere else, so the translations no longer line up.
        if (SystemClock.uptimeMillis() - overlayShownAt > 1_000) dismissOverlay()
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        if (instance === this) instance = null
        _running.value = false
        dismissOverlay()
        bubble?.hide()
        bubble = null
        buttonCallback?.let { runCatching { accessibilityButtonController.unregisterAccessibilityButtonCallback(it) } }
        buttonCallback = null
        scope.cancel()
        TranslateTileService.requestUpdate(this)
    }

    fun toggleTranslation() {
        if (overlay != null) dismissOverlay() else translateScreen()
    }

    /** Called from the Quick Settings tile: close the shade first so we translate the app, not the shade. */
    fun translateFromQuickSettings() {
        scope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            delay(700)
            dismissOverlay()
            translateScreen()
        }
    }

    private fun translateScreen() {
        translateJob?.cancel()
        val source = prefs.screenSource.value
        val target = prefs.screenTarget.value
        bubble?.setBusy(true)

        translateJob = scope.launch {
            try {
                // Walking the tree is lots of small IPC calls; keep it off the main thread.
                val nodes = withContext(Dispatchers.Default) { ScreenTextCollector.collect(this@TranslateAccessibilityService) }
                val imageText = if (prefs.useOcr.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    readImageText(source, nodes)
                } else {
                    emptyList()
                }
                val view = showOverlay()
                view.setStatus(getString(R.string.overlay_translating))

                val texts = nodes + imageText
                if (texts.isEmpty()) {
                    view.setStatus(getString(R.string.overlay_no_text))
                    return@launch
                }
                val (detected, items) = translateTexts(texts, source, target, view)
                if (items.isEmpty()) {
                    view.setStatus(getString(R.string.overlay_already, Languages.displayName(target)))
                    return@launch
                }
                view.setItems(items)
                view.setStatus(
                    if (detected != null) {
                        getString(R.string.overlay_done, Languages.displayName(detected), Languages.displayName(target))
                    } else {
                        getString(R.string.overlay_done_mixed, Languages.displayName(target))
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Screen translation failed", e)
                val message = e.message ?: getString(R.string.overlay_failed)
                overlay?.setStatus(message) ?: Toast.makeText(this@TranslateAccessibilityService, message, Toast.LENGTH_LONG).show()
            } finally {
                bubble?.setBusy(false)
            }
        }
    }

    /**
     * Translates every text. With automatic detection, each text gets its own language when we can tell,
     * otherwise the screen's main foreign language is used. Texts already in [target] are left alone.
     * Returns the main source language (if any) and the overlay items.
     */
    private suspend fun translateTexts(
        texts: List<ScreenText>,
        source: String,
        target: String,
        view: TranslationOverlayView,
    ): Pair<String?, List<TranslationOverlayView.Item>> = coroutineScope {
        val unique = texts.map { it.text.take(MAX_CHARS) }.distinct()

        val identified: Map<String, String?> =
            if (source == Languages.AUTO) {
                unique.associateWith { if (it.count(Char::isLetter) >= 4) engine.identify(it) else null }
            } else {
                emptyMap()
            }
        val main: String? = if (source == Languages.AUTO) {
            identified.entries
                .mapNotNull { (text, lang) -> lang?.takeIf { it != target }?.let { it to text.length } }
                .groupBy({ it.first }, { it.second })
                .maxByOrNull { (_, lengths) -> lengths.sum() }
                ?.key
        } else {
            source.takeIf { it != target }
        }
        if (main == null) return@coroutineScope null to emptyList()

        // Fetch language packs up front so the user knows why the first run is slow.
        for (lang in listOf(main, target)) {
            if (lang !in engine.downloaded.value) {
                view.setStatus(getString(R.string.overlay_downloading, Languages.displayName(lang)))
            }
            engine.ensureModel(lang)
        }
        view.setStatus(getString(R.string.overlay_translating))

        val downloaded = engine.downloaded.value
        val permits = Semaphore(4)
        val translations = unique.map { text ->
            async {
                val from = if (source != Languages.AUTO) {
                    source
                } else {
                    val lang = identified[text]
                    when {
                        lang == null -> main
                        lang == target -> null
                        // Never trigger surprise downloads for a language seen in a single short label.
                        lang in downloaded -> lang
                        else -> main
                    }
                }
                val translated = if (from == null || from == target) {
                    null
                } else {
                    permits.withPermit {
                        try {
                            engine.translateText(text, from, target)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Couldn't translate a text", e)
                            null
                        }
                    }
                }
                text to translated
            }
        }.awaitAll().toMap()

        val items = texts.mapNotNull { screenText ->
            val translated = translations[screenText.text.take(MAX_CHARS)]
            if (translated.isNullOrBlank() || translated == screenText.text) null
            else TranslationOverlayView.Item(screenText.bounds, screenText.text, translated)
        }
        main to items
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun readImageText(source: String, nodes: List<ScreenText>): List<ScreenText> {
        val bitmap = captureWithoutBubble() ?: return emptyList()

        val blocks = try {
            OcrReader.read(bitmap, source)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Usually: the OCR model is still being downloaded by Google Play services.
            Log.w(TAG, "OCR failed", e)
            emptyList()
        } finally {
            bitmap.recycle()
        }
        // Keep only text the accessibility tree doesn't already give us (i.e. text inside images).
        return blocks.filter { block -> nodes.none { overlaps(it.bounds, block.bounds) } }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun captureWithoutBubble(): Bitmap? {
        bubble?.setHidden(true)
        try {
            delay(100) // let the button disappear before the screenshot
            return captureScreen()
        } finally {
            bubble?.setHidden(false)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun captureScreen(): Bitmap? = suspendCancellableCoroutine { cont ->
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val buffer = screenshot.hardwareBuffer
                val bitmap = try {
                    // OCR needs a software bitmap it can read pixels from.
                    Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't read screenshot", e)
                    null
                } finally {
                    buffer.close()
                }
                cont.resume(bitmap)
            }

            override fun onFailure(errorCode: Int) {
                Log.w(TAG, "Screenshot failed: $errorCode")
                cont.resume(null)
            }
        })
    }

    private fun showOverlay(): TranslationOverlayView {
        overlay?.let { return it }
        val view = TranslationOverlayView(this) { dismissOverlay() }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        windowManager.addView(view, params)
        overlay = view
        overlayShownAt = SystemClock.uptimeMillis()
        bubble?.bringToFront()
        return view
    }

    private fun dismissOverlay() {
        translateJob?.cancel()
        translateJob = null
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
        bubble?.setBusy(false)
    }

    private fun openApp() {
        dismissOverlay()
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_ANYWHERE)
        )
    }

    companion object {
        private const val TAG = "AnyTranslateService"
        private const val SYSTEM_UI = "com.android.systemui"
        private const val MAX_CHARS = 4_000

        private val _running = MutableStateFlow(false)
        /** Whether the user has turned the service on (and Android has connected it). */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        @Volatile
        var instance: TranslateAccessibilityService? = null
            private set
    }
}
