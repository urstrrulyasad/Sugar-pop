package com.example.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.game.CandyCell
import com.example.game.CandySpecial
import com.example.game.CandyType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

fun candyPrimaryColor(type: CandyType): Color = when (type) {
    CandyType.STRAWBERRY -> Color(0xFFFF3D71)
    CandyType.LEMON -> Color(0xFFFFD54F)
    CandyType.BLUEBERRY -> Color(0xFF29B6F6)
    CandyType.GRAPE -> Color(0xFFB388FF)
    CandyType.MINT -> Color(0xFF00E676)
    CandyType.ORANGE -> Color(0xFFFF8F00)
}

fun candySecondaryColor(type: CandyType): Color = when (type) {
    CandyType.STRAWBERRY -> Color(0xFFB0003A)
    CandyType.LEMON -> Color(0xFFF57F17)
    CandyType.BLUEBERRY -> Color(0xFF01579B)
    CandyType.GRAPE -> Color(0xFF512DA8)
    CandyType.MINT -> Color(0xFF00796B)
    CandyType.ORANGE -> Color(0xFFD84315)
}

private val ColorBombDotColors = listOf(
    Color(0xFFFF3D71),
    Color(0xFF29B6F6),
    Color(0xFF00E676),
    Color(0xFFFFD54F),
    Color(0xFFB388FF),
    Color(0xFFFF8F00)
)

/**
 * Reusable scratch buffers for zero-allocation 60/120fps candy drawing inside a single board Canvas.
 */
class CandyRenderScratch {
    val diamondPath = Path()
    val hexPath = Path()
    val starPath = Path()
    private val brushStops = Array(CandyType.entries.size) { typeIndex ->
        val t = CandyType.entries[typeIndex]
        listOf(Color.White.copy(alpha = 0.72f), candyPrimaryColor(t), candySecondaryColor(t))
    }
    private val bombColors = listOf(Color(0xFFFFD54F), Color(0xFF5D2906), Color(0xFF2E1202))

    fun fillBrushFor(type: CandyType, cx: Float, cy: Float, radius: Float): Brush {
        return Brush.radialGradient(
            colors = brushStops[type.ordinal],
            center = Offset(cx - radius * 0.25f, cy - radius * 0.25f),
            radius = (radius * 1.45f).coerceAtLeast(1f)
        )
    }

    fun bombBrushFor(cx: Float, cy: Float, radius: Float): Brush {
        return Brush.radialGradient(
            colors = bombColors,
            center = Offset(cx, cy),
            radius = (radius * 1.1f).coerceAtLeast(1f)
        )
    }
}

