package com.metroshare.droidarcade.scenes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.metroshare.droidarcade.ArcadeApp
import com.metroshare.droidarcade.Scene
import com.metroshare.droidarcade.audio.Song
import com.metroshare.droidarcade.audio.Sound
import com.metroshare.droidarcade.game.Gem
import com.metroshare.droidarcade.gfx.Gfx
import com.metroshare.droidarcade.gfx.SynthBackground
import com.metroshare.droidarcade.ui.ArcadeButton
import com.metroshare.droidarcade.ui.ButtonGroup
import com.metroshare.droidarcade.ui.NeonButton

class HowToScene(app: ArcadeApp) : Scene(app) {
    private val buttons = ButtonGroup()
    private val panel = RectF()
    private var sectionH = 140f
    private var y0 = 0f

    private val mini = listOf(
        ArcadeButton(Gfx.Icon.LEFT, "", 0xFFFFC21A.toInt()) to "",
        ArcadeButton(Gfx.Icon.RIGHT, "", 0xFFFFC21A.toInt()) to "MOVE",
        ArcadeButton(Gfx.Icon.DOWN, "", 0xFF22D46A.toInt()) to "FAST",
        ArcadeButton(Gfx.Icon.ROTATE, "", 0xFFFF2D55.toInt()) to "ROTATE",
        ArcadeButton(Gfx.Icon.DROP, "", 0xFF2F8CFF.toInt()) to "DROP",
    )

    private val sections = listOf(
        "LINE UP 3 OR MORE GEMS OF ONE COLOR - ACROSS, DOWN OR DIAGONAL - TO BLAST THEM.",
        "ROTATE CYCLES THE 3 FALLING GEMS. FALLING GEMS CAN SET OFF CHAINS FOR BIG MULTIPLIERS!",
        "A MAGIC GEM WIPES OUT EVERY GEM OF THE COLOR IT LANDS ON.",
        "CLEAR GEMS TO LEVEL UP. THE LEVELS NEVER END - THEY JUST GET FASTER!",
    )

    init {
        buttons.add(NeonButton("BACK", Gfx.CYAN) { back() })
        buttons.onPress = { app.haptics.tick() }
    }

    override val backgroundSpeed = 0.5f

    override fun enter() {
        app.background.setTheme(SynthBackground.THEMES[2])
        app.sound.playSong(Song.MENU)
    }

    override fun layout() {
        val bh = 84f
        val by = app.bottom - bh - 16f
        buttons.buttons[0].place(170f, by, 550f, by + bh)
        y0 = app.top + 130f
        val controlsH = 250f
        sectionH = ((by - 24f - controlsH - y0) / sections.size).coerceIn(120f, 170f)
        panel.set(20f, y0 - 10f, app.vw - 20f, y0 + sectionH * sections.size)
        val cy = panel.bottom + 90f
        var x = 78f
        for ((b, _) in mini) {
            b.place(x, cy, 40f)
            x += 141f
        }
    }

    override fun update(dt: Float) {}

    override fun draw(c: Canvas) {
        app.background.draw(c)
        val t = app.time
        Gfx.outlineText(c, "HOW TO PLAY", app.vw / 2f, app.top + 90f, 46f, Gfx.YELLOW)
        Gfx.panel(c, panel, 16f, 0xD90A0018.toInt(), Gfx.GREEN)

        val s = app.sprites
        val g = 50f
        for ((i, text) in sections.withIndex()) {
            val top = y0 + i * sectionH
            val cy = top + sectionH / 2f
            when (i) {
                0 -> for (k in 0 until 3) s.draw(c, 0, 70f + k * 46f, cy, g, t)
                1 -> {
                    for (k in 0 until 3) s.draw(c, intArrayOf(2, 3, 4)[k], 80f, cy - 46f + k * 46f, g, t)
                    Gfx.icon(c, Gfx.Icon.RIGHT, 125f, cy, 22f, Gfx.WHITE)
                    for (k in 0 until 3) s.draw(c, intArrayOf(4, 2, 3)[k], 170f, cy - 46f + k * 46f, g, t)
                }
                2 -> {
                    s.draw(c, Gem.MAGIC, 125f, cy - 20f, g * 1.2f, t)
                    for (k in 0 until 3) s.draw(c, 5, 80f + k * 46f, cy + 34f, g * 0.8f, t)
                }
                3 -> {
                    val lv = ((t * 1.2f).toInt() % 99) + 1
                    Gfx.outlineText(c, "LV", 125f, cy - 6f, 30f, Gfx.CYAN)
                    Gfx.outlineText(c, lv.toString(), 125f, cy + 38f, 34f, Gfx.WHITE)
                }
            }
            val lines = Gfx.wrap(text, 17f, 450f)
            var y = cy - (lines.size - 1) * 14f + 8f
            for (line in lines) {
                Gfx.glowText(c, line, 232f, y, 17f, Gfx.WHITE, Gfx.CYAN, align = Paint.Align.LEFT, glowRadius = 4f)
                y += 28f
            }
            if (i < sections.size - 1) {
                Gfx.line(c, 40f, top + sectionH, app.vw - 40f, top + sectionH, Gfx.withAlpha(Gfx.GREEN, 60), 2f)
            }
        }

        Gfx.glowText(c, "CONTROLS", app.vw / 2f, panel.bottom + 42f, 22f, Gfx.PINK)
        for ((b, label) in mini) {
            b.draw(c, showLabel = false)
            if (label.isNotEmpty()) {
                val lx = if (label == "MOVE") b.cx - 70f else b.cx
                Gfx.glowText(c, label, lx, b.cy + 80f, 15f, Gfx.WHITE, b.color, glowRadius = 4f)
            }
        }
        val swipe = "OR SWIPE THE BOARD: DRAG MOVES, TAP ROTATES, FLICK DOWN DROPS"
        val lines = Gfx.wrap(swipe, 14f, 640f)
        var y = panel.bottom + 200f
        for (line in lines) {
            Gfx.glowText(c, line, app.vw / 2f, y, 14f, 0xFFB8A9D9.toInt(), Gfx.PURPLE, glowRadius = 3f)
            y += 24f
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
        if (down) back()
        return true
    }
}
