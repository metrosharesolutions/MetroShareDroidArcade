package com.metroshare.droidarcade

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import com.metroshare.droidarcade.audio.Sound
import com.metroshare.droidarcade.audio.SoundSystem
import com.metroshare.droidarcade.data.Haptics
import com.metroshare.droidarcade.data.Leaderboard
import com.metroshare.droidarcade.data.Settings
import com.metroshare.droidarcade.gfx.GemSprites
import com.metroshare.droidarcade.gfx.Gfx
import com.metroshare.droidarcade.gfx.SynthBackground
import com.metroshare.droidarcade.scenes.TitleScene
import kotlin.math.max
import kotlin.math.roundToInt

/** A screen of the game: title, gameplay, settings... Coordinates are virtual units. */
abstract class Scene(val app: ArcadeApp) {
    /** How fast the background grid scrolls on this screen. */
    open val backgroundSpeed = 1f

    open fun enter() {}
    open fun layout() {}
    abstract fun update(dt: Float)
    abstract fun draw(c: Canvas)
    open fun pointerDown(id: Int, x: Float, y: Float) {}
    open fun pointerMove(id: Int, x: Float, y: Float) {}
    open fun pointerUp(id: Int, x: Float, y: Float) {}
    open fun pointerCancel() {}
    open fun back() {}
    open fun key(keyCode: Int, down: Boolean): Boolean = false
    open fun pause() {}
}

/**
 * Owns the shared services (settings, scores, audio, sprites) and the current scene, maps the
 * physical screen onto a 720-unit-wide virtual canvas, and runs fade transitions.
 */
class ArcadeApp(val context: Context, private val exit: () -> Unit) {
    private val prefs = context.getSharedPreferences("droid_arcade", Context.MODE_PRIVATE)
    val settings = Settings(prefs)
    val scores = Leaderboard(prefs)
    val sound = SoundSystem(context, settings)
    val haptics = Haptics(context, settings)
    val background = SynthBackground()

    var sprites = GemSprites(64)
        private set

    /** Virtual canvas size. Width is fixed; height follows the screen's aspect ratio. */
    val vw = VIRTUAL_WIDTH
    var vh = 1280f
        private set
    var scale = 1f
        private set
    private var offX = 0f
    private var offY = 0f
    private var physW = 0
    private var physH = 0
    private var insetTopPx = 0
    private var insetBottomPx = 0

    /** Visible area edges in virtual units (may extend beyond 0..vw on wide screens). */
    val screenLeft get() = -offX / scale
    val screenRight get() = vw + offX / scale

    /** Safe content area, clear of notches and gesture bars. */
    val top get() = insetTopPx / scale + 8f
    val bottom get() = vh - insetBottomPx / scale - 8f

    var time = 0f
        private set

    var scene: Scene
        private set
    private var pending: Scene? = null
    private var fade = 1f
    private var fadingOut = false

    private val overlayPaint = Paint()
    private var scanShader: Shader? = null
    private var vignette: Shader? = null

    init {
        try {
            Gfx.font = Typeface.createFromAsset(context.assets, "fonts/PressStart2P-Regular.ttf")
        } catch (_: Exception) {
            Gfx.font = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        scene = TitleScene(this)
        scene.enter()
    }

    fun go(next: Scene) {
        if (pending != null) return
        pending = next
        fadingOut = true
    }

    fun resize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        physW = w
        physH = h
        scale = w / vw
        vh = h / scale
        offX = 0f
        offY = 0f
        if (vh < MIN_VIRTUAL_HEIGHT) {
            // Wide screen (tablet, foldable): fit height and centre horizontally.
            scale = h / MIN_VIRTUAL_HEIGHT
            vh = MIN_VIRTUAL_HEIGHT
            offX = (w - vw * scale) / 2f
        }
        background.layout(vw, vh, screenLeft, screenRight, 0.42f, scale)
        val spritePx = (MAX_CELL * scale).roundToInt().coerceAtLeast(16)
        if (spritePx != sprites.sizePx) {
            sprites.recycle()
            sprites = GemSprites(spritePx)
        }
        buildOverlay()
        scene.layout()
    }

    fun setInsets(topPx: Int, bottomPx: Int) {
        insetTopPx = topPx
        insetBottomPx = bottomPx
        scene.layout()
    }

    private fun buildOverlay() {
        val period = max(2, (scale * 2f).roundToInt())
        val bmp = Bitmap.createBitmap(1, period, Bitmap.Config.ARGB_8888)
        bmp.setPixel(0, period - 1, 0x55000000)
        scanShader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val r = max(physW, physH) * 0.75f
        vignette = RadialGradient(physW / 2f, physH / 2f, r, intArrayOf(0x00000000, 0x00000000, 0x88000000.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    }

    fun update(dt: Float) {
        time += dt
        background.update(dt, scene.backgroundSpeed)
        val p = pending
        if (fadingOut) {
            fade -= dt / FADE_TIME
            if (fade <= 0f) {
                fade = 0f
                fadingOut = false
                if (p != null) {
                    scene = p
                    pending = null
                    scene.enter()
                    scene.layout()
                }
            }
        } else if (fade < 1f) {
            fade = (fade + dt / FADE_TIME).coerceAtMost(1f)
        }
        scene.update(dt)
    }

    fun render(c: Canvas) {
        c.save()
        c.translate(offX, offY)
        c.scale(scale, scale)
        scene.draw(c)
        c.restore()
        if (settings.scanlines) {
            overlayPaint.shader = scanShader
            c.drawRect(0f, 0f, physW.toFloat(), physH.toFloat(), overlayPaint)
            overlayPaint.shader = vignette
            c.drawRect(0f, 0f, physW.toFloat(), physH.toFloat(), overlayPaint)
            overlayPaint.shader = null
        }
        if (fade < 1f) {
            overlayPaint.shader = null
            overlayPaint.color = 0xFF000000.toInt()
            overlayPaint.alpha = ((1f - fade) * 255).toInt()
            c.drawRect(0f, 0f, physW.toFloat(), physH.toFloat(), overlayPaint)
        }
    }

    fun touch(e: MotionEvent) {
        if (pending != null) {
            if (e.actionMasked == MotionEvent.ACTION_DOWN) scene.pointerCancel()
            return
        }
        fun vx(i: Int) = (e.getX(i) - offX) / scale
        fun vy(i: Int) = (e.getY(i) - offY) / scale
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                scene.pointerDown(e.getPointerId(i), vx(i), vy(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) scene.pointerMove(e.getPointerId(i), vx(i), vy(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                scene.pointerUp(e.getPointerId(i), vx(i), vy(i))
            }
            MotionEvent.ACTION_CANCEL -> scene.pointerCancel()
        }
    }

    fun back() {
        if (pending == null) scene.back()
    }

    fun key(keyCode: Int, down: Boolean): Boolean = pending == null && scene.key(keyCode, down)

    fun pause() {
        scene.pause()
        sound.setPaused(true)
    }

    fun resume() {
        sound.setPaused(false)
    }

    fun release() {
        sound.release()
    }

    fun quit() {
        sound.play(Sound.BACK)
        exit()
    }

    companion object {
        const val VIRTUAL_WIDTH = 720f
        const val MIN_VIRTUAL_HEIGHT = 1280f
        /** Largest on-screen gem size; sprites are rendered at this size in physical pixels. */
        const val MAX_CELL = 96f
        const val FADE_TIME = 0.22f
    }
}
