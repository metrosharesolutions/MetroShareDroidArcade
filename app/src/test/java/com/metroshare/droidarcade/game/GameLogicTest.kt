package com.metroshare.droidarcade.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLogicTest {

    private fun bottom(b: Board) = b.rows - 1

    @Test
    fun horizontalVerticalAndDiagonalRunsMatch() {
        val b = Board()
        val r = bottom(b)
        b[0, r] = 1; b[1, r] = 1; b[2, r] = 1
        var m = b.findMatches()
        assertEquals(3, m.count { it })

        b.clear()
        b[4, r] = 2; b[4, r - 1] = 2; b[4, r - 2] = 2; b[4, r - 3] = 2
        m = b.findMatches()
        assertEquals(4, m.count { it })

        b.clear()
        b[0, r] = 3; b[1, r - 1] = 3; b[2, r - 2] = 3
        assertEquals(3, b.findMatches().count { it })

        b.clear()
        b[5, r] = 4; b[4, r - 1] = 4; b[3, r - 2] = 4
        assertEquals(3, b.findMatches().count { it })
    }

    @Test
    fun twoInARowDoesNotMatch() {
        val b = Board()
        val r = bottom(b)
        b[0, r] = 1; b[1, r] = 1; b[2, r] = 2
        assertFalse(b.findMatches().any { it })
    }

    @Test
    fun gravityDropsGemsAndReportsDistance() {
        val b = Board()
        val fall = IntArray(b.cells.size)
        b[2, 5] = 3
        assertTrue(b.applyGravity(fall))
        assertEquals(3, b[2, bottom(b)])
        assertEquals(Gem.EMPTY, b[2, 5])
        assertEquals(bottom(b) - 5, fall[b.idx(2, bottom(b))])
        assertFalse(b.applyGravity(fall))
    }

    @Test
    fun pieceRotationCyclesDown() {
        val p = Piece(0, 0, intArrayOf(1, 2, 3))
        p.rotate()
        assertEquals(listOf(3, 1, 2), p.gems.toList())
    }

    @Test
    fun hardDropLocksAndSpawnsNext() {
        val g = GemDropGame(Difficulty.NORMAL, 1, seed = 42)
        g.update(GemDropGame.READY_TIME + 0.01f)
        assertEquals(GemDropGame.Phase.FALLING, g.phase)
        assertNotNull(g.piece)
        val p = g.piece!!
        val gems = p.gems.copyOf()
        val col = p.col
        g.hardDrop()
        // Unless the random piece happened to self-match, it now rests at the bottom.
        val b = g.board
        if (g.phase == GemDropGame.Phase.FALLING) {
            assertEquals(gems[2], b[col, b.rows - 1])
            assertEquals(gems[0], b[col, b.rows - 3])
        }
        assertTrue(g.score > 0)
    }

    @Test
    fun matchClearsScoresAndChains() {
        var cleared = 0
        var bestChain = 0
        val g = GemDropGame(Difficulty.NORMAL, 1, seed = 7, events = object : GameEvents {
            override fun onClear(cells: IntArray, colors: IntArray, chain: Int, points: Long) {
                cleared += cells.size
                bestChain = maxOf(bestChain, chain)
            }
        })
        val b = g.board
        val r = b.rows - 1
        // Bottom row: blue blue red, with a second red stacked in column 2.
        b[0, r] = 4; b[1, r] = 4
        b[2, r] = 0; b[2, r - 1] = 0
        g.update(GemDropGame.READY_TIME + 0.01f)
        val p = g.piece!!
        while (p.col > 2) g.moveLeft()
        while (p.col < 2) g.moveRight()
        // Piece (top to bottom): purple, blue, red.
        p.gems[0] = 5; p.gems[1] = 4; p.gems[2] = 0
        g.hardDrop()
        // Three reds clear vertically, the blue falls into the bottom row and completes blue-blue-blue.
        repeat(400) { g.update(1f / 60f) }
        assertTrue("cleared $cleared", cleared >= 6)
        assertEquals(2, bestChain)
        assertTrue(g.maxChain >= 2)
    }

    @Test
    fun levelsAdvanceForever() {
        val g = GemDropGame(Difficulty.HARD, 50, seed = 1)
        assertEquals(50, g.level)
        assertEquals(6, g.colorsInPlay)
        assertEquals(Difficulty.HARD.minInterval, g.gravityInterval, 1e-6f)
        assertTrue(g.gemsForLevel > 0)
    }

    @Test
    fun overflowEndsTheGame() {
        var over = false
        val g = GemDropGame(Difficulty.NORMAL, 1, seed = 3, events = object : GameEvents {
            override fun onGameOver() {
                over = true
            }
        })
        val b = g.board
        // Fill the spawn column with an alternating pattern that can never match.
        for (r in 0 until b.rows) b[GemDropGame.SPAWN_COL, r] = if ((r / 2) % 2 == 0) 0 else 1
        g.update(GemDropGame.READY_TIME + 0.01f)
        assertTrue(over)
        assertTrue(g.isOver)
    }
}
