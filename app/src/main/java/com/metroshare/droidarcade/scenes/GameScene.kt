package com.metroshare.droidarcade.scenes

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.KeyEvent
import com.metroshare.droidarcade.ArcadeApp
import com.metroshare.droidarcade.Scene
import com.metroshare.droidarcade.audio.Song
import com.metroshare.droidarcade.audio.Sound
import com.metroshare.droidarcade.game.Gem
import com.metroshare.droidarcade.game.GameEvents
import com.metroshare.droidarcade.game.GemDropGame
import com.metroshare.droidarcade.game.GemDropGame.Phase
import com.metroshare.droidarcade.game.Piece
import com.metroshare.droidarcade.gfx.Banner
import com.metroshare.droidarcade.gfx.Floaters
import com.metroshare.droidarcade.gfx.GemSprites
import com.metroshare.droidarcade.gfx.Gfx
import com.metroshare.droidarcade.gfx.Particles
import com.metroshare.droidarcade.gfx.Shake
import com.metroshare.droidarcade.gfx.SynthBackground
import com.metroshare.droidarcade.ui.ArcadeButton
import com.metroshare.droidarcade.ui.ButtonGroup
import com.metroshare.droidarcade.ui.NeonButton
import kotlin.math.abs
import kotlin.math.min

class GameScene(app: ArcadeApp) : Scene(app), GameEvents {

    private enum class Control { LEFT, RIGHT, DOWN, ROTATE, DROP }
    private enum class State { PLAYING, PAUSED, OVER }

    private val settings = app.settings
    private var game = newGame()
    private var state = State.PLAYING
    private var overTime = 0f

    override val backgroundSpeed: Float
        get() = if (state == State.PLAYING) 0.8f + min(game.level, 20) * 0.08f else 0.3f

    // ---------------------------------------------------------------- layout
    private var cell = 50f
    private val boardRect = RectF()
    private var sideX = 0f
    private var sideW = 0f
    private var controlsTop = 0f
    private val nextRect = RectF()
    private val levelRect = RectF()
    private val statsRect = RectF()

    // ---------------------------------------------------------------- controls
    private val pad = mapOf(
        Control.LEFT to ArcadeButton(Gfx.Icon.LEFT, "", 0xFFFFC21A.toInt()),
        Control.RIGHT to ArcadeButton(Gfx.Icon.RIGHT, "", 0xFFFFC21A.toInt()),
        Control.DOWN to ArcadeButton(Gfx.Icon.DOWN, "", 0xFF22D46A.toInt()),
        Control.ROTATE to ArcadeButton(Gfx.Icon.ROTATE, "ROTATE", 0xFFFF2D55.toInt()),
        Control.DROP to ArcadeButton(Gfx.Icon.DROP, "DROP", 0xFF2F8CFF.toInt()),
    )
    private val pointerControl = HashMap<Int, Control>()
    private val keyHeld = HashSet<Control>()
    private val held = HashSet<Control>()
    private var dasDir = 0
    private var dasTimer = 0f
    private var repeatTimer = 0f

    // Swipe-on-board gesture state.
    private var swipeId = -1
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeAnchorX = 0f
    private var swipeTime = 0f
    private var swipeMoved = false

    private val hud = ButtonGroup()
    private val pauseButton = hud.add(NeonButton("", Gfx.CYAN) { pauseGame() }.apply { icon = Gfx.Icon.PAUSE })
    private val pauseMenu = ButtonGroup()
    private val overMenu = ButtonGroup()

    // ---------------------------------------------------------------- effects
    private val particles = Particles()
    private val floaters = Floaters()
    private val banner = Banner()
    private val shake = Shake()
    private var dangerTimer = 0f
    private var magicFlash = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var wellShader: Shader? = null
    private var deckShader: Shader? = null
    private val tmp = RectF()

