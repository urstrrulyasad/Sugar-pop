package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.game.GameStatus
import com.example.game.PlatformerEngine
import com.example.game.PlatformerEvent
import com.example.game.PlatformerInput
import com.example.game.PlatformerLevels
import com.example.game.PlatformerWorldState
import com.example.ui.components.PlatformerRenderScratch
import com.example.ui.components.drawPlatformerWorld

private val ArcadeGold = Color(0xFFFFD54F)
private val ArcadePink = Color(0xFFFF4F8B)
private val ArcadeCyan = Color(0xFF40E0D0)

private data class PlatformerHudSnapshot(
    val worldLabel: String,
    val title: String,
    val isHardLevel: Boolean,
    val status: GameStatus,
    val hearts: Int,
    val maxHearts: Int,
    val lives: Int,
    val score: Int,
    val coins: Int,
    val timeLeftInt: Int,
    val starSeconds: Int,
    val starsEarned: Int,
    val flagBonus: Int,
    val timeBonus: Int
)

private fun PlatformerWorldState.toHudSnapshot(): PlatformerHudSnapshot {
    return PlatformerHudSnapshot(
        worldLabel = worldLabel,
        title = title,
        isHardLevel = isHardLevel,
        status = status,
        hearts = player.hearts,
        maxHearts = player.maxHearts,
        lives = player.lives,
        score = score,
        coins = coinsCollected,
        timeLeftInt = timeLeftSec.toInt(),
        starSeconds = if (player.starTimer > 0f) player.starTimer.toInt() + 1 else 0,
        starsEarned = starsEarned,
        flagBonus = flagGrabBonus,
        timeBonus = timeBonus
    )
}

private class WorldHolder(var state: PlatformerWorldState)

