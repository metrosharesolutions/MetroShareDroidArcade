package com.metroshare.droidarcade.gfx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.sin
import kotlin.random.Random

/** One colour scheme for the outrun-style backdrop. Each level gets the next theme. */
class Theme(
    val name: String,
    val skyTop: Int,
    val skyBottom: Int,
    val sunTop: Int,
    val sunBottom: Int,
    val grid: Int,
    val mountain: Int,
    val accent: Int,
)

/** Retro sunset: starfield, striped sun, mountains and a scrolling neon grid. */
class SynthBackground {
    private var theme = THEMES[0]
    private var previous: Theme? = null
    private var blend = 1f
    private var scroll = 0f
    private var time = 0f

    private var w = 0f
    private var h = 0f
    private var left = 0f
    private var right = 0f
    private var horizon = 0f
    private var pxScale = 1f

    private val stars = Array(70) { floatArrayOf(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 6.28f, 1f + Random.nextFloat() * 2.5f) }
    private val mountainsFar = ridge(Random(7), 9, 0.10f, 0.05f)
    private val mountainsNear = ridge(Random(11), 7, 0.06f, 0.035f)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sunCache = HashMap<Theme, Bitmap>()
    private val shaderCache = HashMap<Theme, Array<Shader>>()
    private val sunPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val rect = RectF()

    fun setTheme(t: Theme, instant: Boolean = false) {
        if (t === theme) return
        previous = if (instant) null else theme
        theme = t
        blend = if (instant) 1f else 0f
    }

    /** [left]/[right] extend past 0..[width] when the screen is wider than the virtual area. */
    fun layout(width: Float, height: Float, left: Float, right: Float, horizonFrac: Float, pixelScale: Float) {
        if (w != width || h != height || pixelScale != pxScale) {
            sunCache.values.forEach { it.recycle() }
            sunCache.clear()
        }
        shaderCache.clear()
        w = width
        h = height
        this.left = left
        this.right = right
        horizon = height * horizonFrac
        pxScale = pixelScale
    }

    fun update(dt: Float, speed: Float = 1f) {
        time += dt
        scroll = (scroll + dt * 0.9f * speed) % 1f
        if (blend < 1f) blend = (blend + dt / 1.2f).coerceAtMost(1f)
        if (blend >= 1f) previous = null
    }

    fun draw(c: Canvas) {
        val prev = previous
        if (prev != null) {
            drawTheme(c, prev, 255)
            drawTheme(c, theme, (blend * 255).toInt())
        } else {
            drawTheme(c, theme, 255)
        }
    }

    private fun shadersFor(t: Theme): Array<Shader> = shaderCache.getOrPut(t) {
        val sunR = w * 0.27f
        val sunCy = horizon - sunR * 0.35f
        arrayOf(
            LinearGradient(0f, 0f, 0f, horizon, t.skyTop, t.skyBottom, Shader.TileMode.CLAMP),
            RadialGradient(w / 2f, sunCy, sunR * 2.1f, Gfx.withAlpha(t.sunBottom, 110), Gfx.withAlpha(t.sunBottom, 0), Shader.TileMode.CLAMP),
            LinearGradient(0f, horizon, 0f, h, Gfx.darken(t.mountain, 0.5f), Gfx.NIGHT, Shader.TileMode.CLAMP),
        )
    }

    private fun drawTheme(c: Canvas, t: Theme, alpha: Int) {
        val shaders = shadersFor(t)
        // Sky
        paint.shader = shaders[0]
        paint.alpha = alpha
        c.drawRect(left, 0f, right, horizon + 1f, paint)
        paint.shader = null

        // Stars
        for (s in stars) {
            val sy = s[1] * horizon * 0.85f
            val tw = 0.5f + 0.5f * sin(time * s[3] + s[2])
            paint.color = Gfx.WHITE
            paint.alpha = (alpha * (0.25f + 0.75f * tw) * (1f - s[1] * 0.6f)).toInt()
            val sz = if (s[3] > 3f) 3f else 2f
            val sx = left + s[0] * (right - left)
            c.drawRect(sx, sy, sx + sz, sy + sz, paint)
        }

        // Sun with glow
        val sunR = w * 0.27f
        val sunCx = w / 2f
        val sunCy = horizon - sunR * 0.35f
        paint.shader = shaders[1]
        paint.alpha = alpha
        c.drawCircle(sunCx, sunCy, sunR * 2.1f, paint)
        paint.shader = null
        val sun = sunCache.getOrPut(t) { renderSun(t, sunR) }
        rect.set(sunCx - sunR, sunCy - sunR, sunCx + sunR, sunCy + sunR)
        sunPaint.alpha = alpha
        c.save()
        c.clipRect(left, 0f, right, horizon)
        c.drawBitmap(sun, null, rect, sunPaint)
        c.restore()

        // Mountains
        drawRidge(c, mountainsFar, Gfx.lerpColor(t.mountain, t.skyBottom, 0.35f), Gfx.withAlpha(t.accent, 120), 0.22f, alpha)
        drawRidge(c, mountainsNear, t.mountain, t.accent, 0.13f, alpha)

        // Floor
        paint.shader = shaders[2]
        paint.alpha = alpha
        c.drawRect(left, horizon, right, h, paint)
        paint.shader = null

        // Perspective grid
        val cx = w / 2f
        val depth = h - horizon
        line.color = t.grid
        for (k in 0 until 18) {
            val z = k + 1f - scroll
            if (z <= 0.15f) continue
            val y = horizon + depth * (0.6f / z)
            if (y > h) continue
            val fade = ((y - horizon) / depth).coerceIn(0f, 1f)
            line.alpha = (alpha * (0.15f + 0.85f * fade)).toInt()
            line.strokeWidth = 1.2f + fade * 2.5f
            c.drawLine(left, y, right, y, line)
        }
        line.strokeWidth = 2f
        for (i in -14..14) {
            line.alpha = (alpha * 0.7f).toInt()
            val xb = cx + i * w * 0.16f
            val xt = cx + i * w * 0.012f
            c.drawLine(xt, horizon, xb, h + depth * 0.3f, line)
        }
        // Horizon glow line
        line.color = t.accent
        line.alpha = alpha
        line.strokeWidth = 3f
        c.drawLine(left, horizon, right, horizon, line)
    }

