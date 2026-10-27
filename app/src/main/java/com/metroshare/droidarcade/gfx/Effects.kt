package com.metroshare.droidarcade.gfx

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Pixel-square particle bursts, pooled in flat arrays so nothing is allocated per frame. */
class Particles(private val capacity: Int = 900) {
    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val maxLife = FloatArray(capacity)
    private val size = FloatArray(capacity)
    private val color = IntArray(capacity)
    private val gravity = FloatArray(capacity)
    private var count = 0
    private val paint = Paint()

    fun burst(cx: Float, cy: Float, c: Int, n: Int, speed: Float, sz: Float = 7f, grav: Float = 900f, lifeSec: Float = 0.8f) {
        for (i in 0 until n) {
            if (count >= capacity) return
            val a = Random.nextFloat() * 6.2832f
            val s = speed * (0.3f + Random.nextFloat() * 0.9f)
            val k = count++
            x[k] = cx
            y[k] = cy
            vx[k] = cos(a) * s
            vy[k] = sin(a) * s - speed * 0.35f
            maxLife[k] = lifeSec * (0.6f + Random.nextFloat() * 0.6f)
            life[k] = maxLife[k]
            size[k] = sz * (0.6f + Random.nextFloat() * 0.8f)
            color[k] = if (Random.nextInt(4) == 0) Gfx.WHITE else c
            gravity[k] = grav
        }
    }

    /** Upward streak used for hard drops. */
    fun trail(cx: Float, top: Float, bottom: Float, c: Int) {
        var yy = top
        while (yy < bottom && count < capacity) {
            val k = count++
            x[k] = cx + (Random.nextFloat() - 0.5f) * 30f
            y[k] = yy
            vx[k] = 0f
            vy[k] = -60f - Random.nextFloat() * 80f
            maxLife[k] = 0.25f + Random.nextFloat() * 0.25f
            life[k] = maxLife[k]
            size[k] = 4f + Random.nextFloat() * 4f
            color[k] = c
            gravity[k] = 0f
            yy += 9f
        }
    }

    fun update(dt: Float) {
        var i = 0
        while (i < count) {
            life[i] -= dt
            if (life[i] <= 0f) {
                val last = --count
                x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last]
                life[i] = life[last]; maxLife[i] = maxLife[last]; size[i] = size[last]
                color[i] = color[last]; gravity[i] = gravity[last]
                continue
            }
            vy[i] += gravity[i] * dt
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            i++
        }
    }

    fun draw(c: Canvas) {
        for (i in 0 until count) {
            val t = life[i] / maxLife[i]
            paint.color = color[i]
            paint.alpha = (255 * t.coerceIn(0f, 1f)).toInt()
            val s = size[i] * (0.4f + 0.6f * t)
            c.drawRect(x[i] - s / 2, y[i] - s / 2, x[i] + s / 2, y[i] + s / 2, paint)
        }
    }

    fun clear() {
        count = 0
    }
}

/** Rising "+250" style score pop-ups. */
class Floaters {
    private class F(val text: String, var x: Float, var y: Float, val color: Int, val size: Float, var life: Float, val max: Float)

    private val items = ArrayList<F>()

    fun add(text: String, x: Float, y: Float, color: Int, size: Float = 26f, life: Float = 1.1f) {
        items.add(F(text, x, y, color, size, life, life))
    }

    fun update(dt: Float) {
        val it = items.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.life -= dt
            f.y -= 70f * dt
            if (f.life <= 0f) it.remove()
        }
    }

    fun draw(c: Canvas) {
        for (f in items) {
            val t = (f.life / f.max).coerceIn(0f, 1f)
            Gfx.outlineText(c, f.text, f.x, f.y, f.size, f.color, alpha = (255 * minOf(1f, t * 2f)).toInt(), shadow = 3f)
        }
    }

    fun clear() = items.clear()
}

/** Big centred announcement ("CHAIN x3", "LEVEL 5") that pops in and fades out. */
class Banner {
    var text = ""
        private set
    var sub = ""
        private set
    private var color = Gfx.YELLOW
    private var life = 0f
    private var max = 1f

    val active get() = life > 0f

    fun show(text: String, sub: String = "", color: Int = Gfx.YELLOW, duration: Float = 1.2f) {
        this.text = text
        this.sub = sub
        this.color = color
        life = duration
        max = duration
    }

    fun update(dt: Float) {
        if (life > 0f) life -= dt
    }

    fun draw(c: Canvas, cx: Float, cy: Float, size: Float, maxWidth: Float) {
        if (life <= 0f) return
        val age = max - life
        val pop = if (age < 0.15f) 0.4f + age / 0.15f * 0.75f else if (age < 0.25f) 1.15f - (age - 0.15f) / 0.1f * 0.15f else 1f
        val alpha = (255 * (life / 0.3f).coerceIn(0f, 1f)).toInt()
        var s = size * pop
        val w = Gfx.measure(text, s)
        if (w > maxWidth) s *= maxWidth / w
        Gfx.outlineText(c, text, cx, cy + s * 0.4f, s, color, alpha = alpha, shadow = s * 0.1f)
        if (sub.isNotEmpty()) {
            Gfx.glowText(c, sub, cx, cy + s * 0.4f + size * 0.75f, size * 0.42f, Gfx.WHITE, Gfx.CYAN, alpha = alpha)
        }
    }
}

/** Decaying random camera shake. */
class Shake {
    private var amp = 0f
    var dx = 0f
        private set
    var dy = 0f
        private set

    fun kick(a: Float) {
        amp = maxOf(amp, a)
    }

    fun update(dt: Float) {
        amp *= (1f - dt * 9f).coerceAtLeast(0f)
        if (amp < 0.3f) amp = 0f
        dx = (Random.nextFloat() * 2f - 1f) * amp
        dy = (Random.nextFloat() * 2f - 1f) * amp
    }
}
