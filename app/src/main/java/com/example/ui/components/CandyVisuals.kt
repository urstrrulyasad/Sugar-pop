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
import com.example.game.CandyCell
import com.example.game.CandySpecial
import com.example.game.CandyType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun candyPrimaryColor(type: CandyType): Color = when (type) {
    CandyType.STRAWBERRY -> Color(0xFFFF4D79)
    CandyType.LEMON -> Color(0xFFFFD43B)
    CandyType.MINT -> Color(0xFF2DD4BF)
    CandyType.BLUEBERRY -> Color(0xFF3B82F6)
    CandyType.GRAPE -> Color(0xFFC084FC)
    CandyType.TANGERINE -> Color(0xFFFB923C)
}

fun DrawScope.drawGlossyCandy(
    cell: CandyCell,
    shimmerPhase: Float = 0f
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val r = minOf(w, h) * 0.45f

    if (cell.special == CandySpecial.COLOR_BOMB) {
        // Rotating rainbow aura behind Color Bomb
        drawCircle(
            color = Color(0xFFFFD15C).copy(alpha = 0.25f + 0.15f * sin(shimmerPhase * 2f * PI).toFloat()),
            radius = r * 1.14f,
            center = Offset(cx, cy)
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF7B4836), Color(0xFF261009)),
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
        val rotationOffset = shimmerPhase * 360.0
        for (i in 0 until 6) {
            val angle = (i * 60.0 + rotationOffset) * (PI / 180.0)
            val sx = cx + (r * 0.54f * cos(angle)).toFloat()
            val sy = cy + (r * 0.54f * sin(angle)).toFloat()
            drawCircle(color = sprinkleColors[i], radius = r * 0.16f, center = Offset(sx, sy))
        }
        drawCircle(color = Color.White.copy(alpha = 0.92f), radius = r * 0.23f)
        return
    }

    val (lightColor, darkColor) = when (cell.type) {
        CandyType.STRAWBERRY -> Color(0xFFFF7597) to Color(0xFFC9184A)
        CandyType.LEMON -> Color(0xFFFFE66D) to Color(0xFFF59F00)
        CandyType.MINT -> Color(0xFF67F3DF) to Color(0xFF0F766E)
        CandyType.BLUEBERRY -> Color(0xFF60A5FA) to Color(0xFF1D4ED8)
        CandyType.GRAPE -> Color(0xFFE0B8FF) to Color(0xFF7E22CE)
        CandyType.TANGERINE -> Color(0xFFFFAD78) to Color(0xFFEA580C)
    }

    // Soft drop shadow underneath candy
    drawCircle(
        color = Color.Black.copy(alpha = 0.28f),
        radius = r * 0.92f,
        center = Offset(cx, cy + r * 0.14f)
    )

    val brush = Brush.radialGradient(
        colors = listOf(lightColor, darkColor),
        center = Offset(cx - r * 0.3f, cy - r * 0.3f),
        radius = r * 1.45f
    )

    when (cell.type) {
        CandyType.STRAWBERRY -> {
            val path = Path().apply {
                moveTo(cx, cy + r * 0.85f)
                cubicTo(cx - r * 1.15f, cy + r * 0.1f, cx - r * 0.95f, cy - r * 0.95f, cx, cy - r * 0.32f)
                cubicTo(cx + r * 0.95f, cy - r * 0.95f, cx + r * 1.15f, cy + r * 0.1f, cx, cy + r * 0.85f)
                close()
            }
            drawPath(path, brush = brush)
        }
        CandyType.LEMON -> {
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
            drawCircle(brush = brush, radius = r * 0.92f, center = Offset(cx, cy))
            drawCircle(
                color = Color.White.copy(alpha = 0.25f),
                radius = r * 0.56f,
                center = Offset(cx, cy),
                style = Stroke(width = r * 0.14f)
            )
        }
        CandyType.TANGERINE -> {
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
        color = Color.White.copy(alpha = 0.56f),
        radius = r * 0.22f,
        center = Offset(cx - r * 0.28f, cy - r * 0.30f)
    )

    // Striped Special Frosting Bands + pulsing aura
    if (cell.special == CandySpecial.STRIPED_H || cell.special == CandySpecial.STRIPED_V) {
        val glowAlpha = 0.24f + 0.18f * sin(shimmerPhase * 2f * PI).toFloat()
        drawCircle(
            color = Color.White.copy(alpha = glowAlpha),
            radius = r * 1.08f,
            center = Offset(cx, cy),
            style = Stroke(width = r * 0.12f)
        )
    }

    when (cell.special) {
        CandySpecial.STRIPED_H -> {
            drawLine(
                color = Color.White.copy(alpha = 0.92f),
                start = Offset(cx - r * 0.72f, cy - r * 0.18f),
                end = Offset(cx + r * 0.72f, cy - r * 0.18f),
                strokeWidth = r * 0.15f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = 0.92f),
                start = Offset(cx - r * 0.72f, cy + r * 0.18f),
                end = Offset(cx + r * 0.72f, cy + r * 0.18f),
                strokeWidth = r * 0.15f,
                cap = StrokeCap.Round
            )
        }
        CandySpecial.STRIPED_V -> {
            drawLine(
                color = Color.White.copy(alpha = 0.92f),
                start = Offset(cx - r * 0.18f, cy - r * 0.72f),
                end = Offset(cx - r * 0.18f, cy + r * 0.72f),
                strokeWidth = r * 0.15f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = 0.92f),
                start = Offset(cx + r * 0.18f, cy - r * 0.72f),
                end = Offset(cx + r * 0.18f, cy + r * 0.72f),
                strokeWidth = r * 0.15f,
                cap = StrokeCap.Round
            )
        }
        else -> Unit
    }
}
