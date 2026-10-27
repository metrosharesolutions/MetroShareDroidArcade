package com.metroshare.droidarcade.gfx

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/** Neon palette and shared drawing helpers. All coordinates are in virtual (720-wide) units. */
object Gfx {
    const val PINK = 0xFFFF2A6D.toInt()
    const val CYAN = 0xFF05D9E8.toInt()
    const val MAGENTA = 0xFFD300C5.toInt()
    const val YELLOW = 0xFFFFE138.toInt()
    const val ORANGE = 0xFFFF8C1A.toInt()
    const val GREEN = 0xFF39FF7A.toInt()
    const val PURPLE = 0xFF9D4DFF.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val INK = 0xFF0D0221.toInt()
    const val NIGHT = 0xFF05010F.toInt()

    /** Gem colours: ruby, amber, topaz, emerald, sapphire, amethyst. */
    val GEM_COLORS = intArrayOf(
        0xFFFF2D55.toInt(),
        0xFFFF8C1A.toInt(),
        0xFFFFDD33.toInt(),
        0xFF2EE66B.toInt(),
        0xFF2F8CFF.toInt(),
        0xFFB44DFF.toInt(),
    )

    val RAINBOW = intArrayOf(PINK, ORANGE, YELLOW, GREEN, CYAN, PURPLE)

    var font: Typeface = Typeface.MONOSPACE

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val tmpRect = RectF()
    private val tmpPath = Path()

    fun paint(size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font
            textSize = size
            this.color = color
            textAlign = align
        }

    fun measure(text: String, size: Float): Float {
        textPaint.typeface = font
        textPaint.textSize = size
        return textPaint.measureText(text)
    }

    /** Text with a neon halo. [y] is the baseline. */
    fun glowText(
        c: Canvas,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        glow: Int = color,
        align: Paint.Align = Paint.Align.CENTER,
        alpha: Int = 255,
        glowRadius: Float = size * 0.45f,
    ) {
        textPaint.typeface = font
        textPaint.textSize = size
        textPaint.textAlign = align
        textPaint.shader = null
        textPaint.color = color
        textPaint.alpha = alpha
        if (glowRadius > 0f) textPaint.setShadowLayer(glowRadius, 0f, 0f, withAlpha(glow, (alpha * 0.9f).toInt()))
        c.drawText(text, x, y, textPaint)
        textPaint.clearShadowLayer()
    }

    /** Chunky outlined text with a drop shadow, like a cabinet marquee. */
    fun outlineText(
        c: Canvas,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        outline: Int = INK,
        align: Paint.Align = Paint.Align.CENTER,
        alpha: Int = 255,
        shadow: Float = size * 0.12f,
    ) {
        strokePaint.typeface = font
        strokePaint.textSize = size
        strokePaint.textAlign = align
        strokePaint.strokeWidth = size * 0.22f
        strokePaint.color = outline
        strokePaint.alpha = alpha
        if (shadow > 0f) c.drawText(text, x + shadow, y + shadow, strokePaint)
        c.drawText(text, x, y, strokePaint)
        textPaint.typeface = font
        textPaint.textSize = size
        textPaint.textAlign = align
        textPaint.shader = null
        textPaint.color = color
        textPaint.alpha = alpha
        c.drawText(text, x, y, textPaint)
    }

    /** Text filled with a vertical gradient (chrome logo look). */
    fun gradientText(
        c: Canvas,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        colors: IntArray,
        outline: Int = INK,
        extrude: Int = 0,
        extrudeColor: Int = INK,
        align: Paint.Align = Paint.Align.CENTER,
    ) {
        strokePaint.typeface = font
        strokePaint.textSize = size
        strokePaint.textAlign = align
        strokePaint.strokeWidth = size * 0.16f
        strokePaint.color = extrudeColor
        for (k in extrude downTo 1) {
            c.drawText(text, x + k * 1.5f, y + k * 1.5f, strokePaint)
        }
        strokePaint.color = outline
        c.drawText(text, x, y, strokePaint)
        textPaint.typeface = font
        textPaint.textSize = size
        textPaint.textAlign = align
        textPaint.color = WHITE
        textPaint.shader = LinearGradient(0f, y - size, 0f, y, colors, null, Shader.TileMode.CLAMP)
        c.drawText(text, x, y, textPaint)
        textPaint.shader = null
    }

    /** Glowing rounded frame made of stacked translucent strokes (works on hardware canvases). */
    fun neonFrame(c: Canvas, r: RectF, radius: Float, color: Int, width: Float = 4f, alpha: Int = 255) {
        line.strokeCap = Paint.Cap.ROUND
        for (k in 3 downTo 1) {
            line.color = color
            line.alpha = (alpha * 0.14f * (4 - k)).toInt().coerceIn(0, 255)
            line.strokeWidth = width + k * 7f
            c.drawRoundRect(r, radius, radius, line)
        }
        line.color = color
        line.alpha = alpha
        line.strokeWidth = width
        c.drawRoundRect(r, radius, radius, line)
        line.color = WHITE
        line.alpha = (alpha * 0.55f).toInt()
        line.strokeWidth = width * 0.35f
        c.drawRoundRect(r, radius, radius, line)
    }