    init {
        pauseMenu.add(NeonButton("RESUME", Gfx.GREEN) { resumeGame() })
        pauseMenu.add(NeonButton("RESTART", Gfx.YELLOW) { restart() })
        pauseMenu.add(NeonButton("QUIT", Gfx.PINK) { toMenu() })
        overMenu.add(NeonButton("PLAY AGAIN", Gfx.YELLOW) { restart() })
        overMenu.add(NeonButton("MENU", Gfx.CYAN) { toMenu() })
        pauseMenu.onPress = { app.haptics.tick() }
        overMenu.onPress = { app.haptics.tick() }
    }

    private fun newGame() = GemDropGame(settings.difficulty, settings.startLevel, System.nanoTime(), this)

    override fun enter() {
        app.background.setTheme(SynthBackground.forLevel(game.level), instant = true)
        app.sound.playSong(Song.GAME)
    }

    override fun layout() {
        val vw = app.vw
        val top = app.top
        val hudH = 92f
        val controlsH = 372f
        controlsTop = app.bottom - controlsH
        val boardTop = top + hudH + 14f
        val availH = controlsTop - 26f - boardTop
        val margin = 20f
        val sideMin = 196f
        cell = min(availH / game.board.visibleRows, (vw - margin * 2 - 18f - sideMin) / game.board.cols)
            .coerceAtMost(ArcadeApp.MAX_CELL)
        val bw = cell * game.board.cols
        val bh = cell * game.board.visibleRows
        val bx = margin + 4f
        val by = boardTop + (availH - bh) / 2f
        boardRect.set(bx, by, bx + bw, by + bh)
        sideX = boardRect.right + 22f
        sideW = vw - margin - sideX

        // Side panel boxes
        val nc = min(cell * 0.92f, sideW * 0.5f)
        nextRect.set(sideX, by, sideX + sideW, by + 64f + nc * 3f)
        levelRect.set(sideX, nextRect.bottom + 18f, sideX + sideW, nextRect.bottom + 18f + 196f)
        statsRect.set(sideX, levelRect.bottom + 18f, sideX + sideW, min(levelRect.bottom + 18f + 220f, boardRect.bottom))

        wellShader = LinearGradient(0f, by, 0f, by + bh, 0xE6080016.toInt(), 0xE6180030.toInt(), Shader.TileMode.CLAMP)
        deckShader = LinearGradient(0f, controlsTop, 0f, app.vh, 0xF2140A2E.toInt(), 0xFF05010F.toInt(), Shader.TileMode.CLAMP)

        // Arcade buttons (mirrored for left-handed players)
        val cy = controlsTop + 168f
        fun x(v: Float) = if (settings.leftHanded) vw - v else v
        pad.getValue(Control.LEFT).place(x(104f), cy - 40f, 64f)
        pad.getValue(Control.RIGHT).place(x(264f), cy - 40f, 64f)
        pad.getValue(Control.DOWN).place(x(184f), cy + 104f, 52f)
        pad.getValue(Control.DROP).place(x(478f), cy + 50f, 70f)
        pad.getValue(Control.ROTATE).place(x(616f), cy - 30f, 78f)

        pauseButton.place(vw - margin - 72f, top + 10f, vw - margin, top + 82f)

        val pw = 380f
        val px = (vw - pw) / 2f
        var py = boardRect.centerY() - 40f
        for (b in pauseMenu.buttons) {
            b.place(px, py, px + pw, py + 80f)
            py += 100f
        }
        py = boardRect.centerY() + 120f
        for (b in overMenu.buttons) {
            b.place(px, py, px + pw, py + 80f)
            py += 100f
        }
    }

    // ---------------------------------------------------------------- flow

    private fun pauseGame() {
        if (state != State.PLAYING) return
        state = State.PAUSED
        releaseAll()
        app.sound.play(Sound.PAUSE)
    }

    private fun resumeGame() {
        state = State.PLAYING
        app.sound.play(Sound.SELECT)
    }

    private fun restart() {
        app.sound.play(Sound.START)
        app.go(GameScene(app))
    }

    private fun toMenu() {
        app.sound.play(Sound.BACK)
        app.go(TitleScene(app))
    }

    private fun releaseAll() {
        pointerControl.clear()
        keyHeld.clear()
        refreshHeld()
        swipeId = -1
        game.softDrop = false
    }

