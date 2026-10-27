package com.metroshare.droidarcade.game

/**
 * The playfield. Pure logic, no Android dependencies, so it can be unit tested on the JVM.
 *
 * Row 0 is the top. The first [hiddenRows] rows sit above the visible well; a gem left
 * up there once everything has settled means the stack has overflowed (game over).
 */
class Board(
    val cols: Int = 6,
    val visibleRows: Int = 13,
    val hiddenRows: Int = 3,
) {
    val rows = visibleRows + hiddenRows
    val cells = IntArray(cols * rows) { Gem.EMPTY }

    fun idx(c: Int, r: Int) = r * cols + c
    fun colOf(i: Int) = i % cols
    fun rowOf(i: Int) = i / cols

    fun inBounds(c: Int, r: Int) = c in 0 until cols && r in 0 until rows

    operator fun get(c: Int, r: Int): Int = if (inBounds(c, r)) cells[idx(c, r)] else Gem.EMPTY

    operator fun set(c: Int, r: Int, v: Int) {
        if (inBounds(c, r)) cells[idx(c, r)] = v
    }

    /** A cell a falling gem may occupy. Rows above the board (negative) are always free. */
    fun isFree(c: Int, r: Int): Boolean {
        if (c !in 0 until cols || r >= rows) return false
        if (r < 0) return true
        return cells[idx(c, r)] == Gem.EMPTY
    }

    fun clear() = cells.fill(Gem.EMPTY)

    fun isEmpty() = cells.all { it == Gem.EMPTY }

    /** True when any gem rests in the hidden rows above the visible well. */
    fun overflowed(): Boolean {
        for (i in 0 until hiddenRows * cols) if (cells[i] != Gem.EMPTY) return true
        return false
    }

    /** Height of the tallest column in visible rows (0 = empty, visibleRows = full). */
    fun stackHeight(): Int {
        for (r in 0 until rows) {
            for (c in 0 until cols) if (cells[idx(c, r)] != Gem.EMPTY) return rows - r
        }
        return 0
    }

    /**
     * Marks every gem that is part of a straight line of [minRun] or more gems of the same
     * colour: horizontal, vertical or either diagonal.
     */
    fun findMatches(minRun: Int = 3): BooleanArray {
        val marked = BooleanArray(cells.size)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val g = cells[idx(c, r)]
                if (!Gem.isColor(g)) continue
                for (d in DIRS) {
                    val dc = d[0]
                    val dr = d[1]
                    // Only measure from the first gem of a run.
                    if (get(c - dc, r - dr) == g) continue
                    var len = 1
                    while (get(c + dc * len, r + dr * len) == g) len++
                    if (len >= minRun) {
                        for (k in 0 until len) marked[idx(c + dc * k, r + dr * k)] = true
                    }
                }
            }
        }
        return marked
    }

    /**
     * Drops every gem straight down into empty space below it.
     * [fallOut] receives, for each destination cell, how many rows its gem fell (for animation).
     * Returns true if anything moved.
     */
    fun applyGravity(fallOut: IntArray? = null): Boolean {
        fallOut?.fill(0)
        var moved = false
        for (c in 0 until cols) {
            var write = rows - 1
            for (r in rows - 1 downTo 0) {
                val g = cells[idx(c, r)]
                if (g == Gem.EMPTY) continue
                if (write != r) {
                    cells[idx(c, write)] = g
                    cells[idx(c, r)] = Gem.EMPTY
                    fallOut?.set(idx(c, write), write - r)
                    moved = true
                }
                write--
            }
        }
        return moved
    }

    /** Lowest row the bottom gem of a vertical piece in column [c] can reach starting from [fromRow]. */
    fun dropRow(c: Int, fromRow: Int): Int {
        var r = fromRow
        while (isFree(c, r + 1)) r++
        return r
    }

    companion object {
        private val DIRS = arrayOf(
            intArrayOf(1, 0),  // horizontal
            intArrayOf(0, 1),  // vertical
            intArrayOf(1, 1),  // diagonal down-right
            intArrayOf(1, -1), // diagonal up-right
        )
    }
}

object Gem {
    const val EMPTY = -1
    const val MAGIC = 99
    const val MAX_COLORS = 6

    fun isColor(g: Int) = g in 0 until MAX_COLORS
}

/** A falling column of three gems. gems[0] is the top, gems[2] the bottom. [row] is the bottom gem's row. */
class Piece(var col: Int, var row: Int, val gems: IntArray) {
    val isMagic get() = gems[0] == Gem.MAGIC

    /** Cycle colours downward: the bottom gem moves to the top. */
    fun rotate() {
        val bottom = gems[2]
        gems[2] = gems[1]
        gems[1] = gems[0]
        gems[0] = bottom
    }

    fun copy() = Piece(col, row, gems.copyOf())
}
