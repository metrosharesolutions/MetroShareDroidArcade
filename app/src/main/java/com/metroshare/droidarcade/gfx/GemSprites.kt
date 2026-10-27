package com.metroshare.droidarcade.gfx

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import com.metroshare.droidarcade.game.Gem
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pre-rendered faceted jewels. Each colour also has its own silhouette (round, hexagon, star,
 * emerald, diamond, triangle) so colour-blind players can tell them apart.
 */
class GemSprites(val sizePx: Int) {
    private val gems = Array(Gem.MAX_COLORS) { render(it, -1) }
    private val magic = Array(MAGIC_FRAMES) { render(-1, it) }

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val flashPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(0xFFFFFFFF.toInt(), PorterDuff.Mode.SRC_ATOP)
    }
    private val grayPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f); postConcat(ColorMatrix().apply { setScale(0.55f, 0.55f, 0.6f, 1f) }) })
    }
    private val dst = RectF()

    enum class Style { NORMAL, FLASH, GRAY, GHOST }

    /** Draws gem [g] centred at (cx, cy) with the given on-screen size (virtual units). */
    fun draw(c: Canvas, g: Int, cx: Float, cy: Float, size: Float, time: Float, style: Style = Style.NORMAL, alpha: Int = 255) {
        val bmp = when {
            g == Gem.MAGIC -> magic[((time * 14f).toInt() % MAGIC_FRAMES + MAGIC_FRAMES) % MAGIC_FRAMES]
            Gem.isColor(g) -> gems[g]
            else -> return
        }
        val h = size / 2f
        dst.set(cx - h, cy - h, cx + h, cy + h)
        val p = when (style) {
            Style.NORMAL -> paint
            Style.FLASH -> flashPaint
            Style.GRAY -> grayPaint
            Style.GHOST -> paint
        }
        p.alpha = if (style == Style.GHOST) (alpha * 0.28f).toInt() else alpha
        c.drawBitmap(bmp, null, dst, p)
        p.alpha = 255
    }

    fun recycle() {
        gems.forEach { it.recycle() }
        magic.forEach { it.recycle() }
    }

    // ------------------------------------------------------------------ rendering

    private fun shape(color: Int): Array<FloatArray> = when (color) {
        0 -> poly(12, 0.92f, 15f)                     // round brilliant
        1 -> poly(6, 0.98f, -90f)                     // hexagon
        2 -> star(5, 1.02f, 0.5f)                     // star
        3 -> arrayOf(                                 // emerald cut
            floatArrayOf(-0.52f, -0.86f), floatArrayOf(0.52f, -0.86f), floatArrayOf(0.86f, -0.52f),
            floatArrayOf(0.86f, 0.52f), floatArrayOf(0.52f, 0.86f), floatArrayOf(-0.52f, 0.86f),
            floatArrayOf(-0.86f, 0.52f), floatArrayOf(-0.86f, -0.52f),
        )
        4 -> arrayOf(                                 // diamond
            floatArrayOf(0f, -1.02f), floatArrayOf(0.8f, 0f), floatArrayOf(0f, 1.02f), floatArrayOf(-0.8f, 0f),
        )
        else -> arrayOf(                              // triangle
            floatArrayOf(0f, -0.92f), floatArrayOf(1.0f, 0.78f), floatArrayOf(-1.0f, 0.78f),
        )
    }

    private fun poly(n: Int, r: Float, startDeg: Float) = Array(n) { k ->
        val a = (startDeg + k * 360f / n) * PI.toFloat() / 180f
        floatArrayOf(cos(a) * r, sin(a) * r)
    }

    private fun star(points: Int, outer: Float, inner: Float) = Array(points * 2) { k ->
        val a = (-90f + k * 180f / points) * PI.toFloat() / 180f
        val r = if (k % 2 == 0) outer else inner
        floatArrayOf(cos(a) * r, sin(a) * r + 0.06f)
    }

    private fun render(color: Int, magicFrame: Int): Bitmap {
        val s = sizePx.coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = s / 2f
        val cy = s / 2f
        val rad = s * 0.40f
        val isMagic = magicFrame >= 0
        val base = if (isMagic) 0xFFFFFFFF.toInt() else Gfx.GEM_COLORS[color]
        val verts = if (isMagic) poly(12, 0.95f, 15f) else shape(color)
        val light = Gfx.lighten(base, 0.6f)
        val dark = Gfx.darken(base, 0.55f)

        fun pathOf(v: Array<FloatArray>, scale: Float, ox: Float = 0f, oy: Float = 0f) = Path().apply {
            for ((k, p) in v.withIndex()) {
                val x = cx + (p[0] * scale + ox) * rad
                val y = cy + (p[1] * scale + oy) * rad
                if (k == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        // Soft neon glow behind the gem.
        val glowColor = if (isMagic) Gfx.hsv(magicFrame * 45f, 0.8f, 1f) else base
        fill.color = Gfx.withAlpha(glowColor, 150)
        fill.maskFilter = BlurMaskFilter(s * 0.09f, BlurMaskFilter.Blur.NORMAL)
        c.drawPath(pathOf(verts, 1.0f), fill)
        fill.maskFilter = null

        // Faceted crown: each outer facet is shaded by how much it faces the light (top-left).
        val inner = 0.52f
        val ox = -0.05f
        val oy = -0.07f
        val lx = -0.6f
        val ly = -0.8f
        val n = verts.size
        val facet = Path()
        for (k in 0 until n) {
            val a = verts[k]
            val b = verts[(k + 1) % n]
            val mx = (a[0] + b[0]) / 2f
            val my = (a[1] + b[1]) / 2f
            val len = sqrt(mx * mx + my * my).coerceAtLeast(0.001f)
            val dot = (mx / len) * lx + (my / len) * ly
            val t = (dot + 1f) / 2f
            fill.color = if (isMagic) {
                Gfx.lerpColor(Gfx.darken(Gfx.hsv(k * 30f + magicFrame * 45f, 0.85f, 1f), 0.45f), Gfx.lighten(Gfx.hsv(k * 30f + magicFrame * 45f, 0.85f, 1f), 0.25f), t)
            } else {
                Gfx.lerpColor(dark, Gfx.lighten(base, 0.35f), t * t * 0.6f + t * 0.4f)
            }
            facet.reset()
            facet.moveTo(cx + a[0] * rad, cy + a[1] * rad)
            facet.lineTo(cx + b[0] * rad, cy + b[1] * rad)
            facet.lineTo(cx + (b[0] * inner + ox) * rad, cy + (b[1] * inner + oy) * rad)
            facet.lineTo(cx + (a[0] * inner + ox) * rad, cy + (a[1] * inner + oy) * rad)
            facet.close()
            c.drawPath(facet, fill)
        }

        // Table (the flat top facet).
        val table = pathOf(verts, inner, ox, oy)
        val tableTop = if (isMagic) 0xFFFFFFFF.toInt() else light
        val tableBottom = if (isMagic) Gfx.hsv(magicFrame * 45f + 180f, 0.5f, 1f) else base
        fill.shader = LinearGradient(cx - rad * 0.5f, cy - rad * 0.5f, cx + rad * 0.4f, cy + rad * 0.5f, tableTop, tableBottom, Shader.TileMode.CLAMP)
        c.drawPath(table, fill)
        fill.shader = null

        // Facet edges.
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = s * 0.012f
            this.color = Gfx.withAlpha(0xFFFFFFFF.toInt(), 70)
        }
        for (k in 0 until n) {
            val a = verts[k]
            c.drawLine(cx + a[0] * rad, cy + a[1] * rad, cx + (a[0] * inner + ox) * rad, cy + (a[1] * inner + oy) * rad, edge)
        }
        c.drawPath(table, edge)

        // Dark rim.
        edge.color = Gfx.darken(if (isMagic) 0xFF5A189A.toInt() else base, 0.7f)
        edge.strokeWidth = s * 0.03f
        edge.strokeJoin = Paint.Join.ROUND
        c.drawPath(pathOf(verts, 1.0f), edge)

        // Specular highlight + sparkle.
        fill.color = Gfx.withAlpha(0xFFFFFFFF.toInt(), 170)
        val hx = cx + (verts.minOf { it[0] } * 0.32f + ox) * rad
        val hy = cy + (verts.minOf { it[1] } * 0.32f + oy) * rad
        c.drawOval(RectF(hx - rad * 0.16f, hy - rad * 0.09f, hx + rad * 0.16f, hy + rad * 0.09f), fill)
        sparkle(c, cx - rad * 0.42f, cy - rad * 0.5f, rad * 0.26f, fill)
        if (isMagic) {
            fill.color = 0xFFFFFFFF.toInt()
            sparkle(c, cx, cy + rad * 0.03f, rad * 0.42f, fill)
        }
        return bmp
    }

    private fun sparkle(c: Canvas, x: Float, y: Float, r: Float, p: Paint) {
        val path = Path()
        val w = r * 0.22f
        path.moveTo(x, y - r)
        path.lineTo(x + w, y - w)
        path.lineTo(x + r, y)
        path.lineTo(x + w, y + w)
        path.lineTo(x, y + r)
        path.lineTo(x - w, y + w)
        path.lineTo(x - r, y)
        path.lineTo(x - w, y - w)
        path.close()
        c.drawPath(path, p)
    }

    companion object {
        const val MAGIC_FRAMES = 8
    }
}