    override fun pause() {
        if (state == State.PLAYING && !game.isOver) pauseGame()
    }

    override fun back() {
        when (state) {
            State.PLAYING -> pauseGame()
            State.PAUSED -> resumeGame()
            State.OVER -> toMenu()
        }
    }

    // ---------------------------------------------------------------- update

    override fun update(dt: Float) {
        particles.update(dt)
        floaters.update(dt)
        banner.update(dt)
        shake.update(dt)
        if (magicFlash > 0f) magicFlash -= dt
        if (state == State.PAUSED) return

        game.update(dt)
        updateRepeat(dt)
        if (swipeId >= 0) swipeTime += dt

        if (game.phase == Phase.FALLING && isDanger()) {
            dangerTimer -= dt
            if (dangerTimer <= 0f) {
                app.sound.play(Sound.DANGER, gain = 0.7f)
                dangerTimer = 1.3f
            }
        } else {
            dangerTimer = 0f
        }

        if (game.isOver) {
            if (state != State.OVER) {
                state = State.OVER
                overTime = 0f
                releaseAll()
            }
            overTime += dt
            if (overTime > 2.6f && app.scores.qualifies(game.score)) {
                app.go(NameEntryScene(app, game.score, game.level, settings.difficulty.label.first(), game.maxChain))
            }
        }
    }

    private fun overMenuReady() = state == State.OVER && overTime >= 1.8f && !app.scores.qualifies(game.score)

    private fun isDanger() = game.board.stackHeight() >= game.board.rows - game.board.hiddenRows - 2

    private fun updateRepeat(dt: Float) {
        if (dasDir == 0) return
        val ctl = if (dasDir < 0) Control.LEFT else Control.RIGHT
        if (ctl !in held) {
            dasDir = 0
            return
        }
        dasTimer += dt
        if (dasTimer >= DAS_DELAY) {
            repeatTimer += dt
            while (repeatTimer >= DAS_REPEAT) {
                repeatTimer -= DAS_REPEAT
                if (dasDir < 0) game.moveLeft() else game.moveRight()
            }
        }
    }

    private fun refreshHeld() {
        val now = HashSet<Control>(pointerControl.values)
        now.addAll(keyHeld)
        for (c in Control.entries) {
            val was = c in held
            val isNow = c in now
            if (isNow && !was) press(c)
            if (!isNow && was) release(c)
        }
        held.clear()
        held.addAll(now)
        for ((c, b) in pad) b.pressed = c in held
    }

    private fun press(c: Control) {
        if (state != State.PLAYING) return
        app.haptics.tick()
        when (c) {
            Control.LEFT -> { game.moveLeft(); startRepeat(-1) }
            Control.RIGHT -> { game.moveRight(); startRepeat(1) }
            Control.DOWN -> game.softDrop = true
            Control.ROTATE -> game.rotate()
            Control.DROP -> game.hardDrop()
        }
    }

    private fun release(c: Control) {
        when (c) {
            Control.DOWN -> game.softDrop = false
            Control.LEFT -> if (dasDir < 0) { dasDir = 0; if (Control.RIGHT in held) startRepeat(1) }
            Control.RIGHT -> if (dasDir > 0) { dasDir = 0; if (Control.LEFT in held) startRepeat(-1) }
            else -> Unit
        }
    }

    private fun startRepeat(dir: Int) {
        dasDir = dir
        dasTimer = 0f
        repeatTimer = 0f
    }

    // ---------------------------------------------------------------- input

    private fun controlAt(x: Float, y: Float, maxReach: Float): Control? {
        var best: Control? = null
        var bestReach = maxReach
        for ((c, b) in pad) {
            val r = b.reach(x, y)
            if (r < bestReach) {
                bestReach = r
                best = c
            }
        }
        return best
    }

