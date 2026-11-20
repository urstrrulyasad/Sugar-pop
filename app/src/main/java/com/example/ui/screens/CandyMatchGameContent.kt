package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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

private val CandyBoardDark = Color(0xFF28114E)
private val CandyGold = Color(0xFFFFD54F)
private val CandyPink = Color(0xFFFF4F8B)
private val CandyCyan = Color(0xFF40E0D0)

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

@Composable
fun CandyMatchGameContent(
    initialLevel: Int,
    highScore: Int,
    restartSignal: Int,
    onSaveProgress: (score: Int, level: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var currentLevel by remember(initialLevel) { mutableIntStateOf(initialLevel.coerceAtLeast(1)) }
    val levelConfig = remember(currentLevel) { CandyGameEngine.levelConfig(currentLevel) }

    var board by remember { mutableStateOf(CandyGameEngine.createInitialBoard()) }
    var score by remember { mutableIntStateOf(0) }
    val animatedScore by animateIntAsState(
        targetValue = score,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "match3Score"
    )
    var movesLeft by remember(levelConfig) { mutableIntStateOf(levelConfig.maxMoves) }
    var hammersLeft by remember(levelConfig) { mutableIntStateOf(3) }
    var isHammerArmed by remember { mutableStateOf(false) }

    var selectedPos by remember { mutableStateOf<GridPos?>(null) }
    var clearingCells by remember { mutableStateOf<Set<GridPos>>(emptySet()) }
    var isAnimating by remember { mutableStateOf(false) }
    var comboBanner by remember { mutableStateOf<String?>(null) }

    val popBursts = remember { mutableStateListOf<PopBurstEffect>() }
    val floatingScores = remember { mutableStateListOf<FloatingScoreEffect>() }
    var effectSeq by remember { mutableLongStateOf(1L) }
    val boardShakeX = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "match3Ambient")
    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )
    val selectionPulse by infiniteTransition.animateFloat(
        initialValue = 1.04f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    val levelWon = score >= levelConfig.targetScore && !isAnimating
    val levelLost = movesLeft <= 0 && score < levelConfig.targetScore && !isAnimating

    fun startLevel(targetLevel: Int) {
        currentLevel = targetLevel.coerceAtLeast(1)
        val cfg = CandyGameEngine.levelConfig(currentLevel)
        board = CandyGameEngine.createInitialBoard()
        score = 0
        movesLeft = cfg.maxMoves
        hammersLeft = 3
        isHammerArmed = false
        selectedPos = null
        clearingCells = emptySet()
        popBursts.clear()
        floatingScores.clear()
        comboBanner = null
    }

    LaunchedEffect(restartSignal) {
        if (restartSignal > 0) startLevel(currentLevel)
    }

    LaunchedEffect(comboBanner) {
        if (comboBanner != null) {
            delay(950L)
            comboBanner = null
        }
    }

    fun triggerBoardShake(magnitude: Float = 9f) {
        scope.launch {
            for (x in listOf(-magnitude, magnitude, -magnitude * 0.5f, 0f)) {
                boardShakeX.animateTo(x, spring(stiffness = Spring.StiffnessHigh))
            }
        }
    }

    fun spawnPopEffects(currentBoard: List<List<CandyCell>>, cleared: Set<GridPos>, pointsEarned: Int) {
        if (cleared.isEmpty()) return
        val bursts = cleared.take(24).map { pos ->
            PopBurstEffect(effectSeq++, pos.row, pos.col, candyPrimaryColor(currentBoard[pos.row][pos.col].type))
        }
        popBursts.addAll(bursts)
        val scoreFx = FloatingScoreEffect(
            id = effectSeq++,
            row = cleared.map { it.row }.average().toFloat(),
            col = cleared.map { it.col }.average().toFloat(),
            points = pointsEarned
        )
        floatingScores.add(scoreFx)
        scope.launch {
            delay(520L)
            popBursts.removeAll(bursts.toSet())
            delay(250L)
            floatingScores.remove(scoreFx)
        }
    }

    fun attemptMove(a: GridPos, b: GridPos) {
        if (isAnimating || levelWon || levelLost || movesLeft <= 0) return
        if (!CandyGameEngine.areAdjacent(a, b)) return

        scope.launch {
            isAnimating = true
            selectedPos = null
            var working = CandyGameEngine.swap(board, a, b)
            board = working
            delay(200L)

            if (CandyGameEngine.isColorBombSwap(board, a, b)) {
                movesLeft--
                val step = CandyGameEngine.resolveColorBombSwap(working, a, b)
                clearingCells = step.clearedPositions
                score += step.pointsEarned
                comboBanner = "COLOR BLAST!"
                triggerBoardShake(11f)
                spawnPopEffects(working, step.clearedPositions, step.pointsEarned)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(240L)
                clearingCells = emptySet()
                working = step.nextBoardAfterClearAndDrop
                board = working
                delay(240L)
            } else {
                val first = CandyGameEngine.resolveMatches(working, a, b, comboMultiplier = 1)
                if (first == null) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    board = CandyGameEngine.swap(working, a, b)
                    delay(180L)
                    isAnimating = false
                    return@launch
                }
                movesLeft--
                clearingCells = first.clearedPositions
                score += first.pointsEarned
                spawnPopEffects(working, first.clearedPositions, first.pointsEarned)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(220L)
                clearingCells = emptySet()
                working = first.nextBoardAfterClearAndDrop
                board = working
                delay(230L)
            }

            var combo = 2
            while (true) {
                val cascade = CandyGameEngine.resolveMatches(working, comboMultiplier = combo) ?: break
                clearingCells = cascade.clearedPositions
                score += cascade.pointsEarned
                comboBanner = when {
                    combo >= 4 -> "SUGAR RUSH ×$combo!"
                    combo == 3 -> "DELICIOUS ×3!"
                    else -> "SWEET COMBO ×2!"
                }
                triggerBoardShake(6f + combo * 2f)
                spawnPopEffects(working, cascade.clearedPositions, cascade.pointsEarned)
                delay(210L)
                clearingCells = emptySet()
                working = cascade.nextBoardAfterClearAndDrop
                board = working
                delay(230L)
                combo++
            }

            if (!CandyGameEngine.hasValidMove(working)) {
                comboBanner = "SHUFFLING..."
                board = CandyGameEngine.shuffleBoard(working)
            }

            if (score >= levelConfig.targetScore) {
                onSaveProgress(score, currentLevel + 1)
            } else {
                onSaveProgress(score, currentLevel)
            }
            isAnimating = false
        }
    }

    fun handleTap(pos: GridPos) {
        if (isAnimating || levelWon || levelLost) return
        if (isHammerArmed && hammersLeft > 0) {
            scope.launch {
                isAnimating = true
                isHammerArmed = false
                hammersLeft--
                val step = CandyGameEngine.smashSingleCell(board, pos)
                clearingCells = step.clearedPositions
                score += step.pointsEarned
                triggerBoardShake(9f)
                spawnPopEffects(board, step.clearedPositions, step.pointsEarned)
                delay(220L)
                clearingCells = emptySet()
                var working = step.nextBoardAfterClearAndDrop
                board = working
                delay(230L)
                var combo = 2
                while (true) {
                    val cascade = CandyGameEngine.resolveMatches(working, comboMultiplier = combo) ?: break
                    clearingCells = cascade.clearedPositions
                    score += cascade.pointsEarned
                    spawnPopEffects(working, cascade.clearedPositions, cascade.pointsEarned)
                    delay(210L)
                    clearingCells = emptySet()
                    working = cascade.nextBoardAfterClearAndDrop
                    board = working
                    delay(230L)
                    combo++
                }
                onSaveProgress(score, if (score >= levelConfig.targetScore) currentLevel + 1 else currentLevel)
                isAnimating = false
            }
            return
        }

        val current = selectedPos
        if (current == null) {
            selectedPos = pos
        } else if (current == pos) {
            selectedPos = null
        } else if (CandyGameEngine.areAdjacent(current, pos)) {
            attemptMove(current, pos)
        } else {
            selectedPos = pos
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 540.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Level, Score & Moves HUD
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.09f),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "LEVEL ${levelConfig.level} · ${levelConfig.worldName}",
                            style = MaterialTheme.typography.labelLarge,
                            color = CandyGold
                        )
                        Text(
                            text = "Best: ${maxOf(highScore, animatedScore)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (movesLeft <= 5) Color(0xFFD50000).copy(alpha = 0.45f) else CandyPink.copy(alpha = 0.25f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "$movesLeft",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text("MOVES", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                val progress = (animatedScore.toFloat() / levelConfig.targetScore.toFloat()).coerceIn(0f, 1f)
                val animatedProgress by animateFloatAsState(progress, tween(340), label = "match3Prog")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "$animatedScore / ${levelConfig.targetScore}",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        color = CandyGold,
                        trackColor = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                    )
                }
            }
        }

        // Combo banner
        Box(modifier = Modifier.height(26.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(
                visible = comboBanner != null,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Surface(color = CandyGold, shape = RoundedCornerShape(50)) {
                    Text(
                        text = comboBanner ?: "",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF28114E),
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // 8x8 Animated Match-3 Board
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer { translationX = boardShakeX.value }
                .clip(RoundedCornerShape(22.dp))
                .background(CandyBoardDark)
                .border(2.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
                .padding(6.dp)
                .testTag("candy_board_grid")
        ) {
            val tileSize = maxWidth / CandyGameEngine.BOARD_SIZE

            // Background checkerboard
            Column(modifier = Modifier.fillMaxSize()) {
                for (r in 0 until CandyGameEngine.BOARD_SIZE) {
                    Row(modifier = Modifier.weight(1f)) {
                        for (c in 0 until CandyGameEngine.BOARD_SIZE) {
                            val isSel = selectedPos == GridPos(r, c)
                            val tint = if ((r + c) % 2 == 0) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.12f)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .padding(1.5.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) CandyGold.copy(alpha = 0.26f) else tint)
                                    .then(if (isSel) Modifier.border(2.dp, CandyGold, RoundedCornerShape(8.dp)) else Modifier)
                            )
                        }
                    }
                }
            }

            // Keyed Animated Candy Sprites
            val flatEntries = remember(board) {
                buildList {
                    for (r in 0 until CandyGameEngine.BOARD_SIZE) {
                        for (c in 0 until CandyGameEngine.BOARD_SIZE) {
                            add(Triple(r, c, board[r][c]))
                        }
                    }
                }
            }
            for ((r, c, cell) in flatEntries) {
                key(cell.id) {
                    val animRow = remember { Animatable((r - 2f).coerceAtLeast(-2f)) }
                    val animCol = remember { Animatable(c.toFloat()) }
                    LaunchedEffect(r, c) {
                        launch { animCol.animateTo(c.toFloat(), spring(0.72f, Spring.StiffnessMediumLow)) }
                        launch { animRow.animateTo(r.toFloat(), spring(0.68f, Spring.StiffnessMediumLow)) }
                    }
                    val isSel = selectedPos == GridPos(r, c)
                    val isClearing = GridPos(r, c) in clearingCells
                    val scale by animateFloatAsState(
                        targetValue = when {
                            isClearing -> 0.1f
                            isSel -> selectionPulse
                            else -> 1f
                        },
                        animationSpec = spring(0.55f, Spring.StiffnessMedium),
                        label = "candyScale"
                    )
                    Canvas(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (animCol.value * tileSize.toPx()).roundToInt(),
                                    (animRow.value * tileSize.toPx()).roundToInt()
                                )
                            }
                            .size(tileSize)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                    ) {
                        drawGlossyCandy(cell, shimmerPhase)
                    }
                }
            }

            // Pop Bursts & Floating Scores
            for (burst in popBursts) {
                key(burst.id) {
                    val prog = remember { Animatable(0f) }
                    LaunchedEffect(burst.id) { prog.animateTo(1f, tween(480)) }
                    Canvas(
                        modifier = Modifier
                            .offset(x = tileSize * burst.col, y = tileSize * burst.row)
                            .size(tileSize)
                    ) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val alpha = (1f - prog.value).coerceIn(0f, 1f)
                        val r = size.minDimension * 0.55f * prog.value
                        for (i in 0 until 6) {
                            val a = (i * 60f) * (PI / 180.0)
                            drawCircle(
                                color = burst.color.copy(alpha = alpha),
                                radius = size.minDimension * 0.1f * alpha,
                                center = Offset(center.x + (cos(a) * r).toFloat(), center.y + (sin(a) * r).toFloat())
                            )
                        }
                    }
                }
            }

            for (fx in floatingScores) {
                key(fx.id) {
                    val rise = remember { Animatable(0f) }
                    LaunchedEffect(fx.id) { rise.animateTo(1f, tween(650)) }
                    Text(
                        text = "+${fx.points}",
                        color = CandyGold.copy(alpha = (1f - rise.value * 0.8f).coerceIn(0f, 1f)),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.offset {
                            IntOffset(
                                ((fx.col + 0.15f) * tileSize.toPx()).roundToInt(),
                                ((fx.row - rise.value * 0.9f) * tileSize.toPx()).roundToInt()
                            )
                        }
                    )
                }
            }

            // Static Input Grid
            Column(modifier = Modifier.fillMaxSize()) {
                for (r in 0 until CandyGameEngine.BOARD_SIZE) {
                    Row(modifier = Modifier.weight(1f)) {
                        for (c in 0 until CandyGameEngine.BOARD_SIZE) {
                            val pos = GridPos(r, c)
                            var dragAcc by remember { mutableStateOf(Offset.Zero) }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .pointerInput(r, c, isAnimating) {
                                        detectDragGestures(
                                            onDragStart = { dragAcc = Offset.Zero },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragAcc += amount
                                                val thresh = 26f
                                                if (abs(dragAcc.x) > thresh || abs(dragAcc.y) > thresh) {
                                                    val next = if (abs(dragAcc.x) > abs(dragAcc.y)) {
                                                        GridPos(r, (c + if (dragAcc.x > 0) 1 else -1).coerceIn(0, 7))
                                                    } else {
                                                        GridPos((r + if (dragAcc.y > 0) 1 else -1).coerceIn(0, 7), c)
                                                    }
                                                    dragAcc = Offset.Zero
                                                    attemptMove(pos, next)
                                                }
                                            }
                                        )
                                    }
                                    .clickable { handleTap(pos) }
                                    .testTag("candy_cell_${r}_$c")
                            )
                        }
                    }
                }
            }

            // Win / Loss Overlay
            if (levelWon || levelLost) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EmojiEvents,
                            contentDescription = null,
                            tint = CandyGold,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = if (levelWon) "Level ${levelConfig.level} Clear!" else "Out of Moves!",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Score: $score",
                            style = MaterialTheme.typography.titleMedium,
                            color = CandyGold
                        )
                        Button(
                            onClick = { startLevel(if (levelWon) currentLevel + 1 else currentLevel) },
                            colors = ButtonDefaults.buttonColors(containerColor = CandyPink),
                            modifier = Modifier.testTag("match3_continue_btn")
                        ) {
                            Text(if (levelWon) "Next Level" else "Retry Level", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Bottom Boosters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                onClick = { if (!isAnimating && hammersLeft > 0) isHammerArmed = !isHammerArmed },
                shape = RoundedCornerShape(16.dp),
                color = if (isHammerArmed) CandyGold else Color.White.copy(alpha = 0.12f),
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("candy_hammer_btn")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.AutoFixHigh,
                        contentDescription = null,
                        tint = if (isHammerArmed) Color(0xFF28114E) else CandyGold
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = if (isHammerArmed) "Tap Any Candy!" else "Hammer ($hammersLeft)",
                        color = if (isHammerArmed) Color(0xFF28114E) else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Surface(
                onClick = { if (!isAnimating) board = CandyGameEngine.shuffleBoard(board) },
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("candy_shuffle_btn")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = CandyCyan)
                    Spacer(Modifier.size(8.dp))
                    Text("Shuffle", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
