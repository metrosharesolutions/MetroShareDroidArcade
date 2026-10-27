package com.metroshare.droidarcade.game

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

enum class Difficulty(val label: String, val baseInterval: Float, val minInterval: Float) {
    EASY("EASY", 1.00f, 0.10f),
    NORMAL("NORMAL", 0.80f, 0.07f),
    HARD("HARD", 0.55f, 0.05f);
}

/** Hooks the presentation layer uses for sound, particles and haptics. All optional. */
interface GameEvents {
    fun onSpawn(piece: Piece) {}
    fun onMove() {}
    fun onRotate() {}
    fun onLock(piece: Piece) {}
    fun onHardDrop(col: Int, fromRow: Int, toRow: Int) {}
    fun onMatch(cells: IntArray, chain: Int) {}
    fun onClear(cells: IntArray, colors: IntArray, chain: Int, points: Long) {}
    fun onMagic(color: Int, count: Int, col: Int, row: Int) {}
    fun onAllClear(bonus: Long) {}
    fun onLevelUp(level: Int) {}
    fun onGameOver() {}
}

/**
 * Falling-jewel match-3 rules: a column of three gems drops into a 6x13 well. Line up three or
 * more of a colour in any direction to clear them; gems above fall and can set off chains.
 * Clearing gems advances the level, and the levels never end - they just keep getting faster.
 */
