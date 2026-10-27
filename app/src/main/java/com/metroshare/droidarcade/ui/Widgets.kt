package com.metroshare.droidarcade.ui

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.metroshare.droidarcade.gfx.Gfx
import kotlin.math.hypot

/** A round, domed arcade cabinet button. */
class ArcadeButton(
    val icon: Gfx.Icon?,
    val label: String,
    val color: Int,
) {
    var cx = 0f
        private set
    var cy = 0f
        private set
    var r = 0f
        private set
    var pressed = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bezel: Shader? = null
    private var dome: Shader? = null
    private var domePressed: Shader? = null

    fun place(x: Float, y: Float, radius: Float) {
        cx = x
        cy = y
        r = radius
        bezel = LinearGradient(0f, y - radius * 1.25f, 0f, y + radius * 1.25f, 0xFF4A4A5A.toInt(), 0xFF0E0E16.toInt(), Shader.TileMode.CLAMP)
        dome = RadialGradient(x - radius * 0.35f, y - radius * 0.45f, radius * 1.4f, intArrayOf(Gfx.lighten(color, 0.55f), color, Gfx.darken(color, 0.35f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        val py = y + radius * 0.1f
        domePressed = RadialGradient(x - radius * 0.3f, py - radius * 0.35f, radius * 1.4f, intArrayOf(Gfx.lighten(color, 0.3f), Gfx.darken(color, 0.15f), Gfx.darken(color, 0.5f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }

    /** Distance from the centre in radii; used to pick the nearest button under a finger. */
    fun reach(x: Float, y: Float) = hypot(x - cx, y - cy) / r

    fun draw(c: Canvas, showLabel: Boolean = true) {
        val rb = r * 1.2f
        // Bezel ring
        paint.shader = bezel
        c.drawCircle(cx, cy + r * 0.06f, rb, paint)
        paint.shader = null
        paint.color = 0xFF050508.toInt()
        c.drawCircle(cx, cy + r * 0.08f, r * 1.04f, paint)
        // Plunger side (gives the dome height)
        val lift = if (pressed) r * 0.1f else 0f
        paint.color = Gfx.darken(color, 0.55f)
        c.drawCircle(cx, cy + r * 0.13f, r, paint)
        // Dome
        paint.shader = if (pressed) domePressed else dome
        c.drawCircle(cx, cy + lift, r, paint)
        paint.shader = null
        // Glossy highlight
        if (!pressed) {
            paint.color = Gfx.withAlpha(Gfx.WHITE, 110)
            c.drawOval(cx - r * 0.55f, cy - r * 0.78f, cx + r * 0.35f, cy - r * 0.28f, paint)
        }
        // Glow when lit
        if (pressed) {
            paint.style = Paint.Style.STROKE
            for (k in 1..3) {
                paint.strokeWidth = 5f * k
                paint.color = Gfx.withAlpha(Gfx.lighten(color, 0.3f), 70 / k)
                c.drawCircle(cx, cy + lift, r * 1.05f + k * 4f, paint)
            }
            paint.style = Paint.Style.FILL
        }
        icon?.let {
            Gfx.icon(c, it, cx + 2f, cy + lift + 3f, r * 0.75f, Gfx.withAlpha(0xFF000000.toInt(), 90))
            Gfx.icon(c, it, cx, cy + lift, r * 0.75f, Gfx.withAlpha(Gfx.WHITE, 235))
        }
        if (showLabel && label.isNotEmpty()) {
            Gfx.glowText(c, label, cx, cy + rb + 30f, 18f, Gfx.WHITE, color)
        }
    }
}

/** Rectangular neon menu button with click-on-release semantics. */
class NeonButton(
    var text: String,
    var color: Int,
    val onClick: () -> Unit,
) {
    val rect = RectF()
    var pressed = false
    var enabled = true
    var textSize = 28f
    var icon: Gfx.Icon? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun place(l: Float, t: Float, r: Float, b: Float): NeonButton {
        rect.set(l, t, r, b)
        return this
    }

    fun contains(x: Float, y: Float) = enabled && x >= rect.left - 8f && x <= rect.right + 8f && y >= rect.top - 8f && y <= rect.bottom + 8f

    fun draw(c: Canvas, time: Float, highlight: Boolean = false) {
        val alpha = if (enabled) 255 else 90
        val radius = minOf(18f, rect.height() / 3f)
        paint.style = Paint.Style.FILL
        paint.color = if (pressed) Gfx.withAlpha(color, 230) else Gfx.withAlpha(Gfx.INK, 200)
        c.drawRoundRect(rect, radius, radius, paint)
        if (!pressed) {
            paint.color = Gfx.withAlpha(color, 40)
            c.drawRoundRect(rect.left, rect.top, rect.right, rect.centerY(), radius, radius, paint)
        }
        val pulse = if (highlight) (0.75f + 0.25f * kotlin.math.sin(time * 6f)) else 1f
        Gfx.neonFrame(c, rect, radius, color, 4f, (alpha * pulse).toInt())
        val tc = if (pressed) Gfx.INK else Gfx.WHITE
        val ic = icon
        val baseline = rect.centerY() + textSize * 0.5f
        if (ic != null && text.isEmpty()) {
            Gfx.icon(c, ic, rect.centerX(), rect.centerY(), rect.height() * 0.42f, if (pressed) Gfx.INK else color)
        } else {
            Gfx.glowText(c, text, rect.centerX(), baseline, textSize, tc, if (pressed) Gfx.WHITE else color, alpha = alpha, glowRadius = if (pressed) 0f else textSize * 0.35f)
        }
    }
}

/** Routes pointer events to a set of NeonButtons (tap-to-click, slide off to cancel). */
class ButtonGroup {
    val buttons = ArrayList<NeonButton>()
    private val owner = HashMap<Int, NeonButton>()
    var onPress: (() -> Unit)? = null

    fun add(b: NeonButton): NeonButton {
        buttons.add(b)
        return b
    }

    fun clear() {
        buttons.clear()
        owner.clear()
    }

    fun down(id: Int, x: Float, y: Float): Boolean {
        val b = buttons.lastOrNull { it.contains(x, y) } ?: return false
        owner[id] = b
        b.pressed = true
        onPress?.invoke()
        return true
    }

    fun move(id: Int, x: Float, y: Float) {
        val b = owner[id] ?: return
        b.pressed = b.contains(x, y)
    }

    fun up(id: Int, x: Float, y: Float): Boolean {
        val b = owner.remove(id) ?: return false
        val hit = b.contains(x, y) && b.pressed
        b.pressed = false
        if (hit) b.onClick()
        return hit
    }

    fun cancel() {
        owner.values.forEach { it.pressed = false }
        owner.clear()
    }

    fun draw(c: Canvas, time: Float) {
        for (b in buttons) b.draw(c, time)
    }
}