fun DrawScope.drawGlossyCandyAt(
    cell: CandyCell,
    centerX: Float,
    centerY: Float,
    tilePx: Float,
    scale: Float,
    shimmerPhase: Float,
    isSelected: Boolean,
    scratch: CandyRenderScratch
) {
    if (scale <= 0.02f) return
    val baseRadius = tilePx * 0.40f
    val r = baseRadius * scale

    if (isSelected) {
        drawCircle(
            color = Color(0xFFFFD54F).copy(alpha = 0.28f),
            radius = r * 1.24f,
            center = Offset(centerX, centerY)
        )
        drawCircle(
            color = Color(0xFFFFD54F).copy(alpha = 0.75f),
            radius = r * 1.15f,
            center = Offset(centerX, centerY),
            style = Stroke(width = (tilePx * 0.055f).coerceAtLeast(2f))
        )
    }

    if (cell.special == CandySpecial.COLOR_BOMB) {
        val center = Offset(centerX, centerY)
        // Pulsing rainbow halo around Color Bomb
        drawCircle(
            color = Color(0xFFFFD54F).copy(alpha = 0.24f + 0.14f * sin(shimmerPhase * 2f * PI).toFloat()),
            radius = r * 1.22f,
            center = center
        )
        drawCircle(
            brush = scratch.bombBrushFor(centerX, centerY, r),
            radius = r,
            center = center
        )
        for (i in 0 until 6) {
            val angle = ((i * 60f) + shimmerPhase * 360f) * (PI / 180.0)
            val dx = (cos(angle) * r * 0.56f).toFloat()
            val dy = (sin(angle) * r * 0.56f).toFloat()
            drawCircle(ColorBombDotColors[i], r * 0.16f, Offset(centerX + dx, centerY + dy))
        }
        drawCircle(Color.White.copy(alpha = 0.78f), r * 0.18f, center)
        return
    }

    val center = Offset(centerX, centerY)
    val fillBrush = scratch.fillBrushFor(cell.type, centerX, centerY, r)

    // Soft drop shadow
    drawCircle(
        color = Color.Black.copy(alpha = 0.26f),
        radius = r * 0.96f,
        center = Offset(centerX, centerY + r * 0.14f)
    )

    when (cell.type) {
        CandyType.STRAWBERRY -> {
            drawCircle(brush = fillBrush, radius = r, center = center)
        }
        CandyType.LEMON -> {
            drawRoundRect(
                brush = fillBrush,
                topLeft = Offset(centerX - r * 0.9f, centerY - r * 0.9f),
                size = Size(r * 1.8f, r * 1.8f),
                cornerRadius = CornerRadius(r * 0.45f)
            )
        }
        CandyType.BLUEBERRY -> {
            val diamond = scratch.diamondPath
            diamond.reset()
            diamond.moveTo(centerX, centerY - r)
            diamond.lineTo(centerX + r, centerY)
            diamond.lineTo(centerX, centerY + r)
            diamond.lineTo(centerX - r, centerY)
            diamond.close()
            drawPath(diamond, brush = fillBrush)
            drawPath(diamond, Color.White.copy(alpha = 0.42f), style = Stroke(width = 2.5f))
        }
        CandyType.GRAPE -> {
            val hex = scratch.hexPath
            hex.reset()
            for (i in 0 until 6) {
                val a = (i * 60f - 30f) * (PI / 180.0)
                val px = centerX + (cos(a) * r).toFloat()
                val py = centerY + (sin(a) * r).toFloat()
                if (i == 0) hex.moveTo(px, py) else hex.lineTo(px, py)
            }
            hex.close()
            drawPath(hex, brush = fillBrush)
        }
        CandyType.MINT -> {
            val pw = r * 1.9f
            val ph = r * 1.35f
            drawRoundRect(
                brush = fillBrush,
                topLeft = Offset(centerX - pw / 2f, centerY - ph / 2f),
                size = Size(pw, ph),
                cornerRadius = CornerRadius(ph / 2f)
            )
        }
        CandyType.ORANGE -> {
            val star = scratch.starPath
            star.reset()
            for (i in 0 until 10) {
                val sr = if (i % 2 == 0) r * 1.05f else r * 0.56f
                val a = (i * 36f - 90f) * (PI / 180.0)
                val px = centerX + (cos(a) * sr).toFloat()
                val py = centerY + (sin(a) * sr).toFloat()
                if (i == 0) star.moveTo(px, py) else star.lineTo(px, py)
            }
            star.close()
            drawPath(star, brush = fillBrush)
        }
    }

    // Specular candy gloss
    drawOval(
        color = Color.White.copy(alpha = 0.50f),
        topLeft = Offset(centerX - r * 0.48f, centerY - r * 0.58f),
        size = Size(r * 0.6f, r * 0.28f)
    )

    // Animated striped aura + stripes
    if (cell.special == CandySpecial.STRIPED_H || cell.special == CandySpecial.STRIPED_V) {
        val pulse = 0.55f + 0.35f * sin(shimmerPhase * 4f * PI).toFloat()
        drawCircle(
            color = Color.White.copy(alpha = pulse * 0.42f),
            radius = r * 1.08f,
            center = center,
            style = Stroke(width = 2.5f)
        )
    }

    when (cell.special) {
        CandySpecial.STRIPED_H -> {
            for (k in -1..1) {
                val dy = k * r * 0.35f
                drawLine(
                    color = Color.White.copy(alpha = 0.88f),
                    start = Offset(centerX - r * 0.65f, centerY + dy),
                    end = Offset(centerX + r * 0.65f, centerY + dy),
                    strokeWidth = (tilePx * 0.06f).coerceAtLeast(2.5f),
                    cap = StrokeCap.Round
                )
            }
        }
        CandySpecial.STRIPED_V -> {
            for (k in -1..1) {
                val dx = k * r * 0.35f
                drawLine(
                    color = Color.White.copy(alpha = 0.88f),
                    start = Offset(centerX + dx, centerY - r * 0.65f),
                    end = Offset(centerX + dx, centerY + r * 0.65f),
                    strokeWidth = (tilePx * 0.06f).coerceAtLeast(2.5f),
                    cap = StrokeCap.Round
                )
            }
        }
        else -> Unit
    }
}

fun DrawScope.drawGlossyCandy(
    cell: CandyCell,
    shimmerPhase: Float
) {
    val scratch = CandyRenderScratch()
    drawGlossyCandyAt(
        cell = cell,
        centerX = size.width / 2f,
        centerY = size.height / 2f,
        tilePx = min(size.width, size.height),
        scale = 1f,
        shimmerPhase = shimmerPhase,
        isSelected = false,
        scratch = scratch
    )
}
