package com.example.game

enum class WorldTheme(
    val worldTitle: String
) {
    SUNNY_MEADOW("Sunny Meadow"),
    SUNSET_CANYON("Sunset Canyon"),
    TWILIGHT_CAVERN("Twilight Cavern"),
    AURORA_GLACIER("Aurora Glacier"),
    LAVA_CITADEL("Lava Citadel")
}

enum class TileKind {
    GROUND,
    ICE,
    BRICK,
    QUESTION_COIN,
    QUESTION_BERRY,
    QUESTION_STAR,
    USED_BLOCK,
    PIPE_TOP,
    PIPE_BODY,
    CRUMBLE_BRIDGE,
    SPIKES,
    SPRING
}

data class WorldTile(
    val col: Int,
    val row: Int,
    val kind: TileKind,
    val bumpOffset: Float = 0f,
    val bumpVelocity: Float = 0f,
    val crumbleTimer: Float = -1f, // >= 0 means triggered and counting down
    val respawnTimer: Float = -1f  // >= 0 means fallen and waiting to respawn
) {
    val isSolid: Boolean
        get() = when (kind) {
            TileKind.SPIKES -> false
            TileKind.CRUMBLE_BRIDGE -> respawnTimer < 0f
            else -> true
        }
}

enum class PlatformMotionAxis {
    HORIZONTAL,
    VERTICAL
}

data class MovingPlatform(
    val id: Int,
    val baseX: Float,
    val baseY: Float,
    val x: Float,
    val y: Float,
    val prevX: Float,
    val prevY: Float,
    val width: Float = 2.6f,
    val height: Float = 0.55f,
    val axis: PlatformMotionAxis,
    val range: Float,
    val speed: Float,
    val phase: Float = 0f
)

data class CoinItem(
    val id: Int,
    val x: Float,
    val y: Float,
    val collected: Boolean = false
)

enum class PowerUpKind {
    BERRY,
    STAR
}

data class PowerUpItem(
    val id: Int,
    val kind: PowerUpKind,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val spawnRiseTimer: Float = 0.28f
)

enum class EnemyKind {
    JELLY_WALKER,
    SHELL_BEETLE,
    PIPE_SPROUT
}

enum class EnemyState {
    ACTIVE,
    RETRACTED_SHELL,
    SLIDING_SHELL,
    SQUASHED
}

data class EnemyEntity(
    val id: Int,
    val kind: EnemyKind,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val width: Float = 0.78f,
    val height: Float = 0.78f,
    val state: EnemyState = EnemyState.ACTIVE,
    val stateTimer: Float = 0f,
    val anchorY: Float = y,
    val sproutCycleOffset: Float = 0f
)

enum class ParticleKind {
    DUST,
    COIN_SPARKLE,
    BRICK_SHARD,
    STAR_TRAIL,
    FIREWORK
}

data class ParticleEffect(
    val id: Int,
    val kind: ParticleKind,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val life: Float,
    val maxLife: Float,
    val colorHex: Long,
    val size: Float = 0.22f
)

data class FloatingPopup(
    val id: Int,
    val text: String,
    val x: Float,
    val y: Float,
    val life: Float = 0.85f,
    val colorHex: Long = 0xFFFFD54FL
)

data class CheckpointFlag(
    val x: Float,
    val y: Float,
    val activated: Boolean = false
)

data class GoalFlagpole(
    val x: Float,
    val baseY: Float,
    val height: Float = 5.5f
)

data class PlayerState(
    val x: Float,
    val y: Float,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val width: Float = 0.68f,
    val height: Float = 0.92f,
    val facingRight: Boolean = true,
    val onGround: Boolean = false,
    val onIce: Boolean = false,
    val hearts: Int = 2,
    val maxHearts: Int = 2,
    val lives: Int = 3,
    val coyoteTimer: Float = 0f,
    val jumpBufferTimer: Float = 0f,
    val invincibleHurtTimer: Float = 0f,
    val starTimer: Float = 0f,
    val runAnimPhase: Float = 0f,
    val squashX: Float = 1f,
    val squashY: Float = 1f,
    val shellCombo: Int = 0
)

data class PlatformerInput(
    val left: Boolean = false,
    val right: Boolean = false,
    val jumpHeld: Boolean = false,
    val jumpJustPressed: Boolean = false,
    val dash: Boolean = false
)

enum class GameStatus {
    PLAYING,
    PAUSED,
    LEVEL_CLEAR,
    GAME_OVER
}

enum class PlatformerEvent {
    JUMP,
    COIN,
    STOMP,
    KICK_SHELL,
    BLOCK_BONK,
    BREAK_BRICK,
    POWER_UP,
    SPRING,
    CHECKPOINT,
    HURT,
    LEVEL_CLEAR
}

data class PlatformerLevelSpec(
    val levelNumber: Int,
    val worldLabel: String,
    val title: String,
    val subtitle: String,
    val theme: WorldTheme,
    val widthTiles: Int,
    val heightTiles: Int,
    val timeLimitSec: Float,
    val speedMultiplier: Float,
    val spawnX: Float,
    val spawnY: Float,
    val checkpoint: CheckpointFlag?,
    val goal: GoalFlagpole,
    val initialTiles: Map<Long, WorldTile>,
    val initialPlatforms: List<MovingPlatform>,
    val initialCoins: List<CoinItem>,
    val initialEnemies: List<EnemyEntity>
)

data class PlatformerWorldState(
    val levelNumber: Int,
    val worldLabel: String,
    val title: String,
    val subtitle: String,
    val theme: WorldTheme,
    val widthTiles: Int,
    val heightTiles: Int,
    val timeLeftSec: Float,
    val timeLimitSec: Float,
    val speedMultiplier: Float,
    val spawnX: Float,
    val spawnY: Float,
    val respawnX: Float,
    val respawnY: Float,
    val status: GameStatus = GameStatus.PLAYING,
    val score: Int = 0,
    val coinsCollected: Int = 0,
    val enemiesDefeated: Int = 0,
    val starsEarned: Int = 0,
    val flagGrabBonus: Int = 0,
    val timeBonus: Int = 0,
    val player: PlayerState,
    val tiles: Map<Long, WorldTile>,
    val movingPlatforms: List<MovingPlatform>,
    val coins: List<CoinItem>,
    val powerUps: List<PowerUpItem> = emptyList(),
    val enemies: List<EnemyEntity>,
    val particles: List<ParticleEffect> = emptyList(),
    val popups: List<FloatingPopup> = emptyList(),
    val checkpoint: CheckpointFlag?,
    val goal: GoalFlagpole,
    val cameraX: Float = 0f,
    val worldTime: Float = 0f,
    val nextEntityId: Int = 1000,
    val lastEvents: List<PlatformerEvent> = emptyList()
)

fun tileKey(col: Int, row: Int): Long = (col.toLong() shl 32) or (row.toLong() and 0xFFFFFFFFL)
