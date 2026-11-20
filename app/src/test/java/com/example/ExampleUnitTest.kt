package com.example

import com.example.game.CandyCell
import com.example.game.CandyGameEngine
import com.example.game.CandySpecial
import com.example.game.CandyType
import com.example.game.GridPos
import com.example.util.formatDuration
import com.example.util.formatFileSize
import com.example.util.formatResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun formatDuration_formatsMinutesAndHours() {
        assertEquals("0:00", formatDuration(0L))
        assertEquals("00:45", formatDuration(45_000L))
        assertEquals("02:05", formatDuration(125_000L))
        assertEquals("1:01:05", formatDuration(3_665_000L))
    }

    @Test
    fun formatFileSize_formatsReadableUnits() {
        assertEquals("512 B", formatFileSize(512L))
        assertEquals("10 KB", formatFileSize(10_240L))
        assertEquals("2.5 MB", formatFileSize(2_621_440L))
    }

    @Test
    fun formatResolution_formatsStandardLabels() {
        assertEquals("1080p (1920×1080)", formatResolution(1920, 1080))
        assertEquals("4K (3840×2160)", formatResolution(3840, 2160))
        assertEquals("Unknown", formatResolution(0, 0))
    }

    @Test
    fun candyGameEngine_createsPlayableBoardWithNoInitialMatches() {
        val board = CandyGameEngine.createInitialBoard()
        assertEquals(CandyGameEngine.BOARD_SIZE, board.size)
        assertNull(CandyGameEngine.resolveMatches(board))
        assertTrue(CandyGameEngine.hasValidMove(board))
    }

    @Test
    fun candyGameEngine_detectsMatchFourAndSpawnsStripedCandy() {
        val types = CandyType.entries
        val board = MutableList(CandyGameEngine.BOARD_SIZE) { r ->
            MutableList(CandyGameEngine.BOARD_SIZE) { c ->
                // Deterministic non-matching pattern
                CandyCell((r * 10 + c).toLong(), types[(r * 2 + c) % types.size])
            }
        }
        // Inject 4 horizontal Strawberry candies at row 0, cols 0..3
        for (c in 0..3) {
            board[0][c] = CandyCell(1000L + c, CandyType.STRAWBERRY)
        }
        val step = CandyGameEngine.resolveMatches(
            board = board.map { it.toList() },
            preferredPivotA = GridPos(0, 2)
        )
        assertNotNull(step)
        val spawned = step!!.spawnedSpecials[GridPos(0, 2)]
        assertNotNull(spawned)
        assertEquals(CandySpecial.STRIPED_V, spawned!!.special)
        assertTrue(step.pointsEarned > 0)
    }
}