    private fun drawRidge(c: Canvas, pts: FloatArray, fillColor: Int, edge: Int, height: Float, alpha: Int) {
        path.reset()
        val span = right - left
        path.moveTo(left, horizon + 1f)
        for (i in 0 until pts.size / 2) {
            val x = left + pts[i * 2] * span
            val y = horizon - pts[i * 2 + 1] / 0.12f * height * h * 0.5f
            path.lineTo(x, y)
        }
        path.lineTo(right, horizon + 1f)
        path.close()
        paint.color = fillColor
        paint.alpha = alpha
        c.drawPath(path, paint)
        line.color = edge
        line.alpha = (alpha * android.graphics.Color.alpha(edge) / 255f).toInt()
        line.strokeWidth = 2.5f
        c.drawPath(path, line)
    }

    private fun renderSun(t: Theme, r: Float): Bitmap {
        val size = (r * 2f * pxScale).toInt().coerceIn(16, 1024)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, size.toFloat(), t.sunTop, t.sunBottom, Shader.TileMode.CLAMP)
        c.drawCircle(size / 2f, size / 2f, size / 2f, p)
        // Cut the classic horizontal slits into the lower half.
        val clear = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        var y = size * 0.52f
        var gap = size * 0.012f
        while (y < size) {
            c.drawRect(0f, y, size.toFloat(), y + gap, clear)
            y += size * 0.075f
            gap *= 1.45f
        }
        return bmp
    }

    private fun ridge(rng: Random, peaks: Int, maxH: Float, minH: Float): FloatArray {
        val n = peaks * 2 + 1
        val out = FloatArray(n * 2)
        for (i in 0 until n) {
            out[i * 2] = i / (n - 1f)
            val peak = i % 2 == 1
            out[i * 2 + 1] = if (peak) minH + rng.nextFloat() * (maxH - minH) else rng.nextFloat() * minH * 0.5f
        }
        return out
    }

    companion object {
        val THEMES = listOf(
            Theme("SUNSET", 0xFF0B0033.toInt(), 0xFFB0177A.toInt(), 0xFFFFE66B.toInt(), 0xFFFF2A6D.toInt(), 0xFFFF2A9D.toInt(), 0xFF2A0845.toInt(), 0xFFFF6AD5.toInt()),
            Theme("OCEAN", 0xFF000B2E.toInt(), 0xFF0066AA.toInt(), 0xFFB8FFFF.toInt(), 0xFF00B3FF.toInt(), 0xFF05D9E8.toInt(), 0xFF001A3D.toInt(), 0xFF6CF6FF.toInt()),
            Theme("TOXIC", 0xFF060015.toInt(), 0xFF2A5B10.toInt(), 0xFFF4FF6B.toInt(), 0xFF39FF14.toInt(), 0xFF39FF14.toInt(), 0xFF1A0B33.toInt(), 0xFFB4FF4D.toInt()),
            Theme("INFERNO", 0xFF140000.toInt(), 0xFFA82000.toInt(), 0xFFFFF07A.toInt(), 0xFFFF5E00.toInt(), 0xFFFF7A00.toInt(), 0xFF2B0505.toInt(), 0xFFFFB347.toInt()),
            Theme("VAPOR", 0xFF12002B.toInt(), 0xFF6B2FBF.toInt(), 0xFFFFB3F0.toInt(), 0xFFB967FF.toInt(), 0xFF01CDFE.toInt(), 0xFF24063D.toInt(), 0xFFFF71CE.toInt()),
            Theme("GOLD", 0xFF0A0600.toInt(), 0xFF8A5A00.toInt(), 0xFFFFFFB3.toInt(), 0xFFFFC300.toInt(), 0xFFFFD700.toInt(), 0xFF2B1A00.toInt(), 0xFFFFE680.toInt()),
        )

        fun forLevel(level: Int) = THEMES[(level - 1).coerceAtLeast(0) % THEMES.size]
    }
}
