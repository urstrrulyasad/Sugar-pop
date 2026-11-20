package com.example.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Shuffle
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.game.CandyCell
import com.example.game.CandyGameEngine
import com.example.game.CandySpecial
import com.example.game.GridPos
import com.example.ui.components.CandyRenderScratch
import com.example.ui.components.candyPrimaryColor
import com.example.ui.components.drawGlossyCandyAt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

private val CandyBoardDark = Color(0xFF28114E)
private val CandyGold = Color(0xFFFFD54F)
private val CandyPink = Color(0xFFFF4F8B)
private val CandyCyan = Color(0xFF40E0D0)

private class CandySpriteMotion(
    var row: Float,
    var col: Float,
    var targetRow: Float,
    var targetCol: Float,
    var velRow: Float = 0f,
    var velCol: Float = 0f,
    var scale: Float = 0.2f
)

private class CandyBurstParticle(
    var row: Float,
    var col: Float,
    var vRow: Float,
    var vCol: Float,
    var life: Float,
    val maxLife: Float,
    val color: Color,
    val radiusFactor: Float
)

private class CandyShockwave(
    val row: Float,
    val col: Float,
    val color: Color,
    var age: Float = 0f,
    val duration: Float = 0.42f
)

private class CandyBeamEffect(
    val isHorizontal: Boolean,
    val index: Int,
    val color: Color,
    var age: Float = 0f,
    val duration: Float = 0.38f
)

