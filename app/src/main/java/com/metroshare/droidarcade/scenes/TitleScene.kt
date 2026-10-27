package com.metroshare.droidarcade.scenes

import android.graphics.Canvas
import android.view.KeyEvent
import com.metroshare.droidarcade.ArcadeApp
import com.metroshare.droidarcade.Scene
import com.metroshare.droidarcade.audio.Song
import com.metroshare.droidarcade.audio.Sound
import com.metroshare.droidarcade.game.Gem
import com.metroshare.droidarcade.gfx.Gfx
import com.metroshare.droidarcade.gfx.SynthBackground
import com.metroshare.droidarcade.ui.ButtonGroup
import com.metroshare.droidarcade.ui.NeonButton
import kotlin.math.sin
import kotlin.random.Random

class TitleScene(app: ArcadeApp) : Scene(app) {
    private val buttons = ButtonGroup()
    private var focus = 0

    /** Decorative gems tumbling down behind the logo: x, y, speed, colour, spin, phase. */
    private val rain = Array(14) { newDrop(Random.nextFloat()) }

    private val start = NeonButton("START", Gfx.YELLOW) { startGame() }
    private val scores = NeonButton("HIGH SCORES", Gfx.CYAN) { open(ScoresScene(app)) }
    private val options = NeonButton("SETTINGS", Gfx.PINK) { open(SettingsScene(app)) }
    private val help = NeonButton("HOW TO PLAY", Gfx.PURPLE) { open(HowToScene(app)) }

    init {
        listOf(start, scores, options, help).forEach { buttons.add(it) }
        buttons.onPress = { app.haptics.tick() }
    }

    override fun enter() {
        app.background.setTheme(SynthBackground.THEMES[0])
        app.sound.playSong(Song.MENU)
    }

    override fun layout() {
        val w = 470f
        val h = 86f
        val gap = 26f
        val x = (app.vw - w) / 2f
        var y = app.vh * 0.5f
        val total = 4 * h + 3 * gap
        y = y.coerceAtMost(app.bottom - 90f - total)
        for (b in buttons.buttons) {
            b.place(x, y, x + w, y + h)
            b.textSize = 28f
            y += h + gap
        }
    }

    private fun newDrop(y: Float) = floatArrayOf(
        Random.nextFloat() * 720f,
        y * 1400f - 200f,
        50f + Random.nextFloat() * 90f,
        Random.nextInt(Gem.MAX_COLORS).toFloat(),
        (Random.nextFloat() - 0.5f) * 120f,
        Random.nextFloat() * 360f,
        36f + Random.nextFloat() * 34f,
    )

    private fun startGame() {
        app.sound.play(Sound.START)
        app.haptics.bump()
        app.go(GameScene(app))
    }

    private fun open(s: Scene) {
        app.sound.play(Sound.SELECT)
        app.go(s)
    }

    override fun update(dt: Float) {
        for (i in rain.indices) {
            val d = rain[i]
            d[1] += d[2] * dt
            d[5] += d[4] * dt
            if (d[1] > app.vh + 60f) rain[i] = newDrop(0f).also { it[1] = -80f }
        }
    }

    override fun draw(c: Canvas) {
        app.background.draw(c)
        val t = app.time

        for (d in rain) {
            c.save()
            c.rotate(d[5], d[0], d[1])
            app.sprites.draw(c, d[3].toInt(), d[0], d[1], d[6], t, alpha = 150)
            c.restore()
        }

        // Hi-score strip
        val top = app.top
        Gfx.glowText(c, "HI-SCORE", app.vw / 2f, top + 34f, 20f, Gfx.PINK)
        Gfx.glowText(c, app.scores.topScore.toString().padStart(7, '0'), app.vw / 2f, top + 72f, 30f, Gfx.WHITE, Gfx.CYAN)

        // Logo
        val logoY = (app.vh * 0.22f).coerceAtLeast(top + 210f)
        val bob = sin(t * 2f) * 6f
        val chrome = intArrayOf(0xFFFFFFFF.toInt(), 0xFF9CF6FF.toInt(), 0xFF3A7BFF.toInt(), 0xFFFF6AD5.toInt(), 0xFFFFE66B.toInt())
        Gfx.gradientText(c, "GEM", app.vw / 2f, logoY + bob, 132f, chrome, extrude = 7, extrudeColor = 0xFF3B0A57.toInt())
        Gfx.gradientText(c, "DROP", app.vw / 2f, logoY + 150f + bob, 132f, chrome, extrude = 7, extrudeColor = 0xFF3B0A57.toInt())
        // Sparkles on the logo
        for (k in 0 until 3) {
            val phase = (t * 0.8f + k * 0.33f) % 1f
            val a = (sin(phase * Math.PI.toFloat()) * 255).toInt()
            val sx = app.vw / 2f + (k - 1) * 190f + sin(k * 7f) * 40f
            val sy = logoY - 70f + k * 70f + bob
            sparkle(c, sx, sy, 22f * (0.5f + phase), a)
        }
        Gfx.outlineText(c, "DROID ARCADE", app.vw / 2f, logoY + 236f + bob, 34f, Gfx.CYAN, shadow = 4f)

        buttons.draw(c, t)
        buttons.buttons.getOrNull(focus)?.let { if (usingKeys) it.draw(c, t, highlight = true) }

        // Footer
        val blink = ((t * 1.6f).toInt() % 2) == 0
        if (blink) Gfx.glowText(c, "FREE PLAY", app.vw / 2f, app.bottom - 46f, 22f, Gfx.YELLOW, Gfx.ORANGE)
        Gfx.glowText(c, "© 2026 METROSHARE", app.vw / 2f, app.bottom - 10f, 16f, 0xFFB8A9D9.toInt(), Gfx.PURPLE, glowRadius = 4f)
    }

    private fun sparkle(c: Canvas, x: Float, y: Float, r: Float, alpha: Int) {
        val col = Gfx.withAlpha(Gfx.WHITE, alpha)
        Gfx.line(c, x - r, y, x + r, y, col, 4f)
        Gfx.line(c, x, y - r, x, y + r, col, 4f)
        Gfx.circle(c, x, y, 5f, col)
    }

    private var usingKeys = false

    override fun pointerDown(id: Int, x: Float, y: Float) {
        usingKeys = false
        buttons.down(id, x, y)
    }

    override fun pointerMove(id: Int, x: Float, y: Float) = buttons.move(id, x, y)
    override fun pointerUp(id: Int, x: Float, y: Float) {
        buttons.up(id, x, y)
    }

    override fun pointerCancel() = buttons.cancel()

    override fun back() = app.quit()

    override fun key(keyCode: Int, down: Boolean): Boolean {
        if (!down) return false
        val n = buttons.buttons.size
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> { usingKeys = true; focus = (focus + n - 1) % n; app.sound.play(Sound.TICK) }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> { usingKeys = true; focus = (focus + 1) % n; app.sound.play(Sound.TICK) }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_SPACE -> buttons.buttons[if (usingKeys) focus else 0].onClick()
            else -> return false
        }
        return true
    }
}
