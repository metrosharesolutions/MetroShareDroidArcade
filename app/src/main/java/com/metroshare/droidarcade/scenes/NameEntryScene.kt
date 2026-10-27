package com.metroshare.droidarcade.scenes

import android.graphics.Canvas
import android.graphics.RectF
import android.view.KeyEvent
import com.metroshare.droidarcade.ArcadeApp
import com.metroshare.droidarcade.Scene
import com.metroshare.droidarcade.audio.Song
import com.metroshare.droidarcade.audio.Sound
import com.metroshare.droidarcade.data.ScoreEntry
import com.metroshare.droidarcade.gfx.Gfx
import com.metroshare.droidarcade.gfx.SynthBackground
import com.metroshare.droidarcade.ui.ButtonGroup
import com.metroshare.droidarcade.ui.NeonButton

/** Classic three-letter initials entry with an on-screen alphabet grid. */
class NameEntryScene(
    app: ArcadeApp,
    private val score: Long,
    private val level: Int,
    private val difficulty: Char,
    private val bestChain: Int,
) : Scene(app) {

    private val keys = ('A'..'Z').map { it.toString() } + listOf(".", DEL)
    private val keyRects = List(keys.size) { RectF() }
    private val name = StringBuilder(app.settings.lastInitials.trim().take(3))
    private val rank = app.scores.entries.indexOfFirst { score > it.score }.let { if (it < 0) app.scores.entries.size else it }
    private val buttons = ButtonGroup()
    private val endButton = buttons.add(NeonButton("END", Gfx.GREEN) { finish() })
    private var pressedKey = -1
    private var focus = 0
    private var done = false

    override val backgroundSpeed = 0.6f

    override fun enter() {
        app.background.setTheme(SynthBackground.THEMES[5])
        app.sound.playSong(Song.MENU)
        app.sound.play(Sound.LEVEL_UP)
    }

    override fun layout() {
        val cols = 7
        val ks = 84f
        val gap = 10f
        val gridW = cols * ks + (cols - 1) * gap
        val x0 = (app.vw - gridW) / 2f
        val endH = 84f
        val endY = app.bottom - endH - 20f
        val gridH = 4 * ks + 3 * gap
        val y0 = (endY - 30f - gridH).coerceAtLeast(app.top + 520f)
        for (i in keys.indices) {
            val cx = i % cols
            val cy = i / cols
            val l = x0 + cx * (ks + gap)
            val t = y0 + cy * (ks + gap)
            keyRects[i].set(l, t, l + ks, t + ks)
        }
        endButton.place(170f, endY, 550f, endY + endH)
        endButton.textSize = 30f
    }

    private fun type(i: Int) {
        val k = keys[i]
        if (k == DEL) {
            if (name.isNotEmpty()) name.setLength(name.length - 1)
            app.sound.play(Sound.BACK)
        } else if (name.length < 3) {
            name.append(k)
            app.sound.play(Sound.TICK)
            if (name.length == 3) app.sound.play(Sound.SELECT)
        }
        app.haptics.tick()
    }

    private fun finish() {
        if (done) return
        done = true
        val initials = name.toString().ifBlank { "AAA" }
        app.settings.lastInitials = initials
        val r = app.scores.add(ScoreEntry(initials, score, level, difficulty))
        app.sound.play(Sound.START)
        app.go(ScoresScene(app, r))
    }

    override fun update(dt: Float) {}

    override fun draw(c: Canvas) {
        app.background.draw(c)
        val t = app.time
        val cx = app.vw / 2f
        val top = app.top

        val title = if (rank == 0) "NEW HIGH SCORE!" else "GREAT SCORE!"
        val col = Gfx.RAINBOW[((t * 6f).toInt()) % Gfx.RAINBOW.size]
        Gfx.outlineText(c, title, cx, top + 90f, 38f, col)
        Gfx.glowText(c, "YOU RANK ${ScoresScene.ordinal(rank + 1)}", cx, top + 150f, 26f, Gfx.WHITE, Gfx.CYAN)
        Gfx.outlineText(c, score.toString(), cx, top + 232f, 52f, Gfx.YELLOW)
        Gfx.glowText(c, "LEVEL $level   CHAIN x$bestChain", cx, top + 280f, 18f, Gfx.WHITE, Gfx.PINK)
        Gfx.glowText(c, "ENTER YOUR INITIALS", cx, top + 345f, 22f, Gfx.CYAN)

        // Three letter slots
        val slotW = 96f
        val gap = 26f
        val sx = cx - (3 * slotW + 2 * gap) / 2f
        val sy = top + 470f
        for (k in 0 until 3) {
            val l = sx + k * (slotW + gap)
            val ch = name.getOrNull(k)?.toString() ?: ""
            val cursor = k == name.length && ((t * 3f).toInt() % 2 == 0)
            Gfx.line(c, l, sy + 14f, l + slotW, sy + 14f, if (k == name.length) Gfx.YELLOW else Gfx.PINK, 6f)
            if (ch.isNotEmpty()) Gfx.outlineText(c, ch, l + slotW / 2f, sy, 76f, Gfx.WHITE)
            else if (cursor) Gfx.outlineText(c, "_", l + slotW / 2f, sy, 76f, Gfx.YELLOW)
        }

        for (i in keys.indices) {
            val r = keyRects[i]
            val pressed = i == pressedKey
            val focused = i == focus && usingKeys
            Gfx.panel(c, r, 12f, if (pressed) Gfx.withAlpha(Gfx.CYAN, 220) else 0xD90A0018.toInt(), if (keys[i] == DEL) Gfx.PINK else if (focused) Gfx.YELLOW else Gfx.CYAN)
            if (keys[i] == DEL) {
                Gfx.icon(c, Gfx.Icon.LEFT, r.centerX(), r.centerY(), 30f, if (pressed) Gfx.INK else Gfx.PINK)
            } else {
                Gfx.glowText(c, keys[i], r.centerX(), r.centerY() + 16f, 32f, if (pressed) Gfx.INK else Gfx.WHITE, Gfx.CYAN, glowRadius = if (pressed) 0f else 8f)
            }
        }
        endButton.draw(c, t, highlight = name.length == 3)
    }

    private var usingKeys = false

    private fun keyAt(x: Float, y: Float) = keyRects.indexOfFirst { x >= it.left - 4f && x <= it.right + 4f && y >= it.top - 4f && y <= it.bottom + 4f }

    override fun pointerDown(id: Int, x: Float, y: Float) {
        usingKeys = false
        if (buttons.down(id, x, y)) return
        pressedKey = keyAt(x, y)
    }

    override fun pointerMove(id: Int, x: Float, y: Float) {
        buttons.move(id, x, y)
        if (pressedKey >= 0 && keyAt(x, y) != pressedKey) pressedKey = -1
    }

    override fun pointerUp(id: Int, x: Float, y: Float) {
        if (buttons.up(id, x, y)) return
        if (pressedKey >= 0 && keyAt(x, y) == pressedKey) type(pressedKey)
        pressedKey = -1
    }

    override fun pointerCancel() {
        buttons.cancel()
        pressedKey = -1
    }

    override fun back() = finish()

    override fun key(keyCode: Int, down: Boolean): Boolean {
        if (!down) return false
        usingKeys = true
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> focus = (focus + keys.size - 1) % keys.size
            KeyEvent.KEYCODE_DPAD_RIGHT -> focus = (focus + 1) % keys.size
            KeyEvent.KEYCODE_DPAD_UP -> focus = (focus + keys.size - 7) % keys.size
            KeyEvent.KEYCODE_DPAD_DOWN -> focus = (focus + 7) % keys.size
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> type(focus)
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_DEL -> type(keys.indexOf(DEL))
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_ENTER -> finish()
            else -> {
                if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
                    type(keyCode - KeyEvent.KEYCODE_A)
                } else {
                    return false
                }
            }
        }
        return true
    }

    companion object {
        private const val DEL = "DEL"
    }
}