private class CandyFloatingScore(
    val text: String,
    var row: Float,
    val col: Float,
    var age: Float = 0f,
    val duration: Float = 0.78f
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

    val boardShakeX = remember { Animatable(0f) }

    // Zero-recomposition animation engine backing the single 8x8 board Canvas
    val spriteMotions = remember { HashMap<Long, CandySpriteMotion>(96) }
    val burstParticles = remember { ArrayList<CandyBurstParticle>(128) }
    val shockwaves = remember { ArrayList<CandyShockwave>(32) }
    val beams = remember { ArrayList<CandyBeamEffect>(12) }
    val floatingScores = remember { ArrayList<CandyFloatingScore>(16) }
    val candyScratch = remember { CandyRenderScratch() }
    val popupPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    var canvasTick by remember { mutableLongStateOf(0L) }
    var animClockSec by remember { mutableStateOf(0f) }

    val levelWon = score >= levelConfig.targetScore && !isAnimating
    val levelLost = movesLeft <= 0 && score < levelConfig.targetScore && !isAnimating

    // Sync target row/col whenever board changes
    LaunchedEffect(board) {
        val activeIds = HashSet<Long>(72)
        for (r in 0 until CandyGameEngine.BOARD_SIZE) {
            for (c in 0 until CandyGameEngine.BOARD_SIZE) {
                val cell = board[r][c]
                activeIds.add(cell.id)
                val existing = spriteMotions[cell.id]
                if (existing == null) {
                    spriteMotions[cell.id] = CandySpriteMotion(
                        row = -1.2f - (CandyGameEngine.BOARD_SIZE - r) * 0.28f,
                        col = c.toFloat(),
                        targetRow = r.toFloat(),
                        targetCol = c.toFloat(),
                        scale = 0.25f
                    )
                } else {
                    existing.targetRow = r.toFloat()
                    existing.targetCol = c.toFloat()
                }
            }
        }
        spriteMotions.keys.retainAll(activeIds)
    }

    // Single lightweight 60/120fps frame driver that updates Canvas coordinates without recomposing the tree
    LaunchedEffect(Unit) {
        var lastNs = 0L
        while (true) {
            withFrameNanos { frameNs ->
                if (lastNs == 0L) {
                    lastNs = frameNs
                    return@withFrameNanos
                }
                val dt = ((frameNs - lastNs) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
                lastNs = frameNs
                animClockSec += dt

                val springK = 240f
                val damping = 22f
                for ((_, m) in spriteMotions) {
                    val dr = m.targetRow - m.row
                    val dc = m.targetCol - m.col
                    if (abs(dr) > 0.002f || abs(m.velRow) > 0.01f) {
                        val accR = dr * springK - m.velRow * damping
                        m.velRow += accR * dt
                        m.row += m.velRow * dt
                    } else {
                        m.row = m.targetRow
                        m.velRow = 0f
                    }
                    if (abs(dc) > 0.002f || abs(m.velCol) > 0.01f) {
                        val accC = dc * springK - m.velCol * damping
                        m.velCol += accC * dt
                        m.col += m.velCol * dt
                    } else {
                        m.col = m.targetCol
                        m.velCol = 0f
                    }
                    // Smooth scale-in to 1.0f
                    val alphaScale = 1f - exp(-14f * dt)
                    m.scale += (1f - m.scale) * alphaScale
                }

                // Step particles
                var i = burstParticles.size - 1
                while (i >= 0) {
                    val p = burstParticles[i]
                    p.life -= dt
                    if (p.life <= 0f) {
                        burstParticles.removeAt(i)
                    } else {
                        p.row += p.vRow * dt
                        p.col += p.vCol * dt
                        p.vRow += 8.5f * dt
                    }
                    i--
                }

                // Step shockwaves
                var sIdx = shockwaves.size - 1
                while (sIdx >= 0) {
                    val sw = shockwaves[sIdx]
                    sw.age += dt
                    if (sw.age >= sw.duration) shockwaves.removeAt(sIdx)
                    sIdx--
                }

                // Step beams
                var bIdx = beams.size - 1
                while (bIdx >= 0) {
                    val b = beams[bIdx]
                    b.age += dt
                    if (b.age >= b.duration) beams.removeAt(bIdx)
                    bIdx--
                }

                // Step floating scores
                var fIdx = floatingScores.size - 1
                while (fIdx >= 0) {
                    val fx = floatingScores[fIdx]
                    fx.age += dt
                    fx.row -= dt * 1.45f
                    if (fx.age >= fx.duration) floatingScores.removeAt(fIdx)
                    fIdx--
                }

                canvasTick++
            }
        }
    }

    // Celebratory confetti shower on Level Clear
    LaunchedEffect(levelWon) {
        if (levelWon) {
            val confettiPalette = listOf(CandyGold, CandyPink, CandyCyan, Color(0xFF69F0AEL.toInt()), Color(0xFFB388FF))
            repeat(3) { wave ->
                for (k in 0 until 18) {
                    val col = (k / 18f) * CandyGameEngine.BOARD_SIZE
                    val angle = -PI / 2.0 + ((k % 5) - 2) * 0.35
                    val speed = 4.2f + (k % 3) * 1.6f
                    burstParticles.add(
                        CandyBurstParticle(
                            row = 7.4f - wave * 1.8f,
                            col = col,
                            vRow = (sin(angle) * speed).toFloat() - 3f,
                            vCol = (cos(angle) * speed).toFloat(),
                            life = 1.1f,
                            maxLife = 1.1f,
                            color = confettiPalette[k % confettiPalette.size],
                            radiusFactor = 0.14f
                        )
                    )
                }
                delay(240L)
            }
        }
    }

    fun startLevel(targetLevel: Int) {
        currentLevel = targetLevel.coerceAtLeast(1)
        val cfg = CandyGameEngine.levelConfig(currentLevel)
        spriteMotions.clear()
        burstParticles.clear()
        shockwaves.clear()
        beams.clear()
        floatingScores.clear()
        board = CandyGameEngine.createInitialBoard()
        score = 0
        movesLeft = cfg.maxMoves
        hammersLeft = if (cfg.isHardLevel) 2 else 3
        isHammerArmed = false
        selectedPos = null
        clearingCells = emptySet()
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

    fun triggerBoardShake(magnitude: Float = 8f) {
        scope.launch {
            for (x in listOf(-magnitude, magnitude, -magnitude * 0.45f, 0f)) {
                boardShakeX.animateTo(x, spring(stiffness = Spring.StiffnessHigh))
            }
        }
    }

    fun spawnPopEffects(currentBoard: List<List<CandyCell>>, cleared: Set<GridPos>, pointsEarned: Int) {
        if (cleared.isEmpty()) return
        for (pos in cleared.take(28)) {
            val cell = currentBoard[pos.row][pos.col]
            val colColor = candyPrimaryColor(cell.type)
            shockwaves.add(CandyShockwave(pos.row + 0.5f, pos.col + 0.5f, colColor))
            if (cell.special == CandySpecial.STRIPED_H) {
                beams.add(CandyBeamEffect(isHorizontal = true, index = pos.row, color = colColor))
            } else if (cell.special == CandySpecial.STRIPED_V) {
                beams.add(CandyBeamEffect(isHorizontal = false, index = pos.col, color = colColor))
            }
            for (k in 0 until 5) {
                val angle = (k * 72f + (pos.row * 17)) * (PI / 180.0)
                val spd = 2.6f + (k % 2) * 1.3f
                burstParticles.add(
                    CandyBurstParticle(
                        row = pos.row + 0.5f,
                        col = pos.col + 0.5f,
                        vRow = (sin(angle) * spd).toFloat() - 1.5f,
                        vCol = (cos(angle) * spd).toFloat(),
                        life = 0.48f,
                        maxLife = 0.48f,
                        color = if (k == 0) Color.White else colColor,
                        radiusFactor = 0.11f
                    )
                )
            }
        }
        val avgRow = cleared.map { it.row }.average().toFloat() + 0.4f
        val avgCol = cleared.map { it.col }.average().toFloat() + 0.5f
        floatingScores.add(CandyFloatingScore("+$pointsEarned", avgRow, avgCol))
    }

    fun attemptMove(a: GridPos, b: GridPos) {
        if (isAnimating || levelWon || levelLost || movesLeft <= 0) return
        if (!CandyGameEngine.areAdjacent(a, b)) return

        scope.launch {
            isAnimating = true
            selectedPos = null
            var working = CandyGameEngine.swap(board, a, b)
            board = working
            delay(175L)

            if (CandyGameEngine.isColorBombSwap(board, a, b)) {
                movesLeft--
                val step = CandyGameEngine.resolveColorBombSwap(working, a, b)
                clearingCells = step.clearedPositions
                score += step.pointsEarned
                comboBanner = "COLOR BLAST!"
                triggerBoardShake(10f)
                spawnPopEffects(working, step.clearedPositions, step.pointsEarned)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(210L)
                clearingCells = emptySet()
                working = step.nextBoardAfterClearAndDrop
                board = working
                delay(210L)
            } else {
                val first = CandyGameEngine.resolveMatches(working, a, b, comboMultiplier = 1)
                if (first == null) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    board = CandyGameEngine.swap(working, a, b)
                    delay(165L)
                    isAnimating = false
                    return@launch
                }
                movesLeft--
                clearingCells = first.clearedPositions
                score += first.pointsEarned
                spawnPopEffects(working, first.clearedPositions, first.pointsEarned)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(190L)
                clearingCells = emptySet()
                working = first.nextBoardAfterClearAndDrop
                board = working
                delay(205L)
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
                triggerBoardShake((5f + combo * 1.8f).coerceAtMost(12f))
                spawnPopEffects(working, cascade.clearedPositions, cascade.pointsEarned)
                delay(185L)
                clearingCells = emptySet()
                working = cascade.nextBoardAfterClearAndDrop
                board = working
                delay(205L)
                combo++
            }

            if (!CandyGameEngine.hasValidMove(working)) {
                comboBanner = "SHUFFLING..."
                board = CandyGameEngine.shuffleBoard(working)
            }

            onSaveProgress(score, if (score >= levelConfig.targetScore) currentLevel + 1 else currentLevel)
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
                delay(195L)
                clearingCells = emptySet()
                var working = step.nextBoardAfterClearAndDrop
                board = working
                delay(205L)
                var combo = 2
                while (true) {
                    val cascade = CandyGameEngine.resolveMatches(working, comboMultiplier = combo) ?: break
                    clearingCells = cascade.clearedPositions
                    score += cascade.pointsEarned
                    spawnPopEffects(working, cascade.clearedPositions, cascade.pointsEarned)
                    delay(185L)
                    clearingCells = emptySet()
                    working = cascade.nextBoardAfterClearAndDrop
                    board = working
                    delay(205L)
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
            color = if (levelConfig.isHardLevel) Color(0xFF3B1228).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.09f),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = if (levelConfig.isHardLevel) 1.5.dp else 1.dp,
                    color = if (levelConfig.isHardLevel) Color(0xFFFF5252).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(18.dp)
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "LEVEL ${levelConfig.level} · ${levelConfig.worldName}",
                                style = MaterialTheme.typography.labelLarge,
                                color = CandyGold
                            )
                            if (levelConfig.isHardLevel) {
                                Surface(
                                    color = Color(0xFFD50000),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.testTag("match3_hard_badge")
                                ) {
                                    Text(
                                        text = "HARD",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
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
                        color = if (levelConfig.isHardLevel) Color(0xFFFF6D00) else CandyGold,
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
        Box(modifier = Modifier.height(28.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(
                visible = comboBanner != null,
                enter = scaleIn(spring(0.55f, Spring.StiffnessMedium)) + fadeIn(),
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

        // Single High-Performance 8x8 Animated Canvas Board
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
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isAnimating, levelWon, levelLost) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tilePx = size.width.toFloat() / CandyGameEngine.BOARD_SIZE
                            val startCol = floor(down.position.x / tilePx).toInt().coerceIn(0, 7)
                            val startRow = floor(down.position.y / tilePx).toInt().coerceIn(0, 7)
                            val startPos = GridPos(startRow, startCol)
                            var swiped = false
                            val threshold = (tilePx * 0.34f).coerceAtLeast(18f)

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (!swiped) handleTap(startPos)
                                    break
                                }
                                if (!swiped) {
                                    val dx = change.position.x - down.position.x
                                    val dy = change.position.y - down.position.y
                                    if (abs(dx) > threshold || abs(dy) > threshold) {
                                        swiped = true
                                        change.consume()
                                        val nextPos = if (abs(dx) > abs(dy)) {
                                            GridPos(startRow, (startCol + if (dx > 0) 1 else -1).coerceIn(0, 7))
                                        } else {
                                            GridPos((startRow + if (dy > 0) 1 else -1).coerceIn(0, 7), startCol)
                                        }
                                        attemptMove(startPos, nextPos)
                                    }
                                }
                            }
                        }
                    }
            ) {
                // Read canvasTick once inside onDraw so ONLY Canvas redraws at 60/120fps
                val frame = canvasTick
                val tilePx = size.width / CandyGameEngine.BOARD_SIZE
                val shimmerPhase = (animClockSec * 0.45f) % 1f
                val pulseScale = 1.07f + 0.06f * sin(animClockSec * 6f)

                // 1. Checkerboard background
                val tileInset = tilePx * 0.05f
                val cellCorner = CornerRadius(tilePx * 0.16f)
                for (r in 0 until CandyGameEngine.BOARD_SIZE) {
                    for (c in 0 until CandyGameEngine.BOARD_SIZE) {
                        val isSel = selectedPos == GridPos(r, c)
                        val bg = when {
                            isSel -> CandyGold.copy(alpha = 0.28f)
                            (r + c) % 2 == 0 -> Color.White.copy(alpha = 0.06f)
                            else -> Color.White.copy(alpha = 0.12f)
                        }
                        drawRoundRect(
                            color = bg,
                            topLeft = Offset(c * tilePx + tileInset, r * tilePx + tileInset),
                            size = Size(tilePx - tileInset * 2f, tilePx - tileInset * 2f),
                            cornerRadius = cellCorner
                        )
                        if (isSel) {
                            drawRoundRect(
                                color = CandyGold,
                                topLeft = Offset(c * tilePx + tileInset, r * tilePx + tileInset),
                                size = Size(tilePx - tileInset * 2f, tilePx - tileInset * 2f),
                                cornerRadius = cellCorner,
                                style = Stroke(width = 3f)
                            )
                        }
                    }
                }

                // 2. Striped candy laser beams
                for (b in beams) {
                    val t = (b.age / b.duration).coerceIn(0f, 1f)
                    val alpha = (1f - t) * 0.85f
                    val thickness = tilePx * (0.45f * (1f - t) + 0.12f)
                    if (b.isHorizontal) {
                        val cy = (b.index + 0.5f) * tilePx
                        drawLine(
                            brush = Brush.horizontalGradient(listOf(Color.Transparent, b.color.copy(alpha = alpha), Color.White.copy(alpha = alpha), b.color.copy(alpha = alpha), Color.Transparent)),
                            start = Offset(0f, cy),
                            end = Offset(size.width, cy),
                            strokeWidth = thickness,
                            cap = StrokeCap.Round
                        )
                    } else {
                        val cx = (b.index + 0.5f) * tilePx
                        drawLine(
                            brush = Brush.verticalGradient(listOf(Color.Transparent, b.color.copy(alpha = alpha), Color.White.copy(alpha = alpha), b.color.copy(alpha = alpha), Color.Transparent)),
                            start = Offset(cx, 0f),
                            end = Offset(cx, size.height),
                            strokeWidth = thickness,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // 3. Candies (all 64 drawn in one fast pass)
                for (r in 0 until CandyGameEngine.BOARD_SIZE) {
                    for (c in 0 until CandyGameEngine.BOARD_SIZE) {
                        val cell = board[r][c]
                        val motion = spriteMotions[cell.id]
                        val drawRow = motion?.row ?: r.toFloat()
                        val drawCol = motion?.col ?: c.toFloat()
                        val baseScale = motion?.scale ?: 1f
                        val isSel = selectedPos == GridPos(r, c)
                        val isClearing = GridPos(r, c) in clearingCells
                        val finalScale = when {
                            isClearing -> 0.12f
                            isSel -> baseScale * pulseScale
                            else -> baseScale
                        }
                        drawGlossyCandyAt(
                            cell = cell,
                            centerX = (drawCol + 0.5f) * tilePx,
                            centerY = (drawRow + 0.5f) * tilePx,
                            tilePx = tilePx,
                            scale = finalScale,
                            shimmerPhase = shimmerPhase,
                            isSelected = isSel,
                            scratch = candyScratch
                        )
                    }
                }

                // 4. Expanding Shockwaves
                for (sw in shockwaves) {
                    val t = (sw.age / sw.duration).coerceIn(0f, 1f)
                    val alpha = (1f - t) * 0.75f
                    drawCircle(
                        color = sw.color.copy(alpha = alpha),
                        radius = tilePx * (0.25f + 0.85f * t),
                        center = Offset(sw.col * tilePx, sw.row * tilePx),
                        style = Stroke(width = (tilePx * 0.1f * (1f - t)).coerceAtLeast(1.5f))
                    )
                }

                // 5. Confetti & Sparkle Burst Particles
                for (p in burstParticles) {
                    val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
                    drawCircle(
                        color = p.color.copy(alpha = alpha),
                        radius = tilePx * p.radiusFactor * (0.4f + 0.6f * alpha),
                        center = Offset(p.col * tilePx, p.row * tilePx)
                    )
                }

                // 6. Floating Score Popups
                if (floatingScores.isNotEmpty()) {
                    popupPaint.textSize = tilePx * 0.42f
                    val nc = drawContext.canvas.nativeCanvas
                    for (fx in floatingScores) {
                        val t = (fx.age / fx.duration).coerceIn(0f, 1f)
                        val alpha = ((1f - t * 0.9f) * 255).toInt().coerceIn(0, 255)
                        val x = (fx.col * tilePx).coerceIn(tilePx, size.width - tilePx)
                        val y = (fx.row * tilePx).coerceIn(tilePx * 0.6f, size.height - tilePx * 0.4f)
                        popupPaint.color = android.graphics.Color.argb(alpha, 20, 8, 40)
                        nc.drawText(fx.text, x + 2f, y + 2f, popupPaint)
                        popupPaint.color = CandyGold.copy(alpha = alpha / 255f).toArgb()
                        nc.drawText(fx.text, x, y, popupPaint)
                    }
                }
                if (frame < 0L) Unit
            }

            // Win / Loss Overlay with spring pop-in
            androidx.compose.animation.AnimatedVisibility(
                visible = levelWon || levelLost,
                enter = fadeIn(tween(220)) + scaleIn(spring(0.68f, Spring.StiffnessMediumLow), initialScale = 0.84f),
                exit = fadeOut(tween(160))
            ) {
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
                            text = when {
                                levelWon && levelConfig.isHardLevel -> "HARD LEVEL ${levelConfig.level} CLEAR!"
                                levelWon -> "Level ${levelConfig.level} Clear!"
                                else -> "Out of Moves!"
                            },
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
                    .height(52.dp)
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
                    .height(52.dp)
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
