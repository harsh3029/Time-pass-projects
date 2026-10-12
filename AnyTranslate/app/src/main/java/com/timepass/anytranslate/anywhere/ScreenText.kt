package com.timepass.anytranslate.anywhere

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlin.math.min

/** A piece of text visible on screen, with its bounds in screen coordinates. */
data class ScreenText(val text: String, val bounds: Rect)

/** Full physical screen size, including system bars. */
@Suppress("DEPRECATION")
internal fun screenSize(context: Context): Point {
    val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
    return Point().also { display.getRealSize(it) }
}

internal fun screenBounds(context: Context): Rect = screenSize(context).let { Rect(0, 0, it.x, it.y) }

/** True when two rectangles overlap by more than 30% of the smaller one. */
internal fun overlaps(a: Rect, b: Rect): Boolean {
    val intersection = Rect()
    if (!intersection.setIntersect(a, b)) return false
    val smaller = min(a.width() * a.height(), b.width() * b.height())
    return smaller > 0 && intersection.width() * intersection.height() > smaller * 0.3f
}

/** Walks the accessibility tree of the visible app windows and collects the text that's on screen. */
internal object ScreenTextCollector {
    private const val MAX_DEPTH = 80

    fun collect(service: AccessibilityService): List<ScreenText> {
        val screen = screenBounds(service)
        val out = mutableListOf<ScreenText>()
        val appWindows = runCatching { service.windows }.getOrNull().orEmpty()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedByDescending { it.layer }

        if (appWindows.isEmpty()) {
            service.rootInActiveWindow?.let { visit(it, out, 0, screen, emptyList()) }
        } else {
            // Top-most first, so text hidden behind a dialog or another window is skipped.
            val covered = mutableListOf<Rect>()
            for (window in appWindows) {
                val root = window.root ?: continue
                visit(root, out, 0, screen, covered)
                covered += Rect().also { window.getBoundsInScreen(it) }
            }
        }
        return out.distinct()
    }

    /** Returns true if this node or one of its descendants contributed text. */
    private fun visit(
        node: AccessibilityNodeInfo,
        out: MutableList<ScreenText>,
        depth: Int,
        screen: Rect,
        covered: List<Rect>,
    ): Boolean {
        if (depth > MAX_DEPTH || !node.isVisibleToUser) return false

        var childAdded = false
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (visit(child, out, depth + 1, screen, covered)) childAdded = true
        }
        // Prefer the innermost nodes; a parent's text usually repeats its children's.
        if (childAdded) return true

        if (node.isPassword) return false
        val text = node.text?.toString()?.trim().orEmpty()
        if (text.isEmpty() || text.none(Char::isLetter)) return false

        val bounds = Rect().also { node.getBoundsInScreen(it) }
        if (!bounds.intersect(screen) || bounds.width() < 8 || bounds.height() < 8) return false
        if (covered.any { it.contains(bounds.centerX(), bounds.centerY()) }) return false

        out += ScreenText(text, bounds)
        return true
    }
}
