package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.CandyCell
import com.example.game.CandyGameEngine
import com.example.game.GridPos
import com.example.ui.components.candyPrimaryColor
import com.example.ui.components.drawGlossyCandy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val CandyBgTop = Color(0xFF28114E)
private val CandyBgMid = Color(0xFF3F186E)
private val CandyBgBottom = Color(0xFF1A0A35)
private val CandyPinkAccent = Color(0xFFFF5CA8)
private val CandyGoldAccent = Color(0xFFFFD15C)
private val CandyCyanAccent = Color(0xFF53E5FF)

private data class PopBurstEffect(
    val id: Long,
    val row: Int,
    val col: Int,
    val color: Color
)

private data class FloatingScoreEffect(
    val id: Long,
    val row: Float,
    val col: Float,
    val points: Int
)

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
    val density = LocalDensity.current

    var currentLevel by remember(initialLevel) { mutableIntStateOf(initialLevel.coerceAtLeast(1)) }
    val levelConfig = remember(currentLevel) { CandyGameEngine.levelConfig(currentLevel) }

    var board by remember { mutableStateOf(CandyGameEngine.createInitialBoard()) }
    var score by remember { mutableIntStateOf(0) }
    val animatedScore by animateIntAsState(
        targetValue = score,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "animatedScore"
    )
    var movesLeft by remember(levelConfig) { mutableIntStateOf(levelConfig.maxMoves) }
    var hammersLeft by remember(levelConfig) { mutableIntStateOf(3) }
    var isHammerArmed by remember { mutableStateOf(false) }

    var selectedPos by remember { mutableStateOf<GridPos?>(null) }
    var clearingCells by remember { mutableStateOf<Set<GridPos>>(emptySet()) }
    var isAnimating by remember { mutableStateOf(false) }
    var comboBanner by remember { mutableStateOf<String?>(null) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showSetupHintBanner by remember(hasPinConfigured) { mutableStateOf(!hasPinConfigured) }

    val popBursts = remember { mutableStateListOf<PopBurstEffect>() }
    val floatingScores = remember { mutableStateListOf<FloatingScoreEffect>() }
    var effectSeq by remember { mutableLongStateOf(1L) }
    val boardShakeX = remember { Animatable(0f) }

    // Ambient shimmer & selection pulse
    val infiniteTransition = rememberInfiniteTransition(label = "candyAmbient")
    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerPhase"
    )
    val selectionPulse by infiniteTransition.animateFloat(
        initialValue = 1.04f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "selectionPulse"
    )
    val ambientDrift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientDrift"
    )

    val levelWon = score >= levelConfig.targetScore && !isAnimating
    val levelLost = movesLeft <= 0 && score < levelConfig.targetScore && !isAnimating

    LaunchedEffect(comboBanner) {
        if (comboBanner != null) {
            delay(950L)
            comboBanner = null
        }
    }

    fun triggerBoardShake(magnitude: Float = 10f) {
        scope.launch {
            for (x in listOf(-magnitude, magnitude, -magnitude * 0.6f, magnitude * 0.6f, 0f)) {
                boardShakeX.animateTo(x, spring(stiffness = Spring.StiffnessHigh))
            }
        }
    }

    fun spawnPopEffects(
        currentBoard: List<List<CandyCell>>,
        cleared: Set<GridPos>,
        pointsEarned: Int
    ) {
        if (cleared.isEmpty()) return
        val newBursts = cleared.take(24).map { pos ->
            PopBurstEffect(
                id = effectSeq++,
                row = pos.row,
                col = pos.col,
                color = candyPrimaryColor(currentBoard[pos.row][pos.col].type)
            )
        }
        popBursts.addAll(newBursts)

        val avgRow = cleared.map { it.row }.average().toFloat()
        val avgCol = cleared.map { it.col }.average().toFloat()
        val scoreFx = FloatingScoreEffect(
            id = effectSeq++,
            row = avgRow,
            col = avgCol,
            points = pointsEarned
        )
        floatingScores.add(scoreFx)

        scope.launch {
            delay(550L)
            popBursts.removeAll(newBursts.toSet())
            delay(250L)
            floatingScores.remove(scoreFx)
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
        popBursts.clear()
        floatingScores.clear()
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
                spawnPopEffects(currentBoard, step.clearedPositions, step.pointsEarned)
                comboBanner = when {
                    combo == 2 -> "SWEET! ×2"
                    combo == 3 -> "TASTY! ×3"
                    combo >= 4 -> "SUGAR RUSH! ×$combo"
                    else -> null
                }
                if (combo >= 3) triggerBoardShake(8f)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                delay(240L)

                currentBoard = step.nextBoardAfterClearAndDrop
                board = currentBoard
                clearingCells = emptySet()
                score += step.pointsEarned
                onSaveProgress(score, currentLevel)
                combo++
                delay(260L)
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
                spawnPopEffects(board, step.clearedPositions, step.pointsEarned)
                triggerBoardShake(14f)
                comboBanner = "COLOR BLAST!"
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(280L)
                board = step.nextBoardAfterClearAndDrop
                clearingCells = emptySet()
                delay(240L)
                executeCascadeAfterInitial(step.nextBoardAfterClearAndDrop, step.pointsEarned)
                return@launch
            }

            val swapped = CandyGameEngine.swap(board, a, b)
            board = swapped
            delay(200L)

            val firstMatch = CandyGameEngine.resolveMatches(
                board = swapped,
                preferredPivotA = a,
                preferredPivotB = b,
                comboMultiplier = 1
            )
            if (firstMatch == null) {
                // Smooth bounce-back on invalid swap
                board = CandyGameEngine.swap(swapped, a, b)
                delay(210L)
                isAnimating = false
                return@launch
            }

            movesLeft = (movesLeft - 1).coerceAtLeast(0)
            clearingCells = firstMatch.clearedPositions
            spawnPopEffects(swapped, firstMatch.clearedPositions, firstMatch.pointsEarned)
            if (firstMatch.spawnedSpecials.isNotEmpty()) {
                comboBanner = "SPECIAL CANDY!"
            }
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            delay(240L)
            board = firstMatch.nextBoardAfterClearAndDrop
            clearingCells = emptySet()
            delay(250L)
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
                spawnPopEffects(board, step.clearedPositions, step.pointsEarned)
                triggerBoardShake(11f)
                comboBanner = "CRUSHED!"
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(240L)
                board = step.nextBoardAfterClearAndDrop
                clearingCells = emptySet()
                delay(240L)
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
            .background(Brush.verticalGradient(listOf(CandyBgTop, CandyBgMid, CandyBgBottom)))
            .systemBarsPadding()
    ) {
        // Drifting glowing bokeh background
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = CandyPinkAccent.copy(alpha = 0.10f),
                radius = size.width * 0.36f,
                center = Offset(
                    size.width * (0.14f + 0.08f * ambientDrift),
                    size.height * (0.14f + 0.05f * ambientDrift)
                )
            )
            drawCircle(
                color = CandyCyanAccent.copy(alpha = 0.09f),
                radius = size.width * 0.30f,
                center = Offset(
                    size.width * (0.86f - 0.08f * ambientDrift),
                    size.height * (0.78f - 0.06f * ambientDrift)
                )
            )
            drawCircle(
                color = CandyGoldAccent.copy(alpha = 0.06f),
                radius = size.width * 0.22f,
                center = Offset(
                    size.width * (0.50f + 0.1f * sin(ambientDrift * PI).toFloat()),
                    size.height * 0.45f
                )
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

            // First-launch-only Vault Setup Notice
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

            // Level, Animated Score, Star Progress & Moves HUD
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
                                text = "$animatedScore / ${levelConfig.targetScore}",
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

                    val progressTarget = (score.toFloat() / levelConfig.targetScore.coerceAtLeast(1))
                        .coerceIn(0f, 1f)
                    val animatedProgress by animateFloatAsState(
                        targetValue = progressTarget,
                        animationSpec = tween(350),
                        label = "progressFill"
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            color = CandyGoldAccent,
                            trackColor = Color.White.copy(alpha = 0.16f),
                            modifier = Modifier
                                .weight(1f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(50))
                        )
                        repeat(3) { starIdx ->
                            val threshold = (starIdx + 1) / 3f
                            val earned = progressTarget >= threshold
                            val starScale by animateFloatAsState(
                                targetValue = if (earned) 1.2f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "starScale_$starIdx"
                            )
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = if (earned) CandyGoldAccent else Color.White.copy(alpha = 0.24f),
                                modifier = Modifier
                                    .size(18.dp)
                                    .scale(starScale)
                            )
                        }
                    }
                }
            }

            // 4-Layer Animated 8x8 Match-3 Board
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .offset { IntOffset(boardShakeX.value.roundToInt(), 0) },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1A0C36).copy(alpha = 0.90f),
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            2.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.34f),
                                    CandyPinkAccent.copy(alpha = 0.38f)
                                )
                            ),
                            RoundedCornerShape(24.dp)
                        )
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        val cellSizeDp = maxWidth / CandyGameEngine.BOARD_SIZE
                        val cellPx = with(density) { cellSizeDp.toPx() }

                        // Layer 1: Static Checkerboard Slots
                        Column(modifier = Modifier.fillMaxSize()) {
                            for (r in 0 until CandyGameEngine.BOARD_SIZE) {
                                Row(modifier = Modifier.weight(1f)) {
                                    for (c in 0 until CandyGameEngine.BOARD_SIZE) {
                                        val pos = GridPos(r, c)
                                        val isSelected = selectedPos == pos
                                        val isCheckerDark = (r + c) % 2 == 0
                                        Box(
                                            modifier = Modifier
                                                .size(cellSizeDp)
                                                .padding(2.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    when {
                                                        isSelected -> CandyGoldAccent.copy(alpha = 0.32f)
                                                        isCheckerDark -> Color.White.copy(alpha = 0.08f)
                                                        else -> Color.White.copy(alpha = 0.03f)
                                                    }
                                                )
                                                .then(
                                                    if (isSelected) {
                                                        Modifier.border(
                                                            1.8.dp,
                                                            CandyGoldAccent,
                                                            RoundedCornerShape(10.dp)
                                                        )
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
                                        )
                                    }
                                }
                            }
                        }

                        // Layer 2: Keyed Animated Candy Sprites (slides on swap, bounces on drop)
                        val positionedCells = remember(board) {
                            board.flatMapIndexed { r, rowList ->
                                rowList.mapIndexed { c, cell -> Triple(cell, r, c) }
                            }
                        }

                        positionedCells.forEach { (cell, r, c) ->
                            key(cell.id) {
                                val pos = GridPos(r, c)
                                val isSelected = selectedPos == pos
                                val isClearing = pos in clearingCells

                                val targetOffset = Offset(c * cellPx, r * cellPx)
                                val offsetAnim = remember {
                                    Animatable(
                                        Offset(c * cellPx, ((r - 2).coerceAtLeast(-2)) * cellPx),
                                        Offset.VectorConverter
                                    )
                                }

                                LaunchedEffect(r, c, cellPx) {
                                    offsetAnim.animateTo(
                                        targetValue = targetOffset,
                                        animationSpec = spring(
                                            dampingRatio = 0.68f,
                                            stiffness = 380f
                                        )
                                    )
                                }

                                val scale by animateFloatAsState(
                                    targetValue = when {
                                        isClearing -> 0.1f
                                        isSelected -> selectionPulse
                                        else -> 1f
                                    },
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "candyScale"
                                )
                                val alpha by animateFloatAsState(
                                    targetValue = if (isClearing) 0f else 1f,
                                    animationSpec = tween(190),
                                    label = "candyAlpha"
                                )

                                Box(
                                    modifier = Modifier
                                        .offset {
                                            IntOffset(
                                                offsetAnim.value.x.roundToInt(),
                                                offsetAnim.value.y.roundToInt()
                                            )
                                        }
                                        .size(cellSizeDp)
                                        .alpha(alpha),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxSize(0.80f)
                                            .scale(scale)
                                    ) {
                                        drawGlossyCandy(cell, shimmerPhase)
                                    }
                                }
                            }
                        }

                        // Layer 3: Sparkle Pop Particles & Floating Score Popups
                        popBursts.forEach { burst ->
                            key(burst.id) {
                                CandyBurstOverlay(
                                    burst = burst,
                                    cellSizePx = cellPx
                                )
                            }
                        }
                        floatingScores.forEach { fx ->
                            key(fx.id) {
                                FloatingScoreBadge(
                                    effect = fx,
                                    cellSizePx = cellPx
                                )
                            }
                        }

                        // Layer 4: Static Hit-Grid for Crisp Tap & Swipe Gestures
                        Column(modifier = Modifier.fillMaxSize()) {
                            for (r in 0 until CandyGameEngine.BOARD_SIZE) {
                                Row(modifier = Modifier.weight(1f)) {
                                    for (c in 0 until CandyGameEngine.BOARD_SIZE) {
                                        val pos = GridPos(r, c)
                                        Box(
                                            modifier = Modifier
                                                .size(cellSizeDp)
                                                .pointerInput(pos) {
                                                    detectTapGestures(onTap = { handleCellTap(pos) })
                                                }
                                                .pointerInput(pos) {
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
                                                            val threshold = 22f
                                                            if (abs(totalX) > threshold || abs(totalY) > threshold) {
                                                                triggered = true
                                                                if (abs(totalX) > abs(totalY)) {
                                                                    handleSwap(
                                                                        pos,
                                                                        GridPos(
                                                                            pos.row,
                                                                            pos.col + if (totalX > 0) 1 else -1
                                                                        )
                                                                    )
                                                                } else {
                                                                    handleSwap(
                                                                        pos,
                                                                        GridPos(
                                                                            pos.row + if (totalY > 0) 1 else -1,
                                                                            pos.col
                                                                        )
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    )
                                                }
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
                    enter = scaleIn(
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    Surface(
                        color = CandyGoldAccent,
                        shape = RoundedCornerShape(50),
                        shadowElevation = 10.dp
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
                    color = if (isHammerArmed) CandyGoldAccent else Color.White.copy(alpha = 0.13f),
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
private fun CandyBurstOverlay(
    burst: PopBurstEffect,
    cellSizePx: Float
) {
    val progress = remember { Animatable(0f) }
    val particleAngles = remember {
        List(7) { idx -> (idx * 51.4f + Random.nextFloat() * 18f) * (PI / 180.0) }
    }

    LaunchedEffect(burst.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
        )
    }

    val p = progress.value
    val alpha = (1f - p).coerceIn(0f, 1f)
    val cx = (burst.col + 0.5f) * cellSizePx
    val cy = (burst.row + 0.5f) * cellSizePx

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Expanding candy shockwave ring
        drawCircle(
            color = burst.color.copy(alpha = alpha * 0.7f),
            radius = cellSizePx * (0.28f + 0.68f * p),
            center = Offset(cx, cy),
            style = Stroke(width = (1f - p * 0.7f) * 5f)
        )
        // Radial sugar sparkles
        particleAngles.forEachIndexed { idx, angle ->
            val dist = cellSizePx * (0.18f + 0.85f * p)
            val px = cx + (dist * cos(angle)).toFloat()
            val py = cy + (dist * sin(angle)).toFloat()
            val dotColor = if (idx % 2 == 0) burst.color else Color.White
            drawCircle(
                color = dotColor.copy(alpha = alpha),
                radius = cellSizePx * 0.11f * (1f - p * 0.55f),
                center = Offset(px, py)
            )
        }
    }
}

@Composable
private fun FloatingScoreBadge(
    effect: FloatingScoreEffect,
    cellSizePx: Float
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(effect.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing)
        )
    }

    val p = progress.value
    val xPx = (effect.col * cellSizePx).roundToInt()
    val yPx = ((effect.row * cellSizePx) - (p * cellSizePx * 1.1f)).roundToInt()
    val alpha = if (p < 0.7f) 1f else ((1f - p) / 0.3f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .offset { IntOffset(xPx, yPx) }
            .alpha(alpha)
    ) {
        Text(
            text = "+${effect.points}",
            style = MaterialTheme.typography.titleMedium,
            color = CandyGoldAccent,
            fontWeight = FontWeight.ExtraBold
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