class GemDropGame(
    val difficulty: Difficulty = Difficulty.NORMAL,
    startLevel: Int = 1,
    seed: Long = System.nanoTime(),
    var events: GameEvents = object : GameEvents {},
) {
    enum class Phase { READY, FALLING, FLASHING, SETTLING, LEVEL_UP, GAME_OVER }

    val board = Board()
    private val rng = Random(seed)

    var phase = Phase.READY
        private set
    var phaseTime = 0f
        private set

    var piece: Piece? = null
        private set
    var next: Piece
        private set

    var score = 0L
        private set
    var level = startLevel.coerceAtLeast(1)
        private set
    var gemsThisLevel = 0
        private set
    var totalGems = 0
        private set
    var chain = 0
        private set
    var maxChain = 0
        private set
    var piecesPlaced = 0
        private set
    var playTime = 0f
        private set

    /** Held "down" button: the piece falls fast and earns a point per row. */
    var softDrop = false

    /** Cells currently flashing before they burst, or null. */
    var marked: BooleanArray? = null
        private set

    /** Visual drop animation: how many rows above its resting cell each gem is drawn. */
    val fallOffset = FloatArray(board.cells.size)
    private val fallVel = FloatArray(board.cells.size)
    private val fallScratch = IntArray(board.cells.size)

    private var gravTimer = 0f
    private var lockTimer = 0f
    private var lockResets = 0
    private var magicClear = false
    private var piecesSinceMagic = 0

    init {
        next = randomPiece()
    }

    // ------------------------------------------------------------------ tuning

    val gravityInterval: Float
        get() = (difficulty.baseInterval * 0.87f.pow(level - 1)).coerceAtLeast(difficulty.minInterval)

    val lockDelay: Float
        get() = (0.5f - (level - 1) * 0.01f).coerceAtLeast(0.28f)

    val colorsInPlay: Int
        get() = when (difficulty) {
            Difficulty.EASY -> if (level < 10) 4 else 5
            Difficulty.NORMAL -> if (level < 4) 4 else if (level < 10) 5 else 6
            Difficulty.HARD -> if (level < 5) 5 else 6
        }

    val gemsForLevel: Int
        get() = min(20 + (level - 1) * 4, 60)

    val levelProgress: Float
        get() = (gemsThisLevel.toFloat() / gemsForLevel).coerceIn(0f, 1f)

    val isOver get() = phase == Phase.GAME_OVER

    // ------------------------------------------------------------------ input

    fun moveLeft() = shift(-1)
    fun moveRight() = shift(1)

    private fun shift(dir: Int): Boolean {
        val p = piece ?: return false
        if (phase != Phase.FALLING) return false
        val nc = p.col + dir
        if (!fits(nc, p.row)) return false
        p.col = nc
        bumpLock()
        events.onMove()
        return true
    }

    fun rotate(): Boolean {
        val p = piece ?: return false
        if (phase != Phase.FALLING) return false
        p.rotate()
        bumpLock()
        events.onRotate()
        return true
    }

    fun hardDrop(): Boolean {
        val p = piece ?: return false
        if (phase != Phase.FALLING) return false
        val target = board.dropRow(p.col, p.row)
        val rows = target - p.row
        events.onHardDrop(p.col, p.row, target)
        p.row = target
        score += rows * 2L
        lock()
        return true
    }

    /** Row the current piece would land on (bottom gem), for the ghost preview. */
    fun ghostRow(): Int {
        val p = piece ?: return -1
        return board.dropRow(p.col, p.row)
    }

    /** 0..1 progress toward the next row, used to draw the piece sliding smoothly. */
    fun pieceFallFraction(): Float {
        val p = piece ?: return 0f
        if (phase != Phase.FALLING || !fits(p.col, p.row + 1)) return 0f
        return (gravTimer / currentInterval()).coerceIn(0f, 0.999f)
    }

    // ------------------------------------------------------------------ simulation

    fun update(dt: Float) {
        phaseTime += dt
        if (phase != Phase.GAME_OVER) playTime += dt
        when (phase) {
            Phase.READY -> if (phaseTime >= READY_TIME) spawn()
            Phase.FALLING -> updateFalling(dt)
            Phase.FLASHING -> if (phaseTime >= FLASH_TIME) burst()
            Phase.SETTLING -> if (updateSettling(dt)) resolve()
            Phase.LEVEL_UP -> if (phaseTime >= LEVEL_UP_TIME) spawn()
            Phase.GAME_OVER -> Unit
        }
    }

    private fun setPhase(p: Phase) {
        phase = p
        phaseTime = 0f
    }

    private fun currentInterval(): Float {
        val g = gravityInterval
        return if (softDrop) min(g, SOFT_DROP_INTERVAL) else g
    }

    private fun updateFalling(dt: Float) {
        val p = piece ?: return
        if (fits(p.col, p.row + 1)) {
            lockTimer = 0f
            gravTimer += dt
            val interval = currentInterval()
            while (gravTimer >= interval) {
                gravTimer -= interval
                p.row++
                if (softDrop) score += 1
                if (!fits(p.col, p.row + 1)) {
                    gravTimer = 0f
                    break
                }
            }
        } else {
            gravTimer = 0f
            lockTimer += dt
            val delay = if (softDrop) lockDelay * 0.35f else lockDelay
            if (lockTimer >= delay) lock()
        }
    }

    private fun fits(col: Int, row: Int): Boolean =
        board.isFree(col, row) && board.isFree(col, row - 1) && board.isFree(col, row - 2)

    private fun bumpLock() {
        val p = piece ?: return
        if (!fits(p.col, p.row + 1)) {
            if (lockResets < MAX_LOCK_RESETS) {
                lockTimer = 0f
                lockResets++
            }
        } else {
            lockTimer = 0f
        }
    }

    private fun randomPiece(): Piece {
        piecesSinceMagic++
        if (level >= 3 && piecesSinceMagic > 12 && rng.nextFloat() < MAGIC_CHANCE) {
            piecesSinceMagic = 0
            return Piece(SPAWN_COL, board.hiddenRows - 1, intArrayOf(Gem.MAGIC, Gem.MAGIC, Gem.MAGIC))
        }
        val n = colorsInPlay
        var gems = IntArray(3) { rng.nextInt(n) }
        // A piece of three identical gems is a freebie; make it rarer.
        if (gems[0] == gems[1] && gems[1] == gems[2]) gems = IntArray(3) { rng.nextInt(n) }
        return Piece(SPAWN_COL, board.hiddenRows - 1, gems)
    }

    private fun spawn() {
        val p = next
        p.col = SPAWN_COL
        p.row = board.hiddenRows - 1
        next = randomPiece()
        gravTimer = 0f
        lockTimer = 0f
        lockResets = 0
        if (!fits(p.col, p.row)) {
            piece = null
            gameOver()
            return
        }
        piece = p
        setPhase(Phase.FALLING)
        events.onSpawn(p)
    }

    private fun lock() {
        val p = piece ?: return
        piece = null
        piecesPlaced++
        chain = 0
        events.onLock(p)
        if (p.isMagic) {
            val below = board[p.col, p.row + 1]
            if (Gem.isColor(below)) {
                val m = BooleanArray(board.cells.size)
                var count = 0
                for (i in board.cells.indices) {
                    if (board.cells[i] == below) {
                        m[i] = true
                        count++
                    }
                }
                events.onMagic(below, count, p.col, p.row)
                magicClear = true
                chain = 1
                marked = m
                setPhase(Phase.FLASHING)
                events.onMatch(indicesOf(m), chain)
            } else {
                // Landed on the floor: a small consolation bonus.
                score += 100L * level
                events.onMagic(-1, 0, p.col, p.row)
                resolve()
            }
            return
        }
        for (k in 0 until 3) board[p.col, p.row - 2 + k] = p.gems[k]
        resolve()
    }

    /** Look for matches; either start a flash (chain step) or finish the turn. */
    private fun resolve() {
        val m = board.findMatches()
        if (m.any { it }) {
            chain++
            maxChain = maxOf(maxChain, chain)
            marked = m
            magicClear = false
            setPhase(Phase.FLASHING)
            events.onMatch(indicesOf(m), chain)
        } else {
            marked = null
            endTurn()
        }
    }

    private fun burst() {
        val m = marked ?: return resolve()
        val cells = indicesOf(m)
        val colors = IntArray(cells.size) { board.cells[cells[it]] }
        for (i in cells) board.cells[i] = Gem.EMPTY
        val count = cells.size
        val points = if (magicClear) {
            count * 30L * level
        } else {
            (count * 10L + (count - 3).coerceAtLeast(0) * 10L) * chain * level
        }
        score += points
        totalGems += count
        gemsThisLevel += count
        events.onClear(cells, colors, chain, points)
        marked = null
        magicClear = false

        board.applyGravity(fallScratch)
        for (i in fallScratch.indices) {
            fallOffset[i] = fallScratch[i].toFloat()
            fallVel[i] = 0f
        }
        setPhase(Phase.SETTLING)
    }

    /** Animate falling gems; returns true once everything has landed. */
    private fun updateSettling(dt: Float): Boolean {
        var moving = false
        for (i in fallOffset.indices) {
            if (fallOffset[i] > 0f) {
                fallVel[i] += SETTLE_GRAVITY * dt
                fallOffset[i] = (fallOffset[i] - (fallVel[i] + 2f) * dt).coerceAtLeast(0f)
                if (fallOffset[i] > 0f) moving = true
            }
        }
        return !moving && phaseTime >= MIN_SETTLE_TIME
    }

    private fun endTurn() {
        if (board.overflowed()) {
            gameOver()
            return
        }
        if (board.isEmpty() && piecesPlaced > 0) {
            val bonus = 1000L * level
            score += bonus
            events.onAllClear(bonus)
        }
        if (gemsThisLevel >= gemsForLevel) {
            gemsThisLevel -= gemsForLevel
            level++
            gemsThisLevel = gemsThisLevel.coerceAtMost(gemsForLevel - 1)
            setPhase(Phase.LEVEL_UP)
            events.onLevelUp(level)
            return
        }
        spawn()
    }

    private fun gameOver() {
        piece = null
        softDrop = false
        setPhase(Phase.GAME_OVER)
        events.onGameOver()
    }

    private fun indicesOf(m: BooleanArray): IntArray {
        var n = 0
        for (b in m) if (b) n++
        val out = IntArray(n)
        var k = 0
        for (i in m.indices) if (m[i]) out[k++] = i
        return out
    }

    companion object {
        const val SPAWN_COL = 2
        const val READY_TIME = 1.4f
        const val FLASH_TIME = 0.42f
        const val LEVEL_UP_TIME = 1.6f
        const val MIN_SETTLE_TIME = 0.05f
        const val SETTLE_GRAVITY = 70f
        const val SOFT_DROP_INTERVAL = 0.035f
        const val MAX_LOCK_RESETS = 12
        const val MAGIC_CHANCE = 0.035f
    }
}
