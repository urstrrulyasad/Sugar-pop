package com.example

import com.example.game.CandyGameEngine
import com.example.game.EnemyEntity
import com.example.game.EnemyKind
import com.example.game.EnemyState
import com.example.game.GameStatus
import com.example.game.PlatformerEngine
import com.example.game.PlatformerInput
import com.example.game.PlatformerLevels
import com.example.game.TileKind
import com.example.game.tileKey
import com.example.util.formatDuration
import com.example.util.formatFileSize
import com.example.util.formatResolution
import org.junit.Assert.assertEquals
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
    fun platformerLevels_buildsAllTenCampaignStagesAndMasterMode() {
        for (lvl in 1..PlatformerLevels.TOTAL_CAMPAIGN_LEVELS) {
            val state = PlatformerLevels.createWorldState(lvl)
            assertTrue("Level $lvl should have ground tiles", state.tiles.isNotEmpty())
            assertTrue("Level $lvl should have enemies", state.enemies.isNotEmpty())
            assertTrue("Level $lvl should have coins", state.coins.isNotEmpty())
            assertTrue("Level $lvl goal must be ahead of spawn", state.goal.x > state.spawnX + 20f)
        }
        val master = PlatformerLevels.createWorldState(11)
        assertTrue("Master mode should scale speed multiplier", master.speedMultiplier > 1f)
    }

    @Test
    fun platformerEngine_jumpStompsEnemyAndReachesGoal() {
        var state = PlatformerLevels.createWorldState(1)
        // Settle player onto floor
        repeat(10) {
            state = PlatformerEngine.step(state, PlatformerInput(), 0.016f)
        }
        assertTrue(state.player.onGround)
        val startY = state.player.y

        // Jump upward
        state = PlatformerEngine.step(
            state,
            PlatformerInput(jumpHeld = true, jumpJustPressed = true),
            0.016f
        )
        repeat(6) {
            state = PlatformerEngine.step(state, PlatformerInput(jumpHeld = true), 0.016f)
        }
        assertTrue("Player should rise above starting floor", state.player.y > startY + 0.5f)

        // Stomp a Jelly Walker positioned right beneath falling player
        val targetEnemy = EnemyEntity(
            id = 9999,
            kind = EnemyKind.JELLY_WALKER,
            x = state.player.x,
            y = state.player.y - 0.72f,
            vx = 0f,
            vy = 0f
        )
        val stompState = state.copy(
            player = state.player.copy(vy = -6f),
            enemies = listOf(targetEnemy)
        )
        val afterStomp = PlatformerEngine.step(stompState, PlatformerInput(), 0.016f)
        assertTrue("Stomping should award points", afterStomp.score > stompState.score)
        assertEquals(EnemyState.SQUASHED, afterStomp.enemies.first().state)
        assertTrue("Stomping should bounce player upward", afterStomp.player.vy > 0f)

        // Reach goal flagpole
        val nearGoal = afterStomp.copy(
            player = afterStomp.player.copy(
                x = afterStomp.goal.x,
                y = afterStomp.goal.baseY + 2.0f
            )
        )
        val cleared = PlatformerEngine.step(nearGoal, PlatformerInput(), 0.016f)
        assertEquals(GameStatus.LEVEL_CLEAR, cleared.status)
        assertTrue(cleared.flagGrabBonus >= 500)
    }

    @Test
    fun platformerEngine_headBonkQuestionBlockAwardsCoin() {
        val base = PlatformerLevels.createWorldState(1)
        val qCol = 4
        val qRow = 5
        val customTiles = base.tiles.toMutableMap()
        customTiles[tileKey(qCol, qRow)] = base.tiles.values.first().copy(
            col = qCol,
            row = qRow,
            kind = TileKind.QUESTION_COIN
        )
        val ready = base.copy(
            tiles = customTiles,
            player = base.player.copy(x = qCol + 0.5f, y = qRow - 1.0f, vy = 8.0f, onGround = false)
        )
        val after = PlatformerEngine.step(ready, PlatformerInput(jumpHeld = true), 0.02f)
        assertEquals(1, after.coinsCollected)
        assertEquals(TileKind.USED_BLOCK, after.tiles[tileKey(qCol, qRow)]?.kind)
    }

    @Test
    fun candyGameEngine_createsPlayableBoardWithNoInitialMatches() {
        val board = CandyGameEngine.createInitialBoard()
        assertEquals(CandyGameEngine.BOARD_SIZE, board.size)
        assertNull(CandyGameEngine.resolveMatches(board))
        assertTrue(CandyGameEngine.hasValidMove(board))
    }
}
