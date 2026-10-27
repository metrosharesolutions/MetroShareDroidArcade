package com.metroshare.droidarcade.scenes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.KeyEvent
import com.metroshare.droidarcade.ArcadeApp
import com.metroshare.droidarcade.Scene
import com.metroshare.droidarcade.audio.Song
import com.metroshare.droidarcade.audio.Sound
import com.metroshare.droidarcade.data.Settings
import com.metroshare.droidarcade.game.Difficulty
import com.metroshare.droidarcade.gfx.Gfx
import com.metroshare.droidarcade.gfx.SynthBackground
import com.metroshare.droidarcade.ui.ButtonGroup
import com.metroshare.droidarcade.ui.NeonButton

class SettingsScene(app: ArcadeApp) : Scene(app) {

    private abstract inner class Row(val label: String, val color: Int) {
        val rect = RectF()
        abstract fun value(): String
        abstract fun change(dir: Int)
        open val isSlider = false
        open fun level() = 0
    }

    private inner class Slider(label: String, color: Int, val get: () -> Int, val set: (Int) -> Unit, val preview: () -> Unit) : Row(label, color) {
        override val isSlider = true
        override fun level() = get()
        override fun value() = get().toString()
        override fun change(dir: Int) {
            set((get() + dir).coerceIn(0, 10))
            preview()
        }
    }

    private inner class Toggle(label: String, color: Int, val get: () -> Boolean, val set: (Boolean) -> Unit, val on: String = "ON", val off: String = "OFF") : Row(label, color) {
        override fun value() = if (get()) on else off
        override fun change(dir: Int) {
            set(!get())
            app.sound.play(Sound.TICK)
        }
    }

    private inner class Choice(label: String, color: Int, val count: Int, val get: () -> Int, val set: (Int) -> Unit, val names: (Int) -> String, val wrap: Boolean = true) : Row(label, color) {
        override fun value() = names(get())
        override fun change(dir: Int) {
            var v = get() + dir
            v = if (wrap) (v + count) % count else v.coerceIn(0, count - 1)
            set(v)
            app.sound.play(Sound.TICK)
        }
    }

    private inner class Action(label: String, color: Int, val run: () -> Unit) : Row(label, color) {
        override fun value() = ""
        override fun change(dir: Int) = run()
    }

    private val s = app.settings
    private var confirmReset = 0f
    private var resetDone = 0f

    private val rows: List<Row> = listOf(
        Slider("MUSIC", Gfx.CYAN, { s.musicVolume }, { s.musicVolume = it }, {}),
        Slider("SOUND FX", Gfx.CYAN, { s.sfxVolume }, { s.sfxVolume = it }, { app.sound.play(Sound.SELECT) }),
        Toggle("VIBRATION", Gfx.PINK, { s.vibration }, { s.vibration = it; if (it) app.haptics.bump() }),
        Choice("DIFFICULTY", Gfx.YELLOW, Difficulty.entries.size, { s.difficulty.ordinal }, { s.difficulty = Difficulty.entries[it] }, { Difficulty.entries[it].label }),
        Choice("START LV", Gfx.YELLOW, Settings.MAX_START_LEVEL, { s.startLevel - 1 }, { s.startLevel = it + 1 }, { (it + 1).toString() }, wrap = false),
        Toggle("GHOST PIECE", Gfx.GREEN, { s.ghost }, { s.ghost = it }),
        Toggle("SWIPE CTRL", Gfx.GREEN, { s.swipe }, { s.swipe = it }),
        Toggle("BUTTONS", Gfx.GREEN, { s.leftHanded }, { s.leftHanded = it }, on = "LEFTY", off = "RIGHTY"),
        Toggle("CRT FILTER", Gfx.PURPLE, { s.scanlines }, { s.scanlines = it }),
        Action("RESET HIGH SCORES", Gfx.PINK) { reset() },
    )

    private val buttons = ButtonGroup()
    private var focus = -1
    private var rowDown = -1
    private var downX = 0f

    init {
        buttons.add(NeonButton("BACK", Gfx.CYAN) { back() })
        buttons.onPress = { app.haptics.tick() }
    }

    override val backgroundSpeed = 0.4f

    override fun enter() {
        app.background.setTheme(SynthBackground.THEMES[1])
        app.sound.playSong(Song.MENU)
    }

