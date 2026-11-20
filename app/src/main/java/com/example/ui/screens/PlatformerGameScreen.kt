package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.GameStatus
import com.example.game.PlatformerEngine
import com.example.game.PlatformerEvent
import com.example.game.PlatformerInput
import com.example.game.PlatformerLevels
import com.example.ui.components.drawPlatformerWorld
import kotlin.math.floor
import kotlin.math.roundToInt

private val ArcadeBg = Color(0xFF130B2B)
private val ArcadeGold = Color(0xFFFFD54F)
private val ArcadePink = Color(0xFFFF4F8B)
private val ArcadeCyan = Color(0xFF40E0D0)

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
    val density = LocalDensity.current

    var currentLevel by remember(initialLevel) { mutableIntStateOf(initialLevel.coerceAtLeast(1)) }
    var maxUnlockedLevel by remember(initialLevel) { mutableIntStateOf(initialLevel.coerceAtLeast(1)) }
    var worldState by remember { mutableStateOf(PlatformerLevels.createWorldState(currentLevel)) }
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

    fun loadLevel(level: Int, carriedScore: Int = 0) {
        currentLevel = level.coerceAtLeast(1)
        if (currentLevel > maxUnlockedLevel) {
            maxUnlockedLevel = currentLevel
        }
        worldState = PlatformerLevels.createWorldState(currentLevel, carriedScore = carriedScore)
        prevJumpHeld = false
    }

    LaunchedEffect(restartSignal) {
        if (restartSignal > 0) loadLevel(currentLevel)
    }

    // Main 60fps Fixed-Step Simulation Loop
    val isActivelyPlaying =
        worldState.status == GameStatus.PLAYING && !isExternalDialogOpen && !showLevelPicker

    LaunchedEffect(isActivelyPlaying, currentLevel) {
        if (!isActivelyPlaying) return@LaunchedEffect
        var lastFrameNs = 0L
        while (true) {
            withFrameNanos { frameNs ->
                if (lastFrameNs == 0L) {
                    lastFrameNs = frameNs
                    return@withFrameNanos
                }
                val dt = ((frameNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
                lastFrameNs = frameNs

                val left = touchLeft || keyLeft
                val right = touchRight || keyRight
                val jumpHeld = touchJump || keyJump
                val jumpJustPressed = jumpHeld && !prevJumpHeld
                prevJumpHeld = jumpHeld
                val dash = touchDash || keyDash

                val stepped = PlatformerEngine.step(
                    state = worldState,
                    input = PlatformerInput(
                        left = left,
                        right = right,
                        jumpHeld = jumpHeld,
                        jumpJustPressed = jumpJustPressed,
                        dash = dash
                    ),
                    rawDt = dt
                )

                // Haptic feedback for key events
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
                if (stepped.status == GameStatus.LEVEL_CLEAR && worldState.status != GameStatus.LEVEL_CLEAR) {
                    val unlocked = (currentLevel + 1).coerceAtLeast(maxUnlockedLevel)
                    maxUnlockedLevel = unlocked
                    onSaveProgress(stepped.score, unlocked)
                } else if (stepped.status == GameStatus.GAME_OVER && worldState.status != GameStatus.GAME_OVER) {
                    onSaveProgress(stepped.score, maxUnlockedLevel)
                }

                worldState = stepped
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusable()
            .onPreviewKeyEvent { event ->
                val pressed = event.type == KeyEventType.KeyDown
                val released = event.type == KeyEventType.KeyUp
                if (!pressed && !released) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft, Key.A -> {
                        keyLeft = pressed; true
                    }
                    Key.DirectionRight, Key.D -> {
                        keyRight = pressed; true
                    }
                    Key.DirectionUp, Key.W, Key.Spacebar -> {
                        keyJump = pressed; true
                    }
                    Key.ShiftLeft, Key.ShiftRight, Key.X -> {
                        keyDash = pressed; true
                    }
                    else -> false
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 920.dp)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    color = ArcadePink.copy(alpha = 0.25f),
                    modifier = Modifier
                        .border(1.dp, ArcadePink.copy(alpha = 0.6f), RoundedCornerShape(50))
                        .testTag("game_level_picker_btn")
                ) {
                    Text(
                        text = "${worldState.worldLabel} · ${worldState.title} ▾",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
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
                            worldState = worldState.copy(
                                status = if (worldState.status == GameStatus.PAUSED) GameStatus.PLAYING else GameStatus.PAUSED
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
                                imageVector = if (worldState.status == GameStatus.PAUSED) {
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
                                text = if (worldState.status == GameStatus.PAUSED) "Resume" else "Pause",
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
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        repeat(worldState.player.maxHearts) { idx ->
                            val filled = idx < worldState.player.hearts
                            Icon(
                                imageVector = if (filled) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Heart",
                                tint = if (filled) ArcadePink else Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "×${worldState.player.lives}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "SCORE ${worldState.score}",
                        style = MaterialTheme.typography.labelLarge,
                        color = ArcadeGold,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "COINS ${worldState.coinsCollected}",
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
                            tint = if (worldState.timeLeftSec < 25f) Color(0xFFFF5252) else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${worldState.timeLeftSec.toInt()}s",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (worldState.timeLeftSec < 25f) Color(0xFFFF5252) else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 2.5D Platformer Viewport
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                    .testTag("platformer_world_canvas")
            ) {
                val widthPx = with(density) { maxWidth.toPx() }
                val heightPx = with(density) { maxHeight.toPx() }
                val tilePx = (heightPx / 12.2f).coerceAtLeast(18f)
                val floorBaseline = heightPx - tilePx * 0.2f

                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawPlatformerWorld(worldState)
                }

                // Floating Score / PowerUp Popups
                for (pop in worldState.popups) {
                    val px = (pop.x - worldState.cameraX) * tilePx
                    val py = floorBaseline - pop.y * tilePx
                    if (px in 0f..widthPx && py in 0f..heightPx) {
                        Text(
                            text = pop.text,
                            color = Color(pop.colorHex),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.offset {
                                IntOffset((px - 28f).roundToInt(), (py - 18f).roundToInt())
                            }
                        )
                    }
                }

                // Active Star Power Badge
                if (worldState.player.starTimer > 0f) {
                    Surface(
                        color = ArcadeGold,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                    ) {
                        Text(
                            text = "★ STAR TURBO ${worldState.player.starTimer.toInt() + 1}s",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF2B1200),
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                // Pause Overlay
                if (worldState.status == GameStatus.PAUSED) {
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
                                    text = "${worldState.worldLabel} · ${worldState.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Button(
                                    onClick = { worldState = worldState.copy(status = GameStatus.PLAYING) },
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
                if (worldState.status == GameStatus.LEVEL_CLEAR) {
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
                                    text = "Stage ${worldState.worldLabel} Clear!",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    repeat(3) { idx ->
                                        val lit = idx < worldState.starsEarned
                                        Icon(
                                            imageVector = if (lit) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                            contentDescription = null,
                                            tint = if (lit) ArcadeGold else Color.White.copy(alpha = 0.35f),
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Flag Bonus: +${worldState.flagGrabBonus}  ·  Time: +${worldState.timeBonus}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ArcadeCyan
                                )
                                Text(
                                    text = "Total Score: ${worldState.score}  (Best: $bestScore)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ArcadeGold,
                                    fontWeight = FontWeight.Bold
                                )
                                Button(
                                    onClick = { loadLevel(currentLevel + 1, carriedScore = worldState.score) },
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
                if (worldState.status == GameStatus.GAME_OVER) {
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
                                    text = "Reached ${worldState.worldLabel} · Score ${worldState.score}",
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

            // Dedicated Multi-Touch Arcade Controller Deck
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Multi-touch Left / Right D-Pad (supports seamless thumb slide between Left & Right)
                Row(
                    modifier = Modifier
                        .width(176.dp)
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
                            modifier = Modifier.size(34.dp)
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
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                // Right Action Cluster: Hold DASH + Tap/Hold JUMP
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // DASH Button
                    Box(
                        modifier = Modifier
                            .size(72.dp)
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
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                text = "DASH",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (touchDash || keyDash) Color(0xFF28114E) else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // JUMP Button
                    Box(
                        modifier = Modifier
                            .size(84.dp)
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
                                modifier = Modifier.size(30.dp)
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
                                    if (!unlocked) {
                                        Icon(
                                            Icons.Rounded.Lock,
                                            contentDescription = "Locked",
                                            modifier = Modifier.size(14.dp)
                                        )
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
