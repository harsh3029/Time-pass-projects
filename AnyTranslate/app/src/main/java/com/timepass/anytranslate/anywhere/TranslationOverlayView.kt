package com.timepass.anytranslate.anywhere

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Full-screen overlay that paints each translation over the text it replaces.
 * Tapping a box flips it back to the original; tapping anywhere else closes the overlay.
 * Everything is kept in screen coordinates, so it lines up with what the accessibility tree reports.
 */
@SuppressLint("ViewConstructor")
class TranslationOverlayView(context: Context, private val onDismiss: () -> Unit) : View(context) {

    class Item(val bounds: Rect, val original: String, val translated: String) {
        internal var showOriginal = false
        internal var layout: StaticLayout? = null
        internal val box = RectF()
    }

    private val metrics = resources.displayMetrics
    private val night =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private val pad = dp(3f)
    private val radius = dp(6f)

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (night) 0xF2202124.toInt() else 0xF7FFFFFF.toInt()
    }
    private val originalBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (night) 0xF23C3A2E.toInt() else 0xF7FFF4D6.toInt()
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = if (night) 0xFF8AB4F8.toInt() else 0xFF1A73E8.toInt()
    }
    private val baseTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (night) 0xFFE8EAED.toInt() else 0xFF202124.toInt()
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6202124.toInt() }
    private val pillTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(13f)
    }

    private val items = mutableListOf<Item>()
    private var status: String? = null
    private val location = IntArray(2)

    fun setStatus(text: String?) {
        status = text
        invalidate()
    }

    fun setItems(newItems: List<Item>) {
        items.clear()
        items.addAll(newItems)
        items.forEach(::layoutItem)
        invalidate()
    }

    /** Picks the largest font that fits the original bounds; grows the box downwards if even the smallest doesn't. */
    private fun layoutItem(item: Item) {
        val text = if (item.showOriginal) item.original else item.translated
        val bounds = item.bounds
        val width = max(bounds.width() - 2 * pad, dp(24f)).toInt()
        val available = bounds.height() - 2 * pad
        val minSize = sp(9f)
        var size = min(sp(18f), max(sp(10f), bounds.height() * 0.75f))
        var layout = buildLayout(text, width, size)
        while (layout.height > available && size > minSize) {
            size = max(minSize, size - sp(1f))
            layout = buildLayout(text, width, size)
        }
        item.layout = layout
        val height = max(bounds.height().toFloat(), layout.height + 2 * pad)
        item.box.set(bounds.left.toFloat(), bounds.top.toFloat(), bounds.left + width + 2 * pad, bounds.top + height)
    }

    private fun buildLayout(text: String, width: Int, size: Float): StaticLayout {
        // Each layout keeps its own paint: StaticLayout reads the paint again when drawing.
        val paint = TextPaint(baseTextPaint).apply { textSize = size }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setIncludePad(false)
            .build()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        getLocationOnScreen(location)
        canvas.save()
        canvas.translate(-location[0].toFloat(), -location[1].toFloat())
        for (item in items) {
            val layout = item.layout ?: continue
            canvas.drawRoundRect(item.box, radius, radius, if (item.showOriginal) originalBoxPaint else boxPaint)
            canvas.drawRoundRect(item.box, radius, radius, borderPaint)
            canvas.save()
            val dy = max(pad, (item.box.height() - layout.height) / 2f)
            canvas.translate(item.box.left + pad, item.box.top + dy)
            layout.draw(canvas)
            canvas.restore()
        }
        canvas.restore()
        status?.let { drawStatus(canvas, it) }
    }

    private fun drawStatus(canvas: Canvas, text: String) {
        val maxWidth = max(width - dp(64f).toInt(), dp(160f).toInt())
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, pillTextPaint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
        val textWidth = (0 until layout.lineCount).maxOf { layout.getLineWidth(it) }
        val hPad = dp(16f)
        val vPad = dp(10f)
        val top = dp(56f) - location[1]
        val left = (width - textWidth) / 2f - hPad
        val pill = RectF(left, top, left + textWidth + 2 * hPad, top + layout.height + 2 * vPad)
        val corner = min(pill.height() / 2f, dp(20f))
        canvas.drawRoundRect(pill, corner, corner, pillPaint)
        canvas.save()
        canvas.translate((width - maxWidth) / 2f, top + vPad)
        layout.draw(canvas)
        canvas.restore()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val hit = items.lastOrNull { it.box.contains(event.rawX, event.rawY) }
            if (hit != null) {
                hit.showOriginal = !hit.showOriginal
                layoutItem(hit)
                invalidate()
            } else {
                onDismiss()
            }
        }
        return true
    }

    private fun dp(value: Float) = value * metrics.density
    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, metrics)
}