    override fun pointerDown(id: Int, x: Float, y: Float) {
        when (state) {
            State.PAUSED -> { pauseMenu.down(id, x, y); return }
            State.OVER -> { if (overMenuReady()) overMenu.down(id, x, y); return }
            State.PLAYING -> Unit
        }
        if (hud.down(id, x, y)) return
        val c = if (y > controlsTop - 30f) controlAt(x, y, 1.55f) else null
        if (c != null) {
            pointerControl[id] = c
            refreshHeld()
            return
        }
        if (settings.swipe && swipeId < 0 && y < controlsTop - 10f) {
            swipeId = id
            swipeStartX = x
            swipeStartY = y
            swipeAnchorX = x
            swipeTime = 0f
            swipeMoved = false
        }
    }

    override fun pointerMove(id: Int, x: Float, y: Float) {
        when (state) {
            State.PAUSED -> { pauseMenu.move(id, x, y); return }
            State.OVER -> { overMenu.move(id, x, y); return }
            State.PLAYING -> Unit
        }
        hud.move(id, x, y)
        val current = pointerControl[id]
        if (current != null) {
            // Fingers may slide between the movement buttons, but not onto the action buttons.
            val c = controlAt(x, y, 1.9f)
            val movement = setOf(Control.LEFT, Control.RIGHT, Control.DOWN)
            val next = when {
                c == null -> null
                c == current -> current
                current in movement && c in movement -> c
                else -> current
            }
            if (next != current) {
                if (next == null) pointerControl.remove(id) else pointerControl[id] = next
                refreshHeld()
            }
            return
        }
        if (id == swipeId) {
            val step = cell * 0.85f
            while (x - swipeAnchorX >= step) {
                swipeAnchorX += step
                game.moveRight()
                swipeMoved = true
            }
            while (swipeAnchorX - x >= step) {
                swipeAnchorX -= step
                game.moveLeft()
                swipeMoved = true
            }
        }
    }

    override fun pointerUp(id: Int, x: Float, y: Float) {
        when (state) {
            State.PAUSED -> { pauseMenu.up(id, x, y); return }
            State.OVER -> { overMenu.up(id, x, y); return }
            State.PLAYING -> Unit
        }
        if (hud.up(id, x, y)) return
        if (pointerControl.remove(id) != null) {
            refreshHeld()
            return
        }
        if (id == swipeId) {
            swipeId = -1
            val dx = x - swipeStartX
            val dy = y - swipeStartY
            if (dy > cell * 1.6f && dy > abs(dx) * 1.4f && swipeTime < 0.45f) {
                game.hardDrop()
            } else if (!swipeMoved && abs(dx) < cell * 0.45f && abs(dy) < cell * 0.6f && swipeTime < 0.35f) {
                game.rotate()
            }
        }
    }

    override fun pointerCancel() {
        hud.cancel()
        pauseMenu.cancel()
        overMenu.cancel()
        releaseAll()
    }