@Composable
fun PlatformerGameContent(
    initialLevel: Int,
    highScore: Int,
    restartSignal: Int,
    isExternalDialogOpen: Boolean,
    onSaveProgress: (score: Int, level: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    var currentLevel by remember(initialLevel) { mutableIntStateOf(initialLevel.coerceAtLeast(1)) }
    var maxUnlockedLevel by remember(initialLevel) { mutableIntStateOf(initialLevel.coerceAtLeast(1)) }
    val worldHolder = remember { WorldHolder(PlatformerLevels.createWorldState(currentLevel)) }
    var hud by remember { mutableStateOf(worldHolder.state.toHudSnapshot()) }
    var renderTick by remember { mutableLongStateOf(0L) }
    val renderScratch = remember { PlatformerRenderScratch() }
    var bestScore by remember(highScore) { mutableIntStateOf(highScore) }

    var showLevelPicker by remember { mutableStateOf(false) }

    // Touch + Keyboard input state
    var touchLeft by remember { mutableStateOf(false) }
    var touchRight by remember { mutableStateOf(false) }
    var touchJump by remember { mutableStateOf(false) }
    var touchDash by remember { mutableStateOf(false) }

    var keyLeft by remember { mutableStateOf(false) }
    var keyRight by remember { mutableStateOf(false) }
    var keyJump by remember { mutableStateOf(false) }
    var keyDash by remember { mutableStateOf(false) }

    var prevJumpHeld by remember { mutableStateOf(false) }

    fun setWorldStatus(newStatus: GameStatus) {
        worldHolder.state = worldHolder.state.copy(status = newStatus)
        hud = worldHolder.state.toHudSnapshot()
        renderTick++
    }

    fun loadLevel(level: Int, carriedScore: Int = 0) {
        currentLevel = level.coerceAtLeast(1)
        if (currentLevel > maxUnlockedLevel) {
            maxUnlockedLevel = currentLevel
        }
        val fresh = PlatformerLevels.createWorldState(currentLevel, carriedScore = carriedScore)
        worldHolder.state = fresh
        hud = fresh.toHudSnapshot()
        renderTick++
        prevJumpHeld = false
    }

    LaunchedEffect(restartSignal) {
        if (restartSignal > 0) loadLevel(currentLevel)
    }

    // Main 60Hz Fixed-Step Physics Simulation Loop (decoupled from HUD recomposition)
    val isActivelyPlaying =
        hud.status == GameStatus.PLAYING && !isExternalDialogOpen && !showLevelPicker

    LaunchedEffect(isActivelyPlaying, currentLevel) {
        if (!isActivelyPlaying) return@LaunchedEffect
        var lastFrameNs = 0L
        var accumulator = 0f
        val fixedStep = 1f / 60f

        while (true) {
            withFrameNanos { frameNs ->
                if (lastFrameNs == 0L) {
                    lastFrameNs = frameNs
                    return@withFrameNanos
                }
                val rawDt = ((frameNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastFrameNs = frameNs
                accumulator += rawDt

                var steps = 0
                while (accumulator >= fixedStep && steps < 3) {
                    val prevStatus = worldHolder.state.status
                    val left = touchLeft || keyLeft
                    val right = touchRight || keyRight
                    val jumpHeld = touchJump || keyJump
                    val jumpJustPressed = jumpHeld && !prevJumpHeld
                    prevJumpHeld = jumpHeld
                    val dash = touchDash || keyDash

                    val stepped = PlatformerEngine.step(
                        state = worldHolder.state,
                        input = PlatformerInput(
                            left = left,
                            right = right,
                            jumpHeld = jumpHeld,
                            jumpJustPressed = jumpJustPressed,
                            dash = dash
                        ),
                        rawDt = fixedStep
                    )

                    for (evt in stepped.lastEvents) {
                        when (evt) {
                            PlatformerEvent.STOMP,
                            PlatformerEvent.KICK_SHELL,
                            PlatformerEvent.SPRING,
                            PlatformerEvent.HURT,
                            PlatformerEvent.LEVEL_CLEAR ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            PlatformerEvent.COIN,
                            PlatformerEvent.POWER_UP,
                            PlatformerEvent.CHECKPOINT ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            else -> Unit
                        }
                    }

                    if (stepped.score > bestScore) {
                        bestScore = stepped.score
                    }
                    if (stepped.status == GameStatus.LEVEL_CLEAR && prevStatus != GameStatus.LEVEL_CLEAR) {
                        val unlocked = (currentLevel + 1).coerceAtLeast(maxUnlockedLevel)
                        maxUnlockedLevel = unlocked
                        onSaveProgress(stepped.score, unlocked)
                    } else if (stepped.status == GameStatus.GAME_OVER && prevStatus != GameStatus.GAME_OVER) {
                        onSaveProgress(stepped.score, maxUnlockedLevel)
                    }

                    worldHolder.state = stepped
                    accumulator -= fixedStep
                    steps++
                }

                // Only trigger HUD recomposition if a visible discrete HUD value actually changed
                val nextHud = worldHolder.state.toHudSnapshot()
                if (nextHud != hud) {
                    hud = nextHud
                }
                // Signal Canvas redraw without recomposing outer UI
                renderTick++
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .focusable()
            .onPreviewKeyEvent { event ->
                val pressed = event.type == KeyEventType.KeyDown
                val released = event.type == KeyEventType.KeyUp
                if (!pressed && !released) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft, Key.A -> { keyLeft = pressed; true }
                    Key.DirectionRight, Key.D -> { keyRight = pressed; true }
                    Key.DirectionUp, Key.W, Key.Spacebar -> { keyJump = pressed; true }
                    Key.ShiftLeft, Key.ShiftRight, Key.X -> { keyDash = pressed; true }
                    else -> false
                }
            }
    ) {
        val isCompactHeight = maxHeight < 520.dp
        val deckHeight = if (isCompactHeight) 68.dp else 90.dp
        val dashSize = if (isCompactHeight) 58.dp else 72.dp
        val jumpSize = if (isCompactHeight) 66.dp else 84.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 920.dp)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 4.dp else 6.dp)
        ) {
            // Stage Select + Pause Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { showLevelPicker = true },
                    shape = RoundedCornerShape(50),
                    color = if (hud.isHardLevel) Color(0xFFD50000).copy(alpha = 0.35f) else ArcadePink.copy(alpha = 0.25f),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .border(
                            width = if (hud.isHardLevel) 1.5.dp else 1.dp,
                            color = if (hud.isHardLevel) Color(0xFFFF5252) else ArcadePink.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(50)
                        )
                        .testTag("game_level_picker_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${hud.worldLabel} · ${hud.title} ▾",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (hud.isHardLevel) {
                            Surface(
                                color = Color(0xFFD50000),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.testTag("platformer_hard_badge")
                            ) {
                                Text(
                                    text = "HARD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "BEST $bestScore",
                        style = MaterialTheme.typography.labelSmall,
                        color = ArcadeGold.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        onClick = {
                            setWorldStatus(
                                if (hud.status == GameStatus.PAUSED) GameStatus.PLAYING else GameStatus.PAUSED
                            )
                        },
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.testTag("game_pause_btn")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = if (hud.status == GameStatus.PAUSED) {
                                    Icons.Rounded.PlayArrow
                                } else {
                                    Icons.Rounded.Pause
                                },
                                contentDescription = "Pause Game",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (hud.status == GameStatus.PAUSED) "Resume" else "Pause",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Compact Stats Strip (Score, Coins, Hearts, Lives, Time)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = if (isCompactHeight) 4.dp else 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        repeat(hud.maxHearts) { idx ->
                            val filled = idx < hud.hearts
                            Icon(
                                imageVector = if (filled) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Heart",
                                tint = if (filled) ArcadePink else Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "×${hud.lives}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "SCORE ${hud.score}",
                        style = MaterialTheme.typography.labelLarge,
                        color = ArcadeGold,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "COINS ${hud.coins}",
                        style = MaterialTheme.typography.labelLarge,
                        color = ArcadeCyan,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = "Time Left",
                            tint = if (hud.timeLeftInt < 25) Color(0xFFFF5252) else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${hud.timeLeftInt}s",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (hud.timeLeftInt < 25) Color(0xFFFF5252) else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 2.5D Platformer Viewport (Canvas reads renderTick directly for 0 recomposition overhead)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                    .testTag("platformer_world_canvas")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val frame = renderTick
                    drawPlatformerWorld(worldHolder.state, renderScratch)
                    if (frame < 0L) Unit
                }

                // Active Star Power Badge
                androidx.compose.animation.AnimatedVisibility(
                    visible = hud.starSeconds > 0,
                    enter = scaleIn() + fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                ) {
                    Surface(
                        color = ArcadeGold,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "★ STAR TURBO ${hud.starSeconds}s",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF2B1200),
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                // Pause Overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = hud.status == GameStatus.PAUSED,
                    enter = fadeIn(tween(180)) + scaleIn(spring(0.75f, Spring.StiffnessMedium), initialScale = 0.9f),
                    exit = fadeOut(tween(140))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = Color(0xFF21133F),
                            modifier = Modifier
                                .padding(24.dp)
                                .border(1.5.dp, ArcadeGold.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Game Paused",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${hud.worldLabel} · ${hud.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Button(
                                    onClick = { setWorldStatus(GameStatus.PLAYING) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ArcadePink),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Resume", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { loadLevel(currentLevel) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Restart Stage", color = Color.White)
                                }
                            }
                        }
                    }
                }

                // Level Clear Overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = hud.status == GameStatus.LEVEL_CLEAR,
                    enter = fadeIn(tween(220)) + scaleIn(spring(0.68f, Spring.StiffnessMediumLow), initialScale = 0.85f),
                    exit = fadeOut(tween(140))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.68f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFF221342),
                            modifier = Modifier
                                .widthIn(max = 360.dp)
                                .padding(18.dp)
                                .border(1.5.dp, ArcadeGold, RoundedCornerShape(24.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.EmojiEvents,
                                    contentDescription = null,
                                    tint = ArcadeGold,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = if (hud.isHardLevel) {
                                        "HARD STAGE ${hud.worldLabel} CLEAR!"
                                    } else {
                                        "Stage ${hud.worldLabel} Clear!"
                                    },
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    repeat(3) { idx ->
                                        val lit = idx < hud.starsEarned
                                        Icon(
                                            imageVector = if (lit) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                            contentDescription = null,
                                            tint = if (lit) ArcadeGold else Color.White.copy(alpha = 0.35f),
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Flag Bonus: +${hud.flagBonus}  ·  Time: +${hud.timeBonus}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ArcadeCyan
                                )
                                Text(
                                    text = "Total Score: ${hud.score}  (Best: $bestScore)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ArcadeGold,
                                    fontWeight = FontWeight.Bold
                                )
                                Button(
                                    onClick = { loadLevel(currentLevel + 1, carriedScore = hud.score) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ArcadePink),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("next_level_button")
                                ) {
                                    Text("Next Stage (${currentLevel + 1})", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { loadLevel(currentLevel) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Replay Stage", color = Color.White)
                                }
                            }
                        }
                    }
                }

                // Game Over Overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = hud.status == GameStatus.GAME_OVER,
                    enter = fadeIn(tween(220)) + scaleIn(spring(0.7f, Spring.StiffnessMediumLow), initialScale = 0.88f),
                    exit = fadeOut(tween(140))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.72f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFF28122E),
                            modifier = Modifier
                                .widthIn(max = 340.dp)
                                .padding(18.dp)
                                .border(1.5.dp, ArcadePink, RoundedCornerShape(24.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Out of Lives!",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color(0xFFFF6E91),
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Reached ${hud.worldLabel} · Score ${hud.score}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Button(
                                    onClick = { loadLevel(currentLevel) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ArcadePink),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Retry Stage", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Adaptive Multi-Touch Arcade Controller Deck
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(deckHeight),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .width(if (isCompactHeight) 152.dp else 176.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val activePointers = event.changes.filter { it.pressed }
                                    val halfW = size.width / 2f
                                    touchLeft = activePointers.any { it.position.x < halfW }
                                    touchRight = activePointers.any { it.position.x >= halfW }
                                }
                            }
                        }
                        .testTag("platformer_dpad"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (touchLeft || keyLeft) ArcadeCyan.copy(alpha = 0.28f) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Move Left",
                            tint = if (touchLeft || keyLeft) ArcadeCyan else Color.White,
                            modifier = Modifier.size(if (isCompactHeight) 28.dp else 34.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight(0.6f)
                            .background(Color.White.copy(alpha = 0.18f))
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (touchRight || keyRight) ArcadeCyan.copy(alpha = 0.28f) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Move Right",
                            tint = if (touchRight || keyRight) ArcadeCyan else Color.White,
                            modifier = Modifier.size(if (isCompactHeight) 28.dp else 34.dp)
                        )
                    }
                }

                // Right Action Cluster: Hold DASH + Tap/Hold JUMP with bouncy press feedback
                val dashScale by animateFloatAsState(if (touchDash || keyDash) 0.91f else 1f, spring(stiffness = Spring.StiffnessHigh), label = "dashScale")
                val jumpScale by animateFloatAsState(if (touchJump || keyJump) 0.90f else 1f, spring(stiffness = Spring.StiffnessHigh), label = "jumpScale")

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(dashSize)
                            .graphicsLayer { scaleX = dashScale; scaleY = dashScale }
                            .clip(CircleShape)
                            .background(
                                if (touchDash || keyDash) ArcadeGold else Color.White.copy(alpha = 0.12f)
                            )
                            .border(2.dp, ArcadeGold.copy(alpha = 0.75f), CircleShape)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        touchDash = event.changes.any { it.pressed }
                                    }
                                }
                            }
                            .testTag("platformer_dash_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = "Dash",
                                tint = if (touchDash || keyDash) Color(0xFF28114E) else ArcadeGold,
                                modifier = Modifier.size(if (isCompactHeight) 22.dp else 26.dp)
                            )
                            Text(
                                text = "DASH",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (touchDash || keyDash) Color(0xFF28114E) else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(jumpSize)
                            .graphicsLayer { scaleX = jumpScale; scaleY = jumpScale }
                            .clip(CircleShape)
                            .background(
                                if (touchJump || keyJump) ArcadePink else ArcadePink.copy(alpha = 0.28f)
                            )
                            .border(2.5.dp, ArcadePink, CircleShape)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        touchJump = event.changes.any { it.pressed }
                                    }
                                }
                            }
                            .testTag("platformer_jump_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowUpward,
                                contentDescription = "Jump",
                                tint = Color.White,
                                modifier = Modifier.size(if (isCompactHeight) 24.dp else 30.dp)
                            )
                            Text(
                                text = "JUMP",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLevelPicker) {
        val stages = remember(maxUnlockedLevel) {
            (1..maxOf(PlatformerLevels.TOTAL_CAMPAIGN_LEVELS, maxUnlockedLevel)).toList()
        }
        AlertDialog(
            onDismissRequest = { showLevelPicker = false },
            title = { Text("Select Stage") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.height(260.dp)
                ) {
                    items(stages) { lvl ->
                        val spec = remember(lvl) { PlatformerLevels.buildLevel(lvl) }
                        val unlocked = lvl <= maxUnlockedLevel
                        Surface(
                            onClick = {
                                if (unlocked) {
                                    showLevelPicker = false
                                    loadLevel(lvl)
                                }
                            },
                            enabled = unlocked,
                            shape = RoundedCornerShape(12.dp),
                            color = if (lvl == currentLevel) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else if (unlocked) {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = spec.worldLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (spec.isHardLevel) {
                                            Surface(
                                                color = Color(0xFFD50000),
                                                shape = RoundedCornerShape(50)
                                            ) {
                                                Text(
                                                    text = "HARD",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        if (!unlocked) {
                                            Icon(
                                                Icons.Rounded.Lock,
                                                contentDescription = "Locked",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = spec.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLevelPicker = false }) { Text("Close") }
            }
        )
    }
}
