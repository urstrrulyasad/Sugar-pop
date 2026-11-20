package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Hardware
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.CandyCell
import com.example.game.CandyGameEngine
import com.example.game.CandySpecial
import com.example.game.CandyType
import com.example.game.GridPos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val CandyBgTop = Color(0xFF28114E)
private val CandyBgMid = Color(0xFF3F186E)
private val CandyBgBottom = Color(0xFF1A0A35)
private val CandyPinkAccent = Color(0xFFFF5CA8)
private val CandyGoldAccent = Color(0xFFFFD15C)
private val CandyCyanAccent = Color(0xFF53E5FF)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CandyGameScreen(
    initialLevel: Int,
    highScore: Int,
    hasPinConfigured: Boolean,
    onSaveProgress: (score: Int, level: Int) -> Unit,
    onOpenSecretVault: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var currentLevel by remember(initialLevel) { mutableIntStateOf(initialLevel.coerceAtLeast(1)) }
    val levelConfig = remember(currentLevel) { CandyGameEngine.levelConfig(currentLevel) }

    var board by remember { mutableStateOf(CandyGameEngine.createInitialBoard()) }
    var score by remember { mutableIntStateOf(0) }
    var movesLeft by remember(levelConfig) { mutableIntStateOf(levelConfig.maxMoves) }
    var hammersLeft by remember(levelConfig) { mutableIntStateOf(3) }
    var isHammerArmed by remember { mutableStateOf(false) }

    var selectedPos by remember { mutableStateOf<GridPos?>(null) }
    var clearingCells by remember { mutableStateOf<Set<GridPos>>(emptySet()) }
    var isAnimating by remember { mutableStateOf(false) }
    var comboBanner by remember { mutableStateOf<String?>(null) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showSetupHintBanner by remember(hasPinConfigured) { mutableStateOf(!hasPinConfigured) }

    val levelWon = score >= levelConfig.targetScore && !isAnimating
    val levelLost = movesLeft <= 0 && score < levelConfig.targetScore && !isAnimating

    LaunchedEffect(comboBanner) {
        if (comboBanner != null) {
            delay(900L)
            comboBanner = null
        }
    }

    fun startLevel(targetLevel: Int) {
        currentLevel = targetLevel
        val cfg = CandyGameEngine.levelConfig(targetLevel)
        board = CandyGameEngine.createInitialBoard()
        score = 0
        movesLeft = cfg.maxMoves
        hammersLeft = 3
        isHammerArmed = false
        selectedPos = null
        clearingCells = emptySet()
        isAnimating = false
    }

    fun executeCascadeAfterInitial(
        startingBoard: List<List<CandyCell>>,
        startingScoreGain: Int
    ) {
        scope.launch {
            var currentBoard = startingBoard
            var combo = 2
            score += startingScoreGain
            onSaveProgress(score, currentLevel)

            while (true) {
                val step = CandyGameEngine.resolveMatches(
                    board = currentBoard,
                    comboMultiplier = combo
                ) ?: break

                clearingCells = step.clearedPositions
                comboBanner = when {
                    combo == 2 -> "SWEET! ×2"
                    combo == 3 -> "TASTY! ×3"
                    combo >= 4 -> "SUGAR RUSH! ×$combo"
                    else -> null
                }
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                delay(210L)

                currentBoard = step.nextBoardAfterClearAndDrop
                board = currentBoard
                clearingCells = emptySet()
                score += step.pointsEarned
                onSaveProgress(score, currentLevel)
                combo++
                delay(180L)
            }

            if (!CandyGameEngine.hasValidMove(currentBoard)) {
                board = CandyGameEngine.shuffleBoard(currentBoard)
                comboBanner = "RESHUFFLED!"
            }
            isAnimating = false
        }
    }

    fun handleSwap(a: GridPos, b: GridPos) {
        if (isAnimating || levelWon || levelLost) return
        if (a.row !in 0 until CandyGameEngine.BOARD_SIZE ||
            a.col !in 0 until CandyGameEngine.BOARD_SIZE ||
            b.row !in 0 until CandyGameEngine.BOARD_SIZE ||
            b.col !in 0 until CandyGameEngine.BOARD_SIZE
        ) return
        if (!CandyGameEngine.areAdjacent(a, b)) return

        selectedPos = null
        isAnimating = true

        scope.launch {
            if (CandyGameEngine.isColorBombSwap(board, a, b)) {
                movesLeft = (movesLeft - 1).coerceAtLeast(0)
                val step = CandyGameEngine.resolveColorBombSwap(board, a, b)
                clearingCells = step.clearedPositions
                comboBanner = "COLOR BLAST!"
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(240L)
                board = step.nextBoardAfterClearAndDrop
                clearingCells = emptySet()
                executeCascadeAfterInitial(step.nextBoardAfterClearAndDrop, step.pointsEarned)
                return@launch
            }

            val swapped = CandyGameEngine.swap(board, a, b)
            board = swapped
            delay(150L)

            val firstMatch = CandyGameEngine.resolveMatches(
                board = swapped,
                preferredPivotA = a,
                preferredPivotB = b,
                comboMultiplier = 1
            )
            if (firstMatch == null) {
                // Invalid move — bounce back without spending a move
                board = CandyGameEngine.swap(swapped, a, b)
                isAnimating = false
                return@launch
            }

            movesLeft = (movesLeft - 1).coerceAtLeast(0)
            clearingCells = firstMatch.clearedPositions
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            delay(210L)
            board = firstMatch.nextBoardAfterClearAndDrop
            clearingCells = emptySet()
            executeCascadeAfterInitial(firstMatch.nextBoardAfterClearAndDrop, firstMatch.pointsEarned)
        }
    }

    fun handleCellTap(pos: GridPos) {
        if (isAnimating || levelWon || levelLost) return

        if (isHammerArmed && hammersLeft > 0) {
            isHammerArmed = false
            hammersLeft--
            isAnimating = true
            scope.launch {
                val step = CandyGameEngine.smashSingleCell(board, pos)
                clearingCells = step.clearedPositions
                comboBanner = "CRUSHED!"
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(210L)
                board = step.nextBoardAfterClearAndDrop
                clearingCells = emptySet()
                executeCascadeAfterInitial(step.nextBoardAfterClearAndDrop, step.pointsEarned)
            }
            return
        }

        val currentSel = selectedPos
        if (currentSel == null) {
            selectedPos = pos
        } else if (currentSel == pos) {
            selectedPos = null
        } else if (CandyGameEngine.areAdjacent(currentSel, pos)) {
            handleSwap(currentSel, pos)
        } else {
            selectedPos = pos
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(CandyBgTop, CandyBgMid, CandyBgBottom)
                )
            )
            .systemBarsPadding()
    ) {
        // Subtle floating bokeh accents for playful candy atmosphere
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = CandyPinkAccent.copy(alpha = 0.08f),
                radius = size.width * 0.35f,
                center = Offset(size.width * 0.15f, size.height * 0.14f)
            )
            drawCircle(
                color = CandyCyanAccent.copy(alpha = 0.07f),
                radius = size.width * 0.28f,
                center = Offset(size.width * 0.88f, size.height * 0.78f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 540.dp)
                .align(Alignment.Center)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Game Header (Long-press "Sugar Pop" badge to unlock Secret Vault)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.13f),
                    modifier = Modifier
                        .border(
                            1.5.dp,
                            Brush.horizontalGradient(listOf(CandyPinkAccent, CandyGoldAccent)),
                            RoundedCornerShape(50)
                        )
                        .clip(RoundedCornerShape(50))
                        .combinedClickable(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpenSecretVault()
                            }
                        )
                        .testTag("sugar_pop_secret_trigger")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Sugar Pop Logo",
                            tint = CandyGoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Sugar Pop",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { showRulesDialog = true },
                        modifier = Modifier.testTag("game_help_btn")
                    ) {
                        Icon(
                            Icons.Rounded.HelpOutline,
                            contentDescription = "How to Play",
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    IconButton(
                        onClick = { startLevel(currentLevel) },
                        modifier = Modifier.testTag("game_restart_btn")
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Restart Level",
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // First-launch-only Vault Setup Notice (disappears completely once PIN is configured)
            AnimatedVisibility(visible = showSetupHintBanner && !hasPinConfigured) {
                Surface(
                    onClick = onOpenSecretVault,
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .border(1.dp, CandyGoldAccent.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .testTag("first_launch_vault_setup_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = CandyGoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tap to Set Secret Photo Vault PIN",
                                style = MaterialTheme.typography.titleSmall,
                                color = CandyGoldAccent
                            )
                            Text(
                                text = "After setup, long-press \"Sugar Pop\" above anytime to open your hidden vault.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.86f)
                            )
                        }
                    }
                }
            }

            // Level, Score, Target Progress & Moves HUD
            Surface(
                color = Color.White.copy(alpha = 0.11f),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "LEVEL ${levelConfig.level} · ${levelConfig.name}",
                                style = MaterialTheme.typography.labelMedium,
                                color = CandyGoldAccent
                            )
                            Text(
                                text = "$score / ${levelConfig.targetScore}",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HudMetricBadge(
                                label = "BEST",
                                value = maxOf(highScore, score).toString(),
                                accent = CandyCyanAccent
                            )
                            HudMetricBadge(
                                label = "MOVES",
                                value = movesLeft.toString(),
                                accent = if (movesLeft <= 5) Color(0xFFFF6B8B) else CandyPinkAccent
                            )
                        }
                    }

                    val progress = (score.toFloat() / levelConfig.targetScore.coerceAtLeast(1))
                        .coerceIn(0f, 1f)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            color = CandyGoldAccent,
                            trackColor = Color.White.copy(alpha = 0.16f),
                            modifier = Modifier
                                .weight(1f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(50))
                        )
                        repeat(3) { starIdx ->
                            val threshold = (starIdx + 1) / 3f
                            val earned = progress >= threshold
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = if (earned) CandyGoldAccent else Color.White.copy(alpha = 0.24f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // 8x8 Interactive Match-3 Candy Board
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1A0C36).copy(alpha = 0.88f),
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            2.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.32f),
                                    CandyPinkAccent.copy(alpha = 0.35f)
                                )
                            ),
                            RoundedCornerShape(24.dp)
                        )
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        val cellSize = maxWidth / CandyGameEngine.BOARD_SIZE

                        Column(modifier = Modifier.fillMaxSize()) {
                            for (r in 0 until CandyGameEngine.BOARD_SIZE) {
                                Row(modifier = Modifier.weight(1f)) {
                                    for (c in 0 until CandyGameEngine.BOARD_SIZE) {
                                        val pos = GridPos(r, c)
                                        val cell = board[r][c]
                                        val isSelected = selectedPos == pos
                                        val isClearing = pos in clearingCells
                                        val isCheckerDark = (r + c) % 2 == 0

                                        CandyTileCell(
                                            cell = cell,
                                            isSelected = isSelected,
                                            isClearing = isClearing,
                                            isHammerArmed = isHammerArmed,
                                            isCheckerDark = isCheckerDark,
                                            onTap = { handleCellTap(pos) },
                                            onSwipe = { dr, dc ->
                                                handleSwap(pos, GridPos(pos.row + dr, pos.col + dc))
                                            },
                                            modifier = Modifier
                                                .size(cellSize)
                                                .testTag("candy_cell_${r}_${c}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Floating Combo Banner
                this@Column.AnimatedVisibility(
                    visible = comboBanner != null,
                    enter = scaleIn(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    Surface(
                        color = CandyGoldAccent,
                        shape = RoundedCornerShape(50),
                        shadowElevation = 8.dp
                    ) {
                        Text(
                            text = comboBanner.orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF28114E),
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // Bottom Booster Dock
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = {
                        if (hammersLeft > 0 && !isAnimating) {
                            isHammerArmed = !isHammerArmed
                            selectedPos = null
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isHammerArmed) {
                        CandyGoldAccent
                    } else {
                        Color.White.copy(alpha = 0.13f)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("booster_hammer_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Hardware,
                            contentDescription = "Candy Hammer",
                            tint = if (isHammerArmed) Color(0xFF28114E) else CandyGoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isHammerArmed) "Tap Any Candy!" else "Hammer ($hammersLeft)",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isHammerArmed) Color(0xFF28114E) else Color.White
                        )
                    }
                }

                Surface(
                    onClick = {
                        if (!isAnimating) {
                            board = CandyGameEngine.shuffleBoard(board)
                            comboBanner = "SHUFFLED!"
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.13f),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("booster_shuffle_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle Board",
                            tint = CandyCyanAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Shuffle",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    if (levelWon) {
        AlertDialog(
            onDismissRequest = {},
            icon = {
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = null,
                    tint = CandyGoldAccent,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text("Level ${levelConfig.level} Complete!", textAlign = TextAlign.Center)
            },
            text = {
                Text(
                    "Sweet victory! You scored $score points and unlocked Level ${levelConfig.level + 1}.",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val next = currentLevel + 1
                        onSaveProgress(score, next)
                        startLevel(next)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CandyPinkAccent)
                ) {
                    Text("Next Level", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { startLevel(currentLevel) }) {
                    Text("Replay")
                }
            }
        )
    }

    if (levelLost) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Out of Moves!") },
            text = {
                Text("You reached $score of ${levelConfig.targetScore} points. Give this board another try!")
            },
            confirmButton = {
                Button(
                    onClick = { startLevel(currentLevel) },
                    colors = ButtonDefaults.buttonColors(containerColor = CandyPinkAccent)
                ) {
                    Text("Try Again", color = Color.White)
                }
            }
        )
    }

    if (showRulesDialog) {
        AlertDialog(
            onDismissRequest = { showRulesDialog = false },
            title = { Text("How to Play Sugar Pop") },
            text = {
                Text(
                    "• Swap or swipe adjacent candies to match 3 or more of the same color.\n" +
                        "• Match 4 candies to create a Striped Candy that clears an entire row or column.\n" +
                        "• Match 5 candies to craft a Color Bomb that blasts every candy of a swapped color!\n" +
                        "• Use the Candy Hammer booster anytime to smash a tricky tile."
                )
            },
            confirmButton = {
                TextButton(onClick = { showRulesDialog = false }) {
                    Text("Got It")
                }
            }
        )
    }
}

@Composable
private fun HudMetricBadge(
    label: String,
    value: String,
    accent: Color
) {
    Surface(
        color = Color.Black.copy(alpha = 0.28f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontSize = 10.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CandyTileCell(
    cell: CandyCell,
    isSelected: Boolean,
    isClearing: Boolean,
    isHammerArmed: Boolean,
    isCheckerDark: Boolean,
    onTap: () -> Unit,
    onSwipe: (dr: Int, dc: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = when {
            isClearing -> 0.15f
            isSelected -> 1.12f
            else -> 1f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "candyScale"
    )

    Box(
        modifier = modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isSelected -> CandyGoldAccent.copy(alpha = 0.30f)
                    isCheckerDark -> Color.White.copy(alpha = 0.08f)
                    else -> Color.White.copy(alpha = 0.03f)
                }
            )
            .then(
                if (isSelected) {
                    Modifier.border(1.5.dp, CandyGoldAccent, RoundedCornerShape(10.dp))
                } else if (isHammerArmed) {
                    Modifier.border(
                        1.dp,
                        CandyGoldAccent.copy(alpha = 0.25f),
                        RoundedCornerShape(10.dp)
                    )
                } else {
                    Modifier
                }
            )
            .pointerInput(cell.id) {
                detectTapGestures(onTap = { onTap() })
            }
            .pointerInput(cell.id) {
                var totalX = 0f
                var totalY = 0f
                var triggered = false
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                        triggered = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (triggered) return@detectDragGestures
                        totalX += dragAmount.x
                        totalY += dragAmount.y
                        val threshold = 24f
                        if (abs(totalX) > threshold || abs(totalY) > threshold) {
                            triggered = true
                            if (abs(totalX) > abs(totalY)) {
                                onSwipe(0, if (totalX > 0) 1 else -1)
                            } else {
                                onSwipe(if (totalY > 0) 1 else -1, 0)
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize(0.82f)
                .scale(scale)
        ) {
            drawGlossyCandy(cell)
        }
    }
}

private fun DrawScope.drawGlossyCandy(cell: CandyCell) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val r = minOf(w, h) * 0.45f

    if (cell.special == CandySpecial.COLOR_BOMB) {
        // Chocolate Sprinkle Bomb
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF6B3E2E), Color(0xFF2B130B)),
                center = Offset(cx - r * 0.25f, cy - r * 0.25f)
            ),
            radius = r
        )
        val sprinkleColors = listOf(
            Color(0xFFFF4D6D),
            Color(0xFFFFD166),
            Color(0xFF06D6A0),
            Color(0xFF4CC9F0),
            Color(0xFFC77DFF),
            Color.White
        )
        for (i in 0 until 6) {
            val angle = (i * 60f) * (PI / 180.0)
            val sx = cx + (r * 0.52f * cos(angle)).toFloat()
            val sy = cy + (r * 0.52f * sin(angle)).toFloat()
            drawCircle(color = sprinkleColors[i], radius = r * 0.16f, center = Offset(sx, sy))
        }
        drawCircle(color = Color.White.copy(alpha = 0.9f), radius = r * 0.22f)
        return
    }

    val (lightColor, darkColor) = when (cell.type) {
        CandyType.STRAWBERRY -> Color(0xFFFF6B8B) to Color(0xFFC9184A)
        CandyType.LEMON -> Color(0xFFFFE066) to Color(0xFFF59F00)
        CandyType.MINT -> Color(0xFF5EEAD4) to Color(0xFF0F766E)
        CandyType.BLUEBERRY -> Color(0xFF60A5FA) to Color(0xFF1D4ED8)
        CandyType.GRAPE -> Color(0xFFD8B4FE) to Color(0xFF7E22CE)
        CandyType.TANGERINE -> Color(0xFFFF9F68) to Color(0xFFEA580C)
    }

    val brush = Brush.radialGradient(
        colors = listOf(lightColor, darkColor),
        center = Offset(cx - r * 0.3f, cy - r * 0.3f),
        radius = r * 1.4f
    )

    when (cell.type) {
        CandyType.STRAWBERRY -> {
            // Glossy Heart/Berry
            val path = Path().apply {
                moveTo(cx, cy + r * 0.85f)
                cubicTo(cx - r * 1.15f, cy + r * 0.1f, cx - r * 0.95f, cy - r * 0.95f, cx, cy - r * 0.32f)
                cubicTo(cx + r * 0.95f, cy - r * 0.95f, cx + r * 1.15f, cy + r * 0.1f, cx, cy + r * 0.85f)
                close()
            }
            drawPath(path, brush = brush)
        }
        CandyType.LEMON -> {
            // 5-Point Star
            val path = Path()
            val outer = r * 1.02f
            val inner = r * 0.46f
            for (i in 0 until 10) {
                val rad = if (i % 2 == 0) outer else inner
                val angle = (-90.0 + i * 36.0) * (PI / 180.0)
                val x = cx + (rad * cos(angle)).toFloat()
                val y = cy + (rad * sin(angle)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, brush = brush)
        }
        CandyType.MINT -> {
            // Rounded Hexagon Gem
            val path = Path()
            for (i in 0 until 6) {
                val angle = (-90.0 + i * 60.0) * (PI / 180.0)
                val x = cx + (r * 0.95f * cos(angle)).toFloat()
                val y = cy + (r * 0.95f * sin(angle)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, brush = brush)
        }
        CandyType.BLUEBERRY -> {
            // Faceted Diamond
            val path = Path().apply {
                moveTo(cx, cy - r)
                lineTo(cx + r * 0.9f, cy)
                lineTo(cx, cy + r)
                lineTo(cx - r * 0.9f, cy)
                close()
            }
            drawPath(path, brush = brush)
        }
        CandyType.GRAPE -> {
            // Glossy Orb
            drawCircle(brush = brush, radius = r * 0.92f, center = Offset(cx, cy))
            drawCircle(
                color = Color.White.copy(alpha = 0.25f),
                radius = r * 0.56f,
                center = Offset(cx, cy),
                style = Stroke(width = r * 0.14f)
            )
        }
        CandyType.TANGERINE -> {
            // Cushion Square Candy
            drawRoundRect(
                brush = brush,
                topLeft = Offset(cx - r * 0.85f, cy - r * 0.85f),
                size = Size(r * 1.7f, r * 1.7f),
                cornerRadius = CornerRadius(r * 0.48f, r * 0.48f)
            )
        }
    }

    // Specular Candy Gloss Highlight
    drawCircle(
        color = Color.White.copy(alpha = 0.52f),
        radius = r * 0.22f,
        center = Offset(cx - r * 0.28f, cy - r * 0.30f)
    )

    // Striped Special Frosting Bands
    when (cell.special) {
        CandySpecial.STRIPED_H -> {
            drawLine(
                color = Color.White.copy(alpha = 0.88f),
                start = Offset(cx - r * 0.72f, cy - r * 0.18f),
                end = Offset(cx + r * 0.72f, cy - r * 0.18f),
                strokeWidth = r * 0.15f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = 0.88f),
                start = Offset(cx - r * 0.72f, cy + r * 0.18f),
                end = Offset(cx + r * 0.72f, cy + r * 0.18f),
                strokeWidth = r * 0.15f,
                cap = StrokeCap.Round
            )
        }
        CandySpecial.STRIPED_V -> {
            drawLine(
                color = Color.White.copy(alpha = 0.88f),
                start = Offset(cx - r * 0.18f, cy - r * 0.72f),
                end = Offset(cx - r * 0.18f, cy + r * 0.72f),
                strokeWidth = r * 0.15f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = 0.88f),
                start = Offset(cx + r * 0.18f, cy - r * 0.72f),
                end = Offset(cx + r * 0.18f, cy + r * 0.72f),
                strokeWidth = r * 0.15f,
                cap = StrokeCap.Round
            )
        }
        else -> Unit
    }
}
