package com.example.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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

fun DrawScope.drawGlossyCandy(
    cell: CandyCell,
    shimmerPhase: Float
) {
    val radius = min(size.width, size.height) * 0.40f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val center = Offset(cx, cy)

    if (cell.special == CandySpecial.COLOR_BOMB) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFFFFD54F), Color(0xFF5D2906), Color(0xFF2E1202)),
                center = center,
                radius = radius * 1.1f
            ),
            radius = radius,
            center = center
        )
        val dotColors = listOf(
            Color(0xFFFF3D71),
            Color(0xFF29B6F6),
            Color(0xFF00E676),
            Color(0xFFFFD54F),
            Color(0xFFB388FF),
            Color(0xFFFF8F00)
        )
        for (i in 0 until 6) {
            val angle = ((i * 60f) + shimmerPhase * 360f) * (PI / 180.0)
            val dx = (cos(angle) * radius * 0.56f).toFloat()
            val dy = (sin(angle) * radius * 0.56f).toFloat()
            drawCircle(dotColors[i], radius * 0.16f, Offset(cx + dx, cy + dy))
        }
        drawCircle(Color.White.copy(alpha = 0.7f), radius * 0.16f, center)
        return
    }

    val primary = candyPrimaryColor(cell.type)
    val secondary = candySecondaryColor(cell.type)
    val fillBrush = Brush.radialGradient(
        colors = listOf(Color.White.copy(alpha = 0.7f), primary, secondary),
        center = Offset(cx - radius * 0.25f, cy - radius * 0.25f),
        radius = radius * 1.45f
    )

    // Drop shadow
    drawCircle(
        color = Color.Black.copy(alpha = 0.25f),
        radius = radius * 0.96f,
        center = Offset(cx, cy + radius * 0.12f)
    )

    when (cell.type) {
        CandyType.STRAWBERRY -> {
            drawCircle(brush = fillBrush, radius = radius, center = center)
        }
        CandyType.LEMON -> {
            drawRoundRect(
                brush = fillBrush,
                topLeft = Offset(cx - radius * 0.9f, cy - radius * 0.9f),
                size = Size(radius * 1.8f, radius * 1.8f),
                cornerRadius = CornerRadius(radius * 0.45f)
            )
        }
        CandyType.BLUEBERRY -> {
            val diamond = Path().apply {
                moveTo(cx, cy - radius)
                lineTo(cx + radius, cy)
                lineTo(cx, cy + radius)
                lineTo(cx - radius, cy)
                close()
            }
            drawPath(diamond, brush = fillBrush)
            drawPath(diamond, Color.White.copy(alpha = 0.4f), style = Stroke(width = 2.5f))
        }
        CandyType.GRAPE -> {
            val hex = Path().apply {
                for (i in 0 until 6) {
                    val a = (i * 60f - 30f) * (PI / 180.0)
                    val px = cx + (cos(a) * radius).toFloat()
                    val py = cy + (sin(a) * radius).toFloat()
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            drawPath(hex, brush = fillBrush)
        }
        CandyType.MINT -> {
            val pill = Size(radius * 1.9f, radius * 1.35f)
            drawRoundRect(
                brush = fillBrush,
                topLeft = Offset(cx - pill.width / 2f, cy - pill.height / 2f),
                size = pill,
                cornerRadius = CornerRadius(pill.height / 2f)
            )
        }
        CandyType.ORANGE -> {
            val star = Path().apply {
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) radius * 1.05f else radius * 0.56f
                    val a = (i * 36f - 90f) * (PI / 180.0)
                    val px = cx + (cos(a) * r).toFloat()
                    val py = cy + (sin(a) * r).toFloat()
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            drawPath(star, brush = fillBrush)
        }
    }

    // Specular candy gloss
    drawOval(
        color = Color.White.copy(alpha = 0.48f),
        topLeft = Offset(cx - radius * 0.48f, cy - radius * 0.58f),
        size = Size(radius * 0.6f, radius * 0.28f)
    )

    // Special stripes
    when (cell.special) {
        CandySpecial.STRIPED_H -> {
            for (dy in listOf(-radius * 0.35f, 0f, radius * 0.35f)) {
                drawLine(
                    color = Color.White.copy(alpha = 0.85f),
                    start = Offset(cx - radius * 0.65f, cy + dy),
                    end = Offset(cx + radius * 0.65f, cy + dy),
                    strokeWidth = 3.5f
                )
            }
        }
        CandySpecial.STRIPED_V -> {
            for (dx in listOf(-radius * 0.35f, 0f, radius * 0.35f)) {
                drawLine(
                    color = Color.White.copy(alpha = 0.85f),
                    start = Offset(cx + dx, cy - radius * 0.65f),
                    end = Offset(cx + dx, cy + radius * 0.65f),
                    strokeWidth = 3.5f
                )
            }
        }
        else -> Unit
    }
}