    override fun layout() {
        val bh = 84f
        val by = app.bottom - bh - 16f
        buttons.buttons[0].place(170f, by, 550f, by + bh)
        val top = app.top + 150f
        val rh = ((by - 24f - top) / rows.size).coerceIn(60f, 84f)
        for ((i, r) in rows.withIndex()) {
            r.rect.set(24f, top + i * rh, app.vw - 24f, top + (i + 1) * rh - 10f)
        }
    }

    private fun reset() {
        if (confirmReset > 0f) {
            app.scores.reset()
            confirmReset = 0f
            resetDone = 2f
            app.sound.play(Sound.BACK)
            app.haptics.buzz(50)
        } else {
            confirmReset = 3f
            app.sound.play(Sound.DANGER)
        }
    }

    override fun update(dt: Float) {
        if (confirmReset > 0f) confirmReset -= dt
        if (resetDone > 0f) resetDone -= dt
    }

    override fun draw(c: Canvas) {
        app.background.draw(c)
        val t = app.time
        Gfx.outlineText(c, "SETTINGS", app.vw / 2f, app.top + 100f, 52f, Gfx.PINK)

        for ((i, r) in rows.withIndex()) {
            val focused = i == focus || i == rowDown
            Gfx.panel(c, r.rect, 14f, if (focused) 0xE62A0845.toInt() else 0xCC0A0018.toInt(), r.color, if (focused) 255 else 170)
            val cy = r.rect.centerY()
            if (r is Action) {
                val text = when {
                    resetDone > 0f -> "SCORES RESET!"
                    confirmReset > 0f -> "TAP AGAIN TO CONFIRM"
                    else -> r.label
                }
                val col = if (confirmReset > 0f && ((t * 5f).toInt() % 2 == 0)) Gfx.YELLOW else r.color
                Gfx.glowText(c, text, r.rect.centerX(), cy + 10f, 20f, Gfx.WHITE, col)
                continue
            }
            Gfx.glowText(c, r.label, r.rect.left + 22f, cy + 10f, 20f, Gfx.WHITE, r.color, align = Paint.Align.LEFT)
            val lx = 430f
            val rx = 664f
            Gfx.icon(c, Gfx.Icon.LEFT, lx, cy, 26f, r.color)
            Gfx.icon(c, Gfx.Icon.RIGHT, rx, cy, 26f, r.color)
            val mid = (lx + rx) / 2f
            if (r.isSlider) {
                val n = r.level()
                val bw = 14f
                val gap = 4f
                val total = 10 * bw + 9 * gap
                var x = mid - total / 2f
                for (k in 0 until 10) {
                    val h = 12f + k * 2.4f
                    val col = if (k < n) Gfx.lerpColor(Gfx.GREEN, Gfx.PINK, k / 9f) else 0xFF2A2240.toInt()
                    Gfx.rect(c, x, cy + 16f - h, x + bw, cy + 16f, col)
                    x += bw + gap
                }
            } else {
                Gfx.glowText(c, r.value(), mid, cy + 10f, 20f, Gfx.YELLOW, Gfx.ORANGE)
            }
        }
        buttons.draw(c, t)
    }

    private fun rowAt(x: Float, y: Float) = rows.indexOfFirst { y >= it.rect.top - 5f && y <= it.rect.bottom + 5f && x >= it.rect.left && x <= it.rect.right }

    override fun pointerDown(id: Int, x: Float, y: Float) {
        focus = -1
        if (buttons.down(id, x, y)) return
        rowDown = rowAt(x, y)
        downX = x
    }

    override fun pointerMove(id: Int, x: Float, y: Float) = buttons.move(id, x, y)

    override fun pointerUp(id: Int, x: Float, y: Float) {
        if (buttons.up(id, x, y)) return
        val i = rowAt(x, y)
        if (i >= 0 && i == rowDown) {
            val r = rows[i]
            val dir = if (r !is Action && x < 500f && x > 360f) -1 else 1
            r.change(dir)
        }
        rowDown = -1
    }

    override fun pointerCancel() {
        buttons.cancel()
        rowDown = -1
    }

    override fun back() {
        app.sound.play(Sound.BACK)
        app.go(TitleScene(app))
    }

    override fun key(keyCode: Int, down: Boolean): Boolean {
        if (!down) return false
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> focus = if (focus <= 0) rows.size - 1 else focus - 1
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> focus = (focus + 1) % rows.size
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> rows.getOrNull(focus)?.let { if (it !is Action) it.change(-1) }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> rows.getOrNull(focus)?.let { if (it !is Action) it.change(1) }
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> rows.getOrNull(focus)?.change(1)
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE -> back()
            else -> return false
        }
        return true
    }
}