    override fun key(keyCode: Int, down: Boolean): Boolean {
        val c = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> Control.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> Control.RIGHT
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> Control.DOWN
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_SPACE -> Control.DROP
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_K -> Control.ROTATE
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_ESCAPE -> {
                if (down) back()
                return true
            }
            else -> return false
        }
        if (state != State.PLAYING) {
            if (down && (c == Control.ROTATE || c == Control.DROP)) {
                if (state == State.PAUSED) resumeGame()
                else if (overMenuReady()) restart()
            }
            return true
        }
        if (down) keyHeld.add(c) else keyHeld.remove(c)
        refreshHeld()
        return true
    }

    // ---------------------------------------------------------------- game events

    private fun cellX(col: Int) = boardRect.left + (col + 0.5f) * cell
    private fun cellY(row: Float) = boardRect.top + (row - game.board.hiddenRows + 0.5f) * cell

    override fun onMove() = app.sound.play(Sound.MOVE, gain = 0.7f)

    override fun onRotate() = app.sound.play(Sound.ROTATE)

    override fun onLock(piece: Piece) {
        app.sound.play(Sound.LAND)
        app.haptics.bump()
        val x = cellX(piece.col)
        val y = cellY(piece.row.toFloat()) + cell * 0.45f
        particles.burst(x, y, 0xFFB8A9D9.toInt(), 6, 160f, 5f, 500f, 0.4f)
    }

    override fun onHardDrop(col: Int, fromRow: Int, toRow: Int) {
        app.sound.play(Sound.DROP)
        val x = cellX(col)
        particles.trail(x, cellY(fromRow - 2f), cellY(toRow.toFloat()), Gfx.CYAN)
        shake.kick(5f)
    }

    override fun onMatch(cells: IntArray, chain: Int) {
        if (chain >= 2) {
            val col = Gfx.RAINBOW[(chain - 2) % Gfx.RAINBOW.size]
            banner.show("CHAIN x$chain", if (chain >= 4) "AWESOME!" else if (chain == 3) "RADICAL!" else "", col, 1.1f)
        }
    }

    override fun onClear(cells: IntArray, colors: IntArray, chain: Int, points: Long) {
        var sx = 0f
        var sy = 0f
        val b = game.board
        for (k in cells.indices) {
            val i = cells[k]
            val x = cellX(b.colOf(i))
            val y = cellY(b.rowOf(i).toFloat())
            sx += x
            sy += y
            val color = if (Gem.isColor(colors[k])) Gfx.GEM_COLORS[colors[k]] else Gfx.WHITE
            particles.burst(x, y, color, 12, 420f, 8f)
        }
        if (cells.isNotEmpty()) {
            floaters.add("+$points", sx / cells.size, sy / cells.size, Gfx.YELLOW, if (points >= 1000) 32f else 26f)
        }
        app.sound.play(Sound.CLEAR, rate = 1f + 0.12f * (chain - 1))
        app.haptics.thump(chain)
        shake.kick(2f + chain * 2.5f)
    }

    override fun onMagic(color: Int, count: Int, col: Int, row: Int) {
        app.sound.play(Sound.MAGIC)
        if (color >= 0) {
            banner.show("MAGIC!", "$count GEMS", Gfx.WHITE, 1.2f)
            magicFlash = 0.35f
            shake.kick(10f)
            app.haptics.buzz(60)
        } else {
            floaters.add("+${100 * game.level}", cellX(col), cellY(row.toFloat()), Gfx.WHITE)
        }
        val x = cellX(col)
        val y = cellY(row.toFloat())
        for (k in Gfx.RAINBOW.indices) particles.burst(x, y, Gfx.RAINBOW[k], 8, 520f, 8f)
    }

    override fun onAllClear(bonus: Long) {
        banner.show("ALL CLEAR!", "+$bonus", Gfx.GREEN, 1.6f)
        app.sound.play(Sound.ALL_CLEAR)
        for (k in 0 until 6) {
            particles.burst(boardRect.left + boardRect.width() * (k + 0.5f) / 6f, boardRect.centerY(), Gfx.RAINBOW[k], 14, 500f, 8f)
        }
    }

    override fun onLevelUp(level: Int) {
        banner.show("LEVEL $level", "SPEED UP!", Gfx.YELLOW, GemDropGame.LEVEL_UP_TIME)
        app.sound.play(Sound.LEVEL_UP)
        app.haptics.buzz(40)
        app.background.setTheme(SynthBackground.forLevel(level))
        val y = boardRect.top + boardRect.height() * 0.3f
        for (k in 0 until 6) particles.burst(boardRect.left + boardRect.width() * (k + 0.5f) / 6f, y, Gfx.RAINBOW[k], 10, 380f, 7f)
    }

    override fun onGameOver() {
        app.sound.play(Sound.GAME_OVER)
        app.sound.playSong(null)
        app.haptics.crash()
        shake.kick(14f)
    }

    // ---------------------------------------------------------------- drawing

    override fun draw(c: Canvas) {
        app.background.draw(c)
        val t = app.time
        c.save()
        c.translate(shake.dx, shake.dy)
        drawWell(c, t)
        drawSide(c, t)
        particles.draw(c)
        floaters.draw(c)
        banner.draw(c, boardRect.centerX(), boardRect.top + boardRect.height() * 0.38f, 52f, boardRect.width() + 120f)
        drawPhaseText(c, t)
        c.restore()
        drawHud(c, t)
        drawDeck(c)
        if (magicFlash > 0f) {
            paint.shader = null
            paint.color = Gfx.withAlpha(Gfx.WHITE, (magicFlash / 0.35f * 120).toInt())
            c.drawRect(app.screenLeft, 0f, app.screenRight, app.vh, paint)
        }
        when (state) {
            State.PAUSED -> drawPause(c, t)
            State.OVER -> drawGameOver(c, t)
            State.PLAYING -> Unit
        }
    }

    private fun drawWell(c: Canvas, t: Float) {
        val accent = SynthBackground.forLevel(game.level).accent
        val danger = isDanger() && game.phase != Phase.GAME_OVER
        val border = if (danger && ((t * 4f).toInt() % 2 == 0)) Gfx.PINK else accent
        tmp.set(boardRect)
        tmp.inset(-8f, -8f)
        paint.shader = wellShader
        c.drawRoundRect(tmp, 14f, 14f, paint)
        paint.shader = null
        Gfx.neonFrame(c, tmp, 14f, border, 4f)

        // Faint cell grid
        paint.color = Gfx.withAlpha(accent, 26)
        paint.strokeWidth = 1.5f
        for (col in 1 until game.board.cols) {
            val x = boardRect.left + col * cell
            c.drawLine(x, boardRect.top, x, boardRect.bottom, paint)
        }
        for (row in 1 until game.board.visibleRows) {
            val y = boardRect.top + row * cell
            c.drawLine(boardRect.left, y, boardRect.right, y, paint)
        }

        if (state == State.PAUSED) return

        c.save()
        c.clipRect(boardRect)
        val b = game.board
        val sprites = app.sprites
        val gemSize = cell * 1.14f
        val marked = game.marked
        val flashing = game.phase == Phase.FLASHING
        val flashOn = flashing && ((game.phaseTime * 14f).toInt() % 2 == 0)
        val grayFrom = if (game.isOver) b.rows - (game.phaseTime / 1.1f * b.visibleRows).toInt() else Int.MAX_VALUE
        for (r in 0 until b.rows) {
            for (col in 0 until b.cols) {
                val i = b.idx(col, r)
                val g = b.cells[i]
                if (g == Gem.EMPTY) continue
                val vr = r - game.fallOffset[i]
                if (vr < b.hiddenRows - 1.2f) continue
                val x = cellX(col)
                val y = cellY(vr)
                val isMarked = marked != null && marked[i]
                val style = when {
                    r >= grayFrom -> GemSprites.Style.GRAY
                    isMarked && flashOn -> GemSprites.Style.FLASH
                    else -> GemSprites.Style.NORMAL
                }
                var size = gemSize
                if (isMarked && flashing) size *= 1f + 0.18f * (game.phaseTime / GemDropGame.FLASH_TIME)
                sprites.draw(c, g, x, y, size, t, style)
            }
        }

        val p = game.piece
        if (p != null && game.phase == Phase.FALLING) {
            if (settings.ghost) {
                val gr = game.ghostRow()
                if (gr > p.row) {
                    for (k in 0 until 3) sprites.draw(c, p.gems[k], cellX(p.col), cellY((gr - 2 + k).toFloat()), gemSize, t, GemSprites.Style.GHOST)
                }
            }
            val fy = p.row + game.pieceFallFraction()
            for (k in 0 until 3) sprites.draw(c, p.gems[k], cellX(p.col), cellY(fy - 2 + k), gemSize, t)
        }
        c.restore()
    }

    private fun drawSide(c: Canvas, t: Float) {
        val accent = SynthBackground.forLevel(game.level).accent
        val cx = nextRect.centerX()

        // NEXT
        Gfx.panel(c, nextRect, 14f, 0xCC0A0018.toInt(), Gfx.CYAN)
        Gfx.glowText(c, "NEXT", cx, nextRect.top + 38f, 22f, Gfx.CYAN)
        val n = game.next
        val nc = min(cell * 0.92f, sideW * 0.5f)
        for (k in 0 until 3) {
            app.sprites.draw(c, n.gems[k], cx, nextRect.top + 56f + nc * (k + 0.5f), nc * 1.12f, t)
        }

        // LEVEL + progress
        Gfx.panel(c, levelRect, 14f, 0xCC0A0018.toInt(), accent)
        Gfx.glowText(c, "LEVEL", cx, levelRect.top + 36f, 20f, accent)
        val lv = game.level.toString()
        val lvSize = min(56f, (sideW - 30f) / lv.length)
        Gfx.outlineText(c, lv, cx, levelRect.top + 52f + lvSize, lvSize, Gfx.WHITE)
        val barTop = levelRect.top + 128f
        tmp.set(levelRect.left + 16f, barTop, levelRect.right - 16f, barTop + 22f)
        paint.shader = null
        paint.color = 0xFF1A0B33.toInt()
        c.drawRoundRect(tmp, 8f, 8f, paint)
        val fillW = tmp.width() * game.levelProgress
        if (fillW > 2f) {
            paint.color = accent
            c.drawRoundRect(tmp.left, tmp.top, tmp.left + fillW, tmp.bottom, 8f, 8f, paint)
            paint.color = Gfx.withAlpha(Gfx.WHITE, 90)
            c.drawRect(tmp.left + 4f, tmp.top + 4f, tmp.left + fillW - 4f, tmp.top + 8f, paint)
        }
        val left = (game.gemsForLevel - game.gemsThisLevel).coerceAtLeast(0)
        Gfx.glowText(c, "$left TO GO", cx, barTop + 52f, 15f, Gfx.WHITE, accent, glowRadius = 4f)

        // Stats
        if (statsRect.height() > 120f) {
            Gfx.panel(c, statsRect, 14f, 0xCC0A0018.toInt(), Gfx.PINK)
            var y = statsRect.top + 36f
            Gfx.glowText(c, "CHAIN", cx, y, 18f, Gfx.PINK)
            y += 40f
            Gfx.glowText(c, "x${game.maxChain}", cx, y, 28f, Gfx.WHITE, Gfx.PINK)
            y += 46f
            if (y + 50f < statsRect.bottom) {
                Gfx.glowText(c, "GEMS", cx, y, 18f, Gfx.PINK)
                y += 38f
                Gfx.glowText(c, game.totalGems.toString(), cx, y, 24f, Gfx.WHITE, Gfx.PINK)
                y += 40f
            }
            if (y + 10f < statsRect.bottom) {
                Gfx.glowText(c, settings.difficulty.label, cx, y, 15f, Gfx.YELLOW, glowRadius = 4f)
            }
        }
    }

    private fun drawPhaseText(c: Canvas, t: Float) {
        val cx = boardRect.centerX()
        val cy = boardRect.top + boardRect.height() * 0.45f
        when (game.phase) {
            Phase.READY -> {
                if (game.phaseTime < 0.9f) {
                    Gfx.outlineText(c, "READY?", cx, cy, 44f, Gfx.YELLOW)
                    Gfx.glowText(c, "LEVEL ${game.level}", cx, cy + 56f, 22f, Gfx.WHITE, Gfx.CYAN)
                } else {
                    Gfx.outlineText(c, "GO!", cx, cy, 64f, Gfx.GREEN)
                }
            }
            Phase.GAME_OVER -> if (game.phaseTime > 0.8f) {
                val blink = ((t * 2.5f).toInt() % 2 == 0) || state != State.OVER
                if (blink) {
                    Gfx.outlineText(c, "GAME", cx, cy - 30f, 60f, Gfx.PINK)
                    Gfx.outlineText(c, "OVER", cx, cy + 46f, 60f, Gfx.PINK)
                }
            }
            else -> Unit
        }
    }

    private fun drawHud(c: Canvas, t: Float) {
        val top = app.top
        Gfx.glowText(c, "SCORE", 24f, top + 28f, 18f, Gfx.CYAN, align = Paint.Align.LEFT)
        Gfx.glowText(c, game.score.toString().padStart(7, '0'), 24f, top + 76f, 34f, Gfx.WHITE, Gfx.CYAN, align = Paint.Align.LEFT)
        val hi = maxOf(app.scores.topScore, game.score)
        val hiColor = if (game.score > 0 && game.score >= app.scores.topScore) Gfx.YELLOW else Gfx.PINK
        Gfx.glowText(c, "HI-SCORE", 392f, top + 28f, 18f, hiColor, align = Paint.Align.LEFT)
        Gfx.glowText(c, hi.toString().padStart(7, '0'), 392f, top + 70f, 26f, Gfx.WHITE, hiColor, align = Paint.Align.LEFT)
        hud.draw(c, t)
    }

    private fun drawDeck(c: Canvas) {
        val accent = SynthBackground.forLevel(game.level).accent
        val deckTop = controlsTop - 6f
        paint.shader = deckShader
        c.drawRect(app.screenLeft, deckTop, app.screenRight, app.vh, paint)
        paint.shader = null
        Gfx.line(c, app.screenLeft, deckTop, app.screenRight, deckTop, Gfx.withAlpha(accent, 90), 8f)
        Gfx.line(c, app.screenLeft, deckTop, app.screenRight, deckTop, accent, 3f)
        // Diagonal racing stripes for that cabinet control-panel look
        val mid = app.vw / 2f
        for (k in 0 until 3) {
            val col = Gfx.withAlpha(Gfx.RAINBOW[k * 2], 70)
            val x = mid - 30f + k * 20f
            Gfx.line(c, x, deckTop + 20f, x - 40f, app.vh, col, 8f)
        }
        Gfx.glowText(c, "1P", mid - 10f, deckTop + 40f, 18f, Gfx.withAlpha(Gfx.WHITE, 160), accent, glowRadius = 3f)
        for (b in pad.values) b.draw(c)
    }

    private fun dim(c: Canvas, alpha: Int) {
        paint.shader = null
        paint.color = Gfx.withAlpha(Gfx.NIGHT, alpha)
        c.drawRect(app.screenLeft, 0f, app.screenRight, app.vh, paint)
    }

    private fun drawPause(c: Canvas, t: Float) {
        dim(c, 170)
        val cx = app.vw / 2f
        Gfx.outlineText(c, "PAUSED", cx, boardRect.centerY() - 120f, 56f, Gfx.CYAN)
        pauseMenu.draw(c, t)
    }

    private fun drawGameOver(c: Canvas, t: Float) {
        if (overTime < 1.4f) return
        val qualifies = app.scores.qualifies(game.score)
        val a = ((overTime - 1.4f) / 0.4f).coerceIn(0f, 1f)
        dim(c, (140 * a).toInt())
        val cx = app.vw / 2f
        if (qualifies) {
            Gfx.outlineText(c, "NEW HIGH SCORE!", cx, boardRect.centerY() + 150f, 34f, Gfx.YELLOW, alpha = (255 * a).toInt())
            return
        }
        tmp.set(cx - 260f, boardRect.centerY() - 150f, cx + 260f, boardRect.centerY() + 100f)
        Gfx.panel(c, tmp, 18f, 0xE60A0018.toInt(), Gfx.PINK, (255 * a).toInt())
        Gfx.outlineText(c, "GAME OVER", cx, tmp.top + 70f, 44f, Gfx.PINK, alpha = (255 * a).toInt())
        val lines = listOf(
            "SCORE  ${game.score}",
            "LEVEL  ${game.level}",
            "CHAIN  x${game.maxChain}",
        )
        var y = tmp.top + 130f
        for (s in lines) {
            Gfx.glowText(c, s, cx, y, 22f, Gfx.WHITE, Gfx.CYAN, alpha = (255 * a).toInt())
            y += 40f
        }
        if (overMenuReady()) overMenu.draw(c, t)
    }

    companion object {
        const val DAS_DELAY = 0.17f
        const val DAS_REPEAT = 0.055f
    }
}
