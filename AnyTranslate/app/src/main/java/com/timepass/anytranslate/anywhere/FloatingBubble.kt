package com.timepass.anytranslate.anywhere

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.animation.doOnEnd
import com.timepass.anytranslate.R
import com.timepass.anytranslate.data.AppPrefs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Draggable round button that floats above every app. Tap to translate, long-press to open the app. */
class FloatingBubble(
    private val context: Context,
    private val windowManager: WindowManager,
    private val prefs: AppPrefs,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit,
) {
    private val density = context.resources.displayMetrics.density
    private val sizePx = (54 * density).roundToInt()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())

    private val view = ImageView(context).apply {
        setImageResource(R.drawable.ic_bubble_translate)
        setBackgroundResource(R.drawable.bubble_background)
        val padding = (14 * density).roundToInt()
        setPadding(padding, padding, padding, padding)
        contentDescription = context.getString(R.string.bubble_description)
    }

    private val params = WindowManager.LayoutParams(
        sizePx,
        sizePx,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private var attached = false
    private var pulse: ValueAnimator? = null

    init {
        view.setOnTouchListener(DragListener())
    }

    fun show() {
        if (attached) return
        val screen = screenSize(context)
        params.x = prefs.bubbleX.value.takeIf { it >= 0 } ?: (screen.x - sizePx)
        params.y = prefs.bubbleY.value.takeIf { it >= 0 } ?: (screen.y * 0.35f).roundToInt()
        clampToScreen()
        try {
            windowManager.addView(view, params)
            attached = true
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't show the floating button", e)
        }
    }

    fun hide() {
        setBusy(false)
        if (!attached) return
        runCatching { windowManager.removeView(view) }
        attached = false
    }

    /** Temporarily hides the button, e.g. so it doesn't end up in a screenshot. */
    fun setHidden(hidden: Boolean) {
        view.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
    }

    fun setBusy(busy: Boolean) {
        pulse?.cancel()
        pulse = null
        view.alpha = 1f
        if (busy) {
            pulse = ValueAnimator.ofFloat(1f, 0.4f).apply {
                duration = 550
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { view.alpha = it.animatedValue as Float }
                start()
            }
        }
    }

    /** Re-adds the window so it stays above a newly added overlay. */
    fun bringToFront() {
        if (!attached) return
        runCatching {
            windowManager.removeView(view)
            windowManager.addView(view, params)
        }
    }

    private fun clampToScreen() {
        val screen = screenSize(context)
        params.x = params.x.coerceIn(0, (screen.x - sizePx).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (screen.y - sizePx).coerceAtLeast(0))
    }

    private fun updateLayout() {
        if (attached) runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun snapToEdge() {
        val screen = screenSize(context)
        val targetX = if (params.x + sizePx / 2 < screen.x / 2) 0 else screen.x - sizePx
        ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 180
            addUpdateListener {
                params.x = it.animatedValue as Int
                updateLayout()
            }
            doOnEnd {
                prefs.bubbleX.value = params.x
                prefs.bubbleY.value = params.y
            }
            start()
        }
    }

    private inner class DragListener : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var longPressed = false

        private val longPress = Runnable {
            longPressed = true
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLongPress()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    longPressed = false
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && hypot(dx, dy) > touchSlop) {
                        dragging = true
                        handler.removeCallbacks(longPress)
                    }
                    if (dragging) {
                        params.x = startX + dx.roundToInt()
                        params.y = startY + dy.roundToInt()
                        clampToScreen()
                        updateLayout()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    when {
                        dragging -> snapToEdge()
                        !longPressed -> onTap()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    if (dragging) snapToEdge()
                }
            }
            return true
        }
    }

    private companion object {
        const val TAG = "FloatingBubble"
    }
}