    fun panel(c: Canvas, r: RectF, radius: Float, fillColor: Int, border: Int, alpha: Int = 255) {
        fill.shader = null
        fill.color = fillColor
        fill.alpha = (Color.alpha(fillColor) * alpha / 255f).toInt()
        c.drawRoundRect(r, radius, radius, fill)
        neonFrame(c, r, radius, border, 3f, alpha)
    }

    fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        fill.shader = null
        fill.color = color
        c.drawRect(l, t, r, b, fill)
    }

    fun circle(c: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        fill.shader = null
        fill.color = color
        c.drawCircle(x, y, radius, fill)
    }

    fun line(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, color: Int, width: Float) {
        line.color = color
        line.strokeWidth = width
        c.drawLine(x0, y0, x1, y1, line)
    }

    /** Arrow / symbol glyphs drawn as paths (the pixel font has no arrows). */
    enum class Icon { LEFT, RIGHT, DOWN, UP, ROTATE, DROP, PAUSE, PLAY }

    fun icon(c: Canvas, icon: Icon, cx: Float, cy: Float, s: Float, color: Int) {
        fill.shader = null
        fill.color = color
        val p = tmpPath
        p.reset()
        when (icon) {
            Icon.LEFT -> tri(p, cx - s * 0.55f, cy, cx + s * 0.4f, cy - s * 0.6f, cx + s * 0.4f, cy + s * 0.6f)
            Icon.RIGHT -> tri(p, cx + s * 0.55f, cy, cx - s * 0.4f, cy - s * 0.6f, cx - s * 0.4f, cy + s * 0.6f)
            Icon.DOWN -> tri(p, cx, cy + s * 0.55f, cx - s * 0.6f, cy - s * 0.4f, cx + s * 0.6f, cy - s * 0.4f)
            Icon.UP -> tri(p, cx, cy - s * 0.55f, cx - s * 0.6f, cy + s * 0.4f, cx + s * 0.6f, cy + s * 0.4f)
            Icon.DROP -> {
                tri(p, cx, cy + s * 0.25f, cx - s * 0.55f, cy - s * 0.45f, cx + s * 0.55f, cy - s * 0.45f)
                c.drawPath(p, fill)
                p.reset()
                tmpRect.set(cx - s * 0.6f, cy + s * 0.38f, cx + s * 0.6f, cy + s * 0.58f)
                c.drawRoundRect(tmpRect, s * 0.06f, s * 0.06f, fill)
                return
            }
            Icon.PAUSE -> {
                tmpRect.set(cx - s * 0.45f, cy - s * 0.5f, cx - s * 0.12f, cy + s * 0.5f)
                c.drawRect(tmpRect, fill)
                tmpRect.set(cx + s * 0.12f, cy - s * 0.5f, cx + s * 0.45f, cy + s * 0.5f)
                c.drawRect(tmpRect, fill)
                return
            }
            Icon.PLAY -> tri(p, cx + s * 0.55f, cy, cx - s * 0.4f, cy - s * 0.6f, cx - s * 0.4f, cy + s * 0.6f)
            Icon.ROTATE -> {
                line.color = color
                line.strokeWidth = s * 0.2f
                line.strokeCap = Paint.Cap.BUTT
                tmpRect.set(cx - s * 0.45f, cy - s * 0.45f, cx + s * 0.45f, cy + s * 0.45f)
                c.drawArc(tmpRect, -60f, 290f, false, line)
                line.strokeCap = Paint.Cap.ROUND
                // arrow head at the start of the arc (angle -60 deg)
                val ax = cx + s * 0.45f * 0.5f
                val ay = cy - s * 0.45f * 0.866f
                tri(p, ax + s * 0.34f, ay - s * 0.02f, ax - s * 0.1f, ay - s * 0.3f, ax - s * 0.02f, ay + s * 0.24f)
            }
        }
        c.drawPath(p, fill)
    }

    private fun tri(p: Path, x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
        p.moveTo(x0, y0)
        p.lineTo(x1, y1)
        p.lineTo(x2, y2)
        p.close()
    }

    /** Greedy word wrap for the pixel font. */
    fun wrap(text: String, size: Float, maxWidth: Float): List<String> {
        val out = ArrayList<String>()
        for (para in text.split('\n')) {
            var line = ""
            for (word in para.split(' ')) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (measure(candidate, size) <= maxWidth || line.isEmpty()) {
                    line = candidate
                } else {
                    out.add(line)
                    line = word
                }
            }
            out.add(line)
        }
        return out
    }

    // ---------------------------------------------------------------- colour maths

    fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    fun lerpColor(a: Int, b: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        val ar = Color.red(a); val ag = Color.green(a); val ab = Color.blue(a); val aa = Color.alpha(a)
        return Color.argb(
            (aa + (Color.alpha(b) - aa) * u).toInt(),
            (ar + (Color.red(b) - ar) * u).toInt(),
            (ag + (Color.green(b) - ag) * u).toInt(),
            (ab + (Color.blue(b) - ab) * u).toInt(),
        )
    }

    fun lighten(c: Int, t: Float) = lerpColor(c, WHITE, t)
    fun darken(c: Int, t: Float) = lerpColor(c, 0xFF000000.toInt(), t)

    fun hsv(h: Float, s: Float, v: Float): Int = Color.HSVToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s, v))
}
