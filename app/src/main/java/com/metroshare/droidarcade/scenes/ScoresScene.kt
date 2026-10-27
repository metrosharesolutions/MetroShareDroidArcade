package com.metroshare.droidarcade.scenes

import android.graphics.Canvas
import android.graphics.Paint
import android.view.KeyEvent
import com.metroshare.droidarcade.ArcadeApp
import com.metroshare.droidarcade.Scene
import com.metroshare.droidarcade.audio.Song
import com.metroshare.droidarcade.audio.Sound
import com.metroshare.droidarcade.gfx.Gfx
import com.metroshare.droidarcade.gfx.SynthBackground
import com.metroshare.droidarcade.ui.ButtonGroup
import com.metroshare.droidarcade.ui.NeonButton

/** The top-10 table. [highlight] is the 0-based rank of a freshly entered score, or -1. */
class ScoresScene(app: ArcadeApp, private val highlight: Int = -1) : Scene(app) {
    private val buttons = ButtonGroup()
    private var rowTop = 0f
    private var rowH = 62f

    init {
        buttons.add(NeonButton("PLAY", Gfx.YELLOW) {
            app.sound.play(Sound.START)
            app.go(GameScene(app))
        })
        buttons.add(NeonButton("MENU", Gfx.CYAN) { back() })
        buttons.onPress = { app.haptics.tick() }
    }

    override val backgroundSpeed = 0.5f

    override fun enter() {
        app.background.setTheme(SynthBackground.THEMES[4])
        app.sound.playSong(Song.MENU)
    }

    override fun layout() {
        val bh = 84f
        val by = app.bottom - bh - 20f
        buttons.buttons[0].place(40f, by, 340f, by + bh)
        buttons.buttons[1].place(380f, by, 680f, by + bh)
        rowTop = app.top + 250f
        rowH = ((by - 40f - rowTop) / 10f).coerceIn(48f, 74f)
    }

    override fun update(dt: Float) {}

    override fun draw(c: Canvas) {
        app.background.draw(c)
        val t = app.time
        val cx = app.vw / 2f

        // Rainbow-cycling title, one colour per letter.
        val title = "HIGH SCORES"
        val size = 50f
        var x = cx - Gfx.measure(title, size) / 2f
        for ((k, ch) in title.withIndex()) {
            val col = Gfx.RAINBOW[((t * 8f).toInt() + k) % Gfx.RAINBOW.size]
            Gfx.outlineText(c, ch.toString(), x + size / 2f, app.top + 110f, size, col)
            x += Gfx.measure(ch.toString(), size)
        }

        val hy = rowTop - 34f
        Gfx.glowText(c, "RANK", 50f, hy, 20f, Gfx.CYAN, align = Paint.Align.LEFT)
        Gfx.glowText(c, "NAME", 230f, hy, 20f, Gfx.CYAN, align = Paint.Align.LEFT)
        Gfx.glowText(c, "SCORE", 560f, hy, 20f, Gfx.CYAN, align = Paint.Align.RIGHT)
        Gfx.glowText(c, "LV", 672f, hy, 20f, Gfx.CYAN, align = Paint.Align.RIGHT)

        val entries = app.scores.entries
        for ((i, e) in entries.withIndex()) {
            val y = rowTop + (i + 0.75f) * rowH
            val col = Gfx.RAINBOW[i % Gfx.RAINBOW.size]
            val isNew = i == highlight
            if (isNew && ((t * 4f).toInt() % 2 == 1)) continue
            val textCol = if (isNew) Gfx.WHITE else col
            val s = 26f
            Gfx.glowText(c, ordinal(i + 1), 50f, y, s, textCol, col, align = Paint.Align.LEFT)
            Gfx.glowText(c, e.name, 230f, y, s, textCol, col, align = Paint.Align.LEFT)
            Gfx.glowText(c, e.score.toString(), 560f, y, s, textCol, col, align = Paint.Align.RIGHT)
            Gfx.glowText(c, e.level.toString(), 672f, y, s, textCol, col, align = Paint.Align.RIGHT)
        }
        if (highlight < 0 && app.scores.entries.isNotEmpty()) {
            val blink = ((t * 1.5f).toInt() % 2) == 0
            if (blink) Gfx.glowText(c, "CAN YOU BEAT THEM?", cx, rowTop + 10.5f * rowH, 18f, Gfx.YELLOW, Gfx.ORANGE)
        }
        buttons.draw(c, t)
    }

    override fun pointerDown(id: Int, x: Float, y: Float) {
        buttons.down(id, x, y)
    }

    override fun pointerMove(id: Int, x: Float, y: Float) = buttons.move(id, x, y)
    override fun pointerUp(id: Int, x: Float, y: Float) {
        buttons.up(id, x, y)
    }

    override fun pointerCancel() = buttons.cancel()

    override fun back() {
        app.sound.play(Sound.BACK)
        app.go(TitleScene(app))
    }

    override fun key(keyCode: Int, down: Boolean): Boolean {
        if (!down) return false
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_SPACE -> buttons.buttons[0].onClick()
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE -> back()
            else -> return false
        }
        return true
    }

    companion object {
        fun ordinal(n: Int): String = when {
            n % 100 in 11..13 -> "${n}TH"
            n % 10 == 1 -> "${n}ST"
            n % 10 == 2 -> "${n}ND"
            n % 10 == 3 -> "${n}RD"
            else -> "${n}TH"
        }
    }
}
