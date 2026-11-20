package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import com.example.game.EnemyEntity
import com.example.game.EnemyKind
import com.example.game.EnemyState
import com.example.game.GameStatus
import com.example.game.PlatformerWorldState
import com.example.game.PowerUpKind
import com.example.game.TileKind
import com.example.game.WorldTheme
import com.example.game.tileKey
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

private data class GroundThemePalette(
    val front: Color,
    val side: Color,
    val capFront: Color,
    val capTop: Color
)

private val SkyPalettes: Map<WorldTheme, List<Color>> = mapOf(
    WorldTheme.SUNNY_MEADOW to listOf(Color(0xFF1A78D6), Color(0xFF5FC3FF), Color(0xFFC5F2FF)),
    WorldTheme.SUNSET_CANYON to listOf(Color(0xFF411B5C), Color(0xFFB33F62), Color(0xFFFF9A62)),
    WorldTheme.TWILIGHT_CAVERN to listOf(Color(0xFF0B0E24), Color(0xFF19224E), Color(0xFF28346F)),
    WorldTheme.AURORA_GLACIER to listOf(Color(0xFF0A2444), Color(0xFF115572), Color(0xFF39D4B0)),
    WorldTheme.LAVA_CITADEL to listOf(Color(0xFF1A0710), Color(0xFF42101A), Color(0xFF7D1F18))
)

private val GroundPalettes: Map<WorldTheme, GroundThemePalette> = mapOf(
    WorldTheme.SUNNY_MEADOW to GroundThemePalette(Color(0xFF8D5524), Color(0xFF5D3411), Color(0xFF43A047), Color(0xFF76FF03)),
    WorldTheme.SUNSET_CANYON to GroundThemePalette(Color(0xFFA24B34), Color(0xFF6A2B1D), Color(0xFFFF8F00), Color(0xFFFFD54F)),
    WorldTheme.TWILIGHT_CAVERN to GroundThemePalette(Color(0xFF313966), Color(0xFF1C2242), Color(0xFF7E57C2), Color(0xFFB388FF)),
    WorldTheme.AURORA_GLACIER to GroundThemePalette(Color(0xFF28557A), Color(0xFF183652), Color(0xFF80DEEA), Color(0xFFE0F7FA)),
    WorldTheme.LAVA_CITADEL to GroundThemePalette(Color(0xFF3B242B), Color(0xFF211216), Color(0xFFBF360C), Color(0xFFFF6E40))
)

private val PipeLipColors = listOf(Color(0xFF1B5E20), Color(0xFFA5D6A7), Color(0xFF43A047), Color(0xFF144618))
private val PipeBodyColors = listOf(Color(0xFF1B5E20), Color(0xFF66BB6A), Color(0xFF2E7D32), Color(0xFF144618))
private val LavaColors = listOf(Color(0xFFFF6D00).copy(alpha = 0.85f), Color(0xFFD50000))
private val CoinGoldColors = listOf(Color(0xFFFFF59D), Color(0xFFFFC107))
private val BerryColors = listOf(Color(0xFFFF80AB), Color(0xFFE91E63))
private val PoleColors = listOf(Color(0xFFB0BEC5), Color.White, Color(0xFF78909C))
private val JellyColors = listOf(Color(0xFFE040FB), Color(0xFF7B1FA2))
private val ShellActiveColors = listOf(Color(0xFF69F0AE), Color(0xFF00796B))
private val ShellSlidingColors = listOf(Color(0xFFFF6D00), Color(0xFFFFD54F), Color(0xFF00BFA5))
private val HeroBodyColors = listOf(Color(0xFF40C4FF), Color(0xFF0277BD))
private val HeroHeadColors = listOf(Color(0xFFFFE0B2), Color(0xFFFFB74D))

/**
 * Reusable Skia Path & Paint buffers so 60/120fps Canvas rendering allocates zero objects in hot loops.
 */
class PlatformerRenderScratch {
    val pathA = Path()
    val pathB = Path()
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        style = Paint.Style.STROKE
        color = android.graphics.Color.argb(210, 20, 8, 40)
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        style = Paint.Style.FILL
    }
}

fun DrawScope.drawPlatformerWorld(
    state: PlatformerWorldState,
    scratch: PlatformerRenderScratch = PlatformerRenderScratch()
) {
    val tilePx = (size.height / 12.2f).coerceAtLeast(12f)
    val depthX = tilePx * 0.24f
    val depthY = tilePx * 0.18f
    val camX = state.cameraX
    val floorBaseline = size.height - tilePx * 0.2f

    fun toScreenX(wx: Float): Float = (wx - camX) * tilePx
    fun toScreenY(wy: Float): Float = floorBaseline - wy * tilePx

    // 1. Sky & Animated 2.5D Parallax Backdrop
    drawParallaxBackdrop(state.theme, camX, state.worldTime, tilePx, scratch)

    val visibleColStart = floor(camX - 1f).toInt().coerceAtLeast(0)
    val visibleColEnd = floor(camX + size.width / tilePx + 2f).toInt().coerceAtMost(state.widthTiles - 1)

    // 2. Pipe Sprout enemies drawn BEFORE pipe tops so they visibly emerge from inside pipes
    for (enemy in state.enemies) {
        if (enemy.kind != EnemyKind.PIPE_SPROUT) continue
        if (enemy.x < visibleColStart - 1f || enemy.x > visibleColEnd + 1f) continue
        val pipeLipY = enemy.anchorY + 0.72f
        val clipBottomPx = toScreenY(pipeLipY)
        clipRect(0f, 0f, size.width, clipBottomPx) {
            drawPipeSprout(
                cx = toScreenX(enemy.x),
                bottomY = toScreenY(enemy.y),
                w = enemy.width * tilePx,
                h = enemy.height * tilePx,
                worldTime = state.worldTime
            )
        }
    }

    // 3. 2.5D Extruded World Tiles (draw back-to-front so top/right extrusion overlaps cleanly)
    for (row in 0 until state.heightTiles) {
        for (col in visibleColStart..visibleColEnd) {
            val tile = state.tiles[tileKey(col, row)] ?: continue
            if (tile.kind == TileKind.CRUMBLE_BRIDGE && tile.respawnTimer >= 0f) continue

            val jitterX = if (tile.kind == TileKind.CRUMBLE_BRIDGE && tile.crumbleTimer >= 0f) {
                sin(state.worldTime * 48f + col) * tilePx * 0.08f
            } else {
                0f
            }

            val sx = toScreenX(col.toFloat()) + jitterX
            val sy = toScreenY((row + 1).toFloat() + tile.bumpOffset)

            draw25DTile(
                kind = tile.kind,
                theme = state.theme,
                x = sx,
                y = sy,
                tilePx = tilePx,
                depthX = depthX,
                depthY = depthY,
                worldTime = state.worldTime,
                col = col,
                bumpOffset = tile.bumpOffset,
                scratch = scratch
            )
        }
    }

    // 4. 2.5D Moving Platforms
    for (plat in state.movingPlatforms) {
        if (plat.x + plat.width < visibleColStart - 1f || plat.x > visibleColEnd + 1f) continue
        val sx = toScreenX(plat.x)
        val sy = toScreenY(plat.y + plat.height)
        val pw = plat.width * tilePx
        val ph = plat.height * tilePx
        drawExtrudedBox(
            left = sx,
            top = sy,
            width = pw,
            height = ph,
            depthX = depthX,
            depthY = depthY,
            frontColor = Color(0xFF455A64),
            topColor = Color(0xFFFFD54F),
            sideColor = Color(0xFF263238),
            scratch = scratch
        )
        // Pulsing thruster glow under moving platforms
        val thrusterPulse = 0.45f + 0.25f * sin(state.worldTime * 10f)
        drawOval(
            color = Color(0xFF40E0D0).copy(alpha = thrusterPulse),
            topLeft = Offset(sx + pw * 0.18f, sy + ph),
            size = Size(pw * 0.64f, ph * 0.36f)
        )
    }

    // 5. Checkpoint Flag
    state.checkpoint?.let { cp ->
        if (cp.x >= visibleColStart - 2f && cp.x <= visibleColEnd + 2f) {
            drawCheckpointFlag(
                cx = toScreenX(cp.x),
                baseY = toScreenY(cp.y),
                tilePx = tilePx,
                activated = cp.activated,
                worldTime = state.worldTime,
                scratch = scratch
            )
        }
    }

    // 6. Goal Flagpole & 2.5D Castle Pedestal
    val goal = state.goal
    if (goal.x >= visibleColStart - 4f && goal.x <= visibleColEnd + 4f) {
        drawGoalFlagpole(
            cx = toScreenX(goal.x),
            baseY = toScreenY(goal.baseY),
            heightPx = goal.height * tilePx,
            tilePx = tilePx,
            depthX = depthX,
            depthY = depthY,
            worldTime = state.worldTime,
            cleared = state.status == GameStatus.LEVEL_CLEAR,
            scratch = scratch
        )
    }

    // 7. Spinning 2.5D Coins with sparkle twinkle
    for (coin in state.coins) {
        if (coin.collected) continue
        if (coin.x < visibleColStart - 1f || coin.x > visibleColEnd + 1f) continue
        val bob = sin(state.worldTime * 4f + coin.x) * tilePx * 0.08f
        draw25DCoin(
            cx = toScreenX(coin.x),
            cy = toScreenY(coin.y) + bob,
            radius = tilePx * 0.32f,
            spinPhase = state.worldTime * 4.5f + coin.x * 0.7f
        )
    }

    // 8. Power-Ups (Heart Berry & Rainbow Star)
    for (item in state.powerUps) {
        val sx = toScreenX(item.x)
        val sy = toScreenY(item.y + 0.42f)
        when (item.kind) {
            PowerUpKind.BERRY -> drawHeartBerry(sx, sy, tilePx * 0.38f, state.worldTime)
            PowerUpKind.STAR -> drawRainbowStar(sx, sy, tilePx * 0.42f, state.worldTime, scratch)
        }
    }

    // 9. Grounded Enemies (Jelly Walkers & Shell Beetles) + contact shadow
    for (enemy in state.enemies) {
        if (enemy.kind == EnemyKind.PIPE_SPROUT) continue
        if (enemy.x < visibleColStart - 1f || enemy.x > visibleColEnd + 1f) continue
        val shadowY = findGroundShadowY(state, enemy.x, enemy.y)
        if (shadowY != null) {
            val dist = (enemy.y - shadowY).coerceIn(0f, 4f)
            val shadowAlpha = (0.36f * (1f - dist / 4f)).coerceAtLeast(0.05f)
            drawOval(
                color = Color.Black.copy(alpha = shadowAlpha),
                topLeft = Offset(toScreenX(enemy.x - 0.36f), toScreenY(shadowY) - tilePx * 0.07f),
                size = Size(tilePx * 0.72f, tilePx * 0.14f)
            )
        }
        drawGroundEnemy(
            enemy = enemy,
            cx = toScreenX(enemy.x),
            bottomY = toScreenY(enemy.y),
            tilePx = tilePx,
            worldTime = state.worldTime
        )
    }

    // 10. Hero ("Pip") + Contact Shadow + Dash Speed Lines
    val player = state.player
    val blinkHide = player.invincibleHurtTimer > 0f && ((state.worldTime * 16f).toInt() % 2 == 0)
    val shadowY = findGroundShadowY(state, player.x, player.y)
    if (shadowY != null) {
        val dist = (player.y - shadowY).coerceIn(0f, 5f)
        val alpha = (0.4f * (1f - dist / 5f)).coerceAtLeast(0.06f)
        val scale = (1f - dist * 0.12f).coerceIn(0.45f, 1f)
        val sw = tilePx * 0.72f * scale
        drawOval(
            color = Color.Black.copy(alpha = alpha),
            topLeft = Offset(toScreenX(player.x) - sw * 0.5f, toScreenY(shadowY) - tilePx * 0.07f),
            size = Size(sw, tilePx * 0.14f)
        )
    }
    if (!blinkHide) {
        val heroCx = toScreenX(player.x)
        val heroBot = toScreenY(player.y)
        // Subtle wind speed-lines when dashing fast or in Star Turbo
        if (abs(player.vx) > 6.8f || player.starTimer > 0f) {
            val dir = if (player.facingRight) 1f else -1f
            for (k in 0..2) {
                val ly = heroBot - tilePx * (0.28f + k * 0.25f)
                val len = tilePx * (0.55f + 0.25f * sin(state.worldTime * 24f + k))
                drawLine(
                    color = (if (player.starTimer > 0f) Color(0xFFFFEB3B) else Color.White).copy(alpha = 0.45f),
                    start = Offset(heroCx - dir * tilePx * 0.45f, ly),
                    end = Offset(heroCx - dir * (tilePx * 0.45f + len), ly),
                    strokeWidth = 2.2f,
                    cap = StrokeCap.Round
                )
            }
        }
        drawHeroPip(
            cx = heroCx,
            bottomY = heroBot,
            tilePx = tilePx,
            facingRight = player.facingRight,
            vx = player.vx,
            vy = player.vy,
            onGround = player.onGround,
            runPhase = player.runAnimPhase,
            squashX = player.squashX,
            squashY = player.squashY,
            hasSuperHeart = player.maxHearts >= 3,
            starTimer = player.starTimer,
            worldTime = state.worldTime,
            scratch = scratch
        )
    }

    // 11. Foreground Particles
    for (p in state.particles) {
        val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
        val px = toScreenX(p.x)
        val py = toScreenY(p.y)
        val r = p.size * tilePx * (0.5f + 0.5f * alpha)
        drawCircle(
            color = Color(p.colorHex).copy(alpha = alpha),
            radius = r,
            center = Offset(px, py)
        )
    }

    // 12. Floating Score / PowerUp Popups drawn directly on Canvas (zero Compose Text allocations)
    if (state.popups.isNotEmpty()) {
        val textPx = (tilePx * 0.46f).coerceIn(22f, 44f)
        scratch.strokePaint.textSize = textPx
        scratch.strokePaint.strokeWidth = textPx * 0.16f
        scratch.fillPaint.textSize = textPx
        val nc = drawContext.canvas.nativeCanvas
        for (pop in state.popups) {
            val px = toScreenX(pop.x)
            val py = toScreenY(pop.y)
            if (px in -80f..(size.width + 80f) && py in -40f..(size.height + 40f)) {
                val alphaRatio = (pop.life / 0.85f).coerceIn(0f, 1f)
                val alphaInt = (alphaRatio * 255).toInt().coerceIn(0, 255)
                scratch.strokePaint.alpha = alphaInt
                scratch.fillPaint.color = pop.colorHex.toInt()
                scratch.fillPaint.alpha = alphaInt
                nc.drawText(pop.text, px, py, scratch.strokePaint)
                nc.drawText(pop.text, px, py, scratch.fillPaint)
            }
        }
    }

    // 13. Lava / Mist Bottom Hazard Strip with animated glowing surface wave
    if (state.theme == WorldTheme.LAVA_CITADEL) {
        val lavaTop = floorBaseline - tilePx * 0.35f + sin(state.worldTime * 4f) * tilePx * 0.04f
        drawRect(
            brush = Brush.verticalGradient(LavaColors, startY = lavaTop, endY = size.height),
            topLeft = Offset(0f, lavaTop),
            size = Size(size.width, size.height - lavaTop)
        )
    }
}

private fun findGroundShadowY(state: PlatformerWorldState, wx: Float, wy: Float): Float? {
    val col = floor(wx).toInt()
    val startRow = floor(wy + 0.1f).toInt().coerceAtMost(state.heightTiles - 1)
    var bestY: Float? = null
    for (r in startRow downTo 0) {
        val t = state.tiles[tileKey(col, r)]
        if (t != null && t.isSolid) {
            bestY = (r + 1).toFloat()
            break
        }
    }
    for (plat in state.movingPlatforms) {
        if (wx >= plat.x && wx <= plat.x + plat.width && plat.y + plat.height <= wy + 0.25f) {
            val top = plat.y + plat.height
            if (bestY == null || top > bestY) {
                bestY = top
            }
        }
    }
    return bestY
}

private fun DrawScope.drawParallaxBackdrop(
    theme: WorldTheme,
    camX: Float,
    worldTime: Float,
    tilePx: Float,
    scratch: PlatformerRenderScratch
) {
    val skyColors = SkyPalettes[theme] ?: SkyPalettes.getValue(WorldTheme.SUNNY_MEADOW)
    drawRect(brush = Brush.verticalGradient(skyColors), size = size)

    // Twinkling stars in Twilight/Aurora/Lava worlds
    if (theme == WorldTheme.TWILIGHT_CAVERN || theme == WorldTheme.AURORA_GLACIER || theme == WorldTheme.LAVA_CITADEL) {
        val starColor = when (theme) {
            WorldTheme.LAVA_CITADEL -> Color(0xFFFF8A65)
            WorldTheme.AURORA_GLACIER -> Color(0xFFA7FFEB)
            else -> Color.White
        }
        for (i in 0 until 14) {
            val sx = ((i * 173f + camX * tilePx * 0.05f) % size.width + size.width) % size.width
            val sy = size.height * (0.06f + ((i * 37) % 45) / 100f)
            val twinkle = 0.25f + 0.65f * abs(sin(worldTime * (1.8f + (i % 3) * 0.6f) + i))
            drawCircle(
                color = starColor.copy(alpha = twinkle),
                radius = 1.6f + (i % 3) * 1.1f,
                center = Offset(sx, sy)
            )
        }
    }

    // Animated Sun / Moon / Aurora glow orb
    val orbColor = when (theme) {
        WorldTheme.SUNNY_MEADOW -> Color(0xFFFFF176).copy(alpha = 0.88f)
        WorldTheme.SUNSET_CANYON -> Color(0xFFFFB74D).copy(alpha = 0.88f)
        WorldTheme.TWILIGHT_CAVERN -> Color(0xFFB388FF).copy(alpha = 0.45f)
        WorldTheme.AURORA_GLACIER -> Color(0xFF64FFDA).copy(alpha = 0.42f)
        WorldTheme.LAVA_CITADEL -> Color(0xFFFF5252).copy(alpha = 0.5f)
    }
    val orbCenter = Offset(size.width * 0.8f, size.height * 0.22f)
    val orbRadius = size.minDimension * (0.14f + 0.008f * sin(worldTime * 2.2f))
    drawCircle(color = orbColor.copy(alpha = 0.22f), radius = orbRadius * 1.45f, center = orbCenter)
    drawCircle(color = orbColor, radius = orbRadius, center = orbCenter)

    // Far 2.5D faceted mountain peaks (using reusable Path instances)
    val farShift = camX * tilePx * 0.18f
    val peakSpan = size.width * 0.34f
    val farColorLeft = when (theme) {
        WorldTheme.SUNNY_MEADOW -> Color(0xFF53A8D8).copy(alpha = 0.5f)
        WorldTheme.SUNSET_CANYON -> Color(0xFF7B2E59).copy(alpha = 0.65f)
        WorldTheme.TWILIGHT_CAVERN -> Color(0xFF1E295E).copy(alpha = 0.8f)
        WorldTheme.AURORA_GLACIER -> Color(0xFF1B6985).copy(alpha = 0.6f)
        WorldTheme.LAVA_CITADEL -> Color(0xFF3A1019).copy(alpha = 0.8f)
    }
    val farColorRight = farColorLeft.copy(alpha = farColorLeft.alpha * 0.72f)

    val peakCount = (size.width / peakSpan).toInt() + 3
    val basePeakIndex = floor(farShift / peakSpan).toInt()
    val leftTri = scratch.pathA
    val rightTri = scratch.pathB
    for (i in basePeakIndex..(basePeakIndex + peakCount)) {
        val px = i * peakSpan - farShift
        val h = size.height * (0.28f + ((abs(i) % 3) * 0.08f))
        val midX = px + peakSpan * 0.5f
        val topY = size.height - h
        val botY = size.height

        leftTri.reset()
        leftTri.moveTo(px, botY)
        leftTri.lineTo(midX, topY)
        leftTri.lineTo(midX, botY)
        leftTri.close()

        rightTri.reset()
        rightTri.moveTo(midX, topY)
        rightTri.lineTo(px + peakSpan, botY)
        rightTri.lineTo(midX, botY)
        rightTri.close()

        drawPath(leftTri, farColorLeft)
        drawPath(rightTri, farColorRight)
    }

    // Floating clouds / atmospheric motes
    val cloudShift = camX * tilePx * 0.32f + worldTime * 16f
    val cloudSpan = size.width * 0.45f
    val startCloud = floor(cloudShift / cloudSpan).toInt()
    val cloudColor = when (theme) {
        WorldTheme.TWILIGHT_CAVERN -> Color(0xFF7C4DFF).copy(alpha = 0.25f)
        WorldTheme.LAVA_CITADEL -> Color(0xFFFF7043).copy(alpha = 0.28f)
        else -> Color.White.copy(alpha = 0.42f)
    }
    for (i in startCloud..(startCloud + 4)) {
        val cx = i * cloudSpan - cloudShift + size.width * 0.2f
        val cy = size.height * (0.14f + (abs(i) % 3) * 0.09f)
        drawRoundRect(
            color = cloudColor,
            topLeft = Offset(cx, cy),
            size = Size(tilePx * 2.4f, tilePx * 0.75f),
            cornerRadius = CornerRadius(tilePx * 0.4f)
        )
        drawCircle(
            color = cloudColor,
            radius = tilePx * 0.52f,
            center = Offset(cx + tilePx * 0.85f, cy)
        )
    }
}

private fun DrawScope.draw25DTile(
    kind: TileKind,
    theme: WorldTheme,
    x: Float,
    y: Float,
    tilePx: Float,
    depthX: Float,
    depthY: Float,
    worldTime: Float,
    col: Int,
    bumpOffset: Float,
    scratch: PlatformerRenderScratch
) {
    when (kind) {
        TileKind.GROUND -> {
            val p = GroundPalettes[theme] ?: GroundPalettes.getValue(WorldTheme.SUNNY_MEADOW)
            drawExtrudedBox(x, y, tilePx, tilePx, depthX, depthY, p.front, p.capTop, p.side, scratch)
            drawRect(
                color = p.capFront,
                topLeft = Offset(x, y),
                size = Size(tilePx, tilePx * 0.24f)
            )
        }

        TileKind.ICE -> {
            drawExtrudedBox(
                left = x,
                top = y,
                width = tilePx,
                height = tilePx,
                depthX = depthX,
                depthY = depthY,
                frontColor = Color(0xFF4DD0E1),
                topColor = Color(0xFFE0F7FA),
                sideColor = Color(0xFF0097A7),
                scratch = scratch
            )
            drawLine(
                color = Color.White.copy(alpha = 0.65f),
                start = Offset(x + tilePx * 0.18f, y + tilePx * 0.25f),
                end = Offset(x + tilePx * 0.45f, y + tilePx * 0.7f),
                strokeWidth = tilePx * 0.08f,
                cap = StrokeCap.Round
            )
        }

        TileKind.BRICK -> {
            drawExtrudedBox(
                left = x,
                top = y,
                width = tilePx,
                height = tilePx,
                depthX = depthX,
                depthY = depthY,
                frontColor = Color(0xFFD86B43),
                topColor = Color(0xFFF39C73),
                sideColor = Color(0xFF9E4020),
                scratch = scratch
            )
            val mortar = Color(0xFF6B2812).copy(alpha = 0.65f)
            drawLine(mortar, Offset(x, y + tilePx * 0.5f), Offset(x + tilePx, y + tilePx * 0.5f), strokeWidth = 2f)
            drawLine(mortar, Offset(x + tilePx * 0.5f, y), Offset(x + tilePx * 0.5f, y + tilePx * 0.5f), strokeWidth = 2f)
            drawLine(mortar, Offset(x + tilePx * 0.25f, y + tilePx * 0.5f), Offset(x + tilePx * 0.25f, y + tilePx), strokeWidth = 2f)
        }

        TileKind.QUESTION_COIN, TileKind.QUESTION_BERRY, TileKind.QUESTION_STAR -> {
            val pulse = 0.65f + 0.35f * sin(worldTime * 5.2f + col * 0.7f)
            drawExtrudedBox(
                x, y, tilePx, tilePx, depthX, depthY,
                Color(0xFFFFB300), Color(0xFFFFE082), Color(0xFFC67100), scratch
            )
            drawRect(
                color = Color(0xFFFFF8E1).copy(alpha = pulse * 0.6f),
                topLeft = Offset(x + tilePx * 0.1f, y + tilePx * 0.1f),
                size = Size(tilePx * 0.8f, tilePx * 0.8f),
                style = Stroke(width = 2.5f)
            )
            val cx = x + tilePx * 0.5f
            val cy = y + tilePx * 0.42f
            val qPath = scratch.pathA
            qPath.reset()
            qPath.moveTo(cx - tilePx * 0.14f, cy - tilePx * 0.08f)
            qPath.cubicTo(
                cx - tilePx * 0.14f, cy - tilePx * 0.28f,
                cx + tilePx * 0.18f, cy - tilePx * 0.28f,
                cx + tilePx * 0.14f, cy - tilePx * 0.04f
            )
            qPath.lineTo(cx, cy + tilePx * 0.12f)
            drawPath(qPath, Color(0xFF5D2A00), style = Stroke(width = tilePx * 0.12f, cap = StrokeCap.Round))
            drawCircle(Color(0xFF5D2A00), radius = tilePx * 0.065f, center = Offset(cx, y + tilePx * 0.76f))
        }

        TileKind.USED_BLOCK -> {
            drawExtrudedBox(
                left = x,
                top = y,
                width = tilePx,
                height = tilePx,
                depthX = depthX,
                depthY = depthY,
                frontColor = Color(0xFF6D5D53),
                topColor = Color(0xFF948378),
                sideColor = Color(0xFF463B35),
                scratch = scratch
            )
        }

        TileKind.PIPE_BODY -> {
            val pipeBrush = Brush.horizontalGradient(PipeBodyColors, startX = x + tilePx * 0.06f, endX = x + tilePx * 0.94f)
            drawRect(brush = pipeBrush, topLeft = Offset(x + tilePx * 0.06f, y), size = Size(tilePx * 0.88f, tilePx))
        }

        TileKind.PIPE_TOP -> {
            drawExtrudedBox(
                left = x - tilePx * 0.05f,
                top = y,
                width = tilePx * 1.1f,
                height = tilePx * 0.42f,
                depthX = depthX * 0.8f,
                depthY = depthY * 0.8f,
                frontColor = Color(0xFF2E7D32),
                topColor = Color(0xFF81C784),
                sideColor = Color(0xFF1B5E20),
                scratch = scratch
            )
            val lipBrush = Brush.horizontalGradient(PipeLipColors, startX = x - tilePx * 0.05f, endX = x + tilePx * 1.05f)
            drawRect(brush = lipBrush, topLeft = Offset(x - tilePx * 0.05f, y), size = Size(tilePx * 1.1f, tilePx * 0.42f))
            val bodyBrush = Brush.horizontalGradient(PipeBodyColors, startX = x + tilePx * 0.06f, endX = x + tilePx * 0.94f)
            drawRect(
                brush = bodyBrush,
                topLeft = Offset(x + tilePx * 0.06f, y + tilePx * 0.42f),
                size = Size(tilePx * 0.88f, tilePx * 0.58f)
            )
        }

        TileKind.CRUMBLE_BRIDGE -> {
            val warnColor = if (theme == WorldTheme.LAVA_CITADEL) Color(0xFFFF8A65) else Color(0xFFD7A87E)
            drawExtrudedBox(
                left = x,
                top = y,
                width = tilePx,
                height = tilePx * 0.42f,
                depthX = depthX,
                depthY = depthY,
                frontColor = warnColor,
                topColor = Color(0xFFFFE0B2),
                sideColor = Color(0xFF8D6E63),
                scratch = scratch
            )
            drawLine(
                color = Color(0xFF5D4037),
                start = Offset(x + tilePx * 0.35f, y),
                end = Offset(x + tilePx * 0.55f, y + tilePx * 0.42f),
                strokeWidth = 2f
            )
        }

        TileKind.SPIKES -> {
            val bot = y + tilePx
            val leftFace = scratch.pathA
            val rightFace = scratch.pathB
            for (k in 0..1) {
                val sx = x + k * (tilePx * 0.5f)
                val tipX = sx + tilePx * 0.25f
                val tipY = y + tilePx * 0.35f
                leftFace.reset()
                leftFace.moveTo(sx, bot)
                leftFace.lineTo(tipX, tipY)
                leftFace.lineTo(tipX, bot)
                leftFace.close()

                rightFace.reset()
                rightFace.moveTo(tipX, tipY)
                rightFace.lineTo(sx + tilePx * 0.5f, bot)
                rightFace.lineTo(tipX, bot)
                rightFace.close()

                drawPath(leftFace, Color(0xFFE0E0E0))
                drawPath(rightFace, Color(0xFF90A4AE))
            }
        }

        TileKind.SPRING -> {
            val bounceCompress = (-bumpOffset * tilePx * 0.6f).coerceIn(0f, tilePx * 0.2f)
            val baseTop = y + tilePx * 0.52f + bounceCompress
            drawRect(Color(0xFF455A64), Offset(x + tilePx * 0.15f, y + tilePx * 0.65f), Size(tilePx * 0.7f, tilePx * 0.35f))
            drawExtrudedBox(
                left = x + tilePx * 0.06f,
                top = baseTop - tilePx * 0.15f,
                width = tilePx * 0.88f,
                height = tilePx * 0.28f,
                depthX = depthX * 0.7f,
                depthY = depthY * 0.7f,
                frontColor = Color(0xFFFF3D71),
                topColor = Color(0xFFFF80AB),
                sideColor = Color(0xFFB71C1C),
                scratch = scratch
            )
        }
    }
}

private fun DrawScope.drawExtrudedBox(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    depthX: Float,
    depthY: Float,
    frontColor: Color,
    topColor: Color,
    sideColor: Color,
    scratch: PlatformerRenderScratch
) {
    val topPath = scratch.pathA
    topPath.reset()
    topPath.moveTo(left, top)
    topPath.lineTo(left + depthX, top - depthY)
    topPath.lineTo(left + width + depthX, top - depthY)
    topPath.lineTo(left + width, top)
    topPath.close()
    drawPath(topPath, topColor)

    val sidePath = scratch.pathB
    sidePath.reset()
    sidePath.moveTo(left + width, top)
    sidePath.lineTo(left + width + depthX, top - depthY)
    sidePath.lineTo(left + width + depthX, top + height - depthY)
    sidePath.lineTo(left + width, top + height)
    sidePath.close()
    drawPath(sidePath, sideColor)

    drawRect(
        color = frontColor,
        topLeft = Offset(left, top),
        size = Size(width, height)
    )
    drawLine(
        color = Color.White.copy(alpha = 0.25f),
        start = Offset(left, top),
        end = Offset(left + width, top),
        strokeWidth = 2f
    )
}

private fun DrawScope.draw25DCoin(
    cx: Float,
    cy: Float,
    radius: Float,
    spinPhase: Float
) {
    val scaleX = max(0.22f, abs(cos(spinPhase)))
    val rx = radius * scaleX
    drawOval(
        color = Color(0xFFE68A00),
        topLeft = Offset(cx - rx + radius * 0.18f, cy - radius),
        size = Size(rx * 2f, radius * 2f)
    )
    drawOval(
        brush = Brush.radialGradient(CoinGoldColors, center = Offset(cx, cy), radius = radius.coerceAtLeast(1f)),
        topLeft = Offset(cx - rx, cy - radius),
        size = Size(rx * 2f, radius * 2f)
    )
    if (scaleX > 0.45f) {
        drawRoundRect(
            color = Color(0xFFB26A00),
            topLeft = Offset(cx - rx * 0.22f, cy - radius * 0.45f),
            size = Size(rx * 0.44f, radius * 0.9f),
            cornerRadius = CornerRadius(4f)
        )
    }
}

private fun DrawScope.drawHeartBerry(cx: Float, cy: Float, r: Float, worldTime: Float) {
    val pulse = 1f + 0.06f * sin(worldTime * 6f)
    val pr = r * pulse
    drawCircle(
        brush = Brush.radialGradient(BerryColors, center = Offset(cx, cy), radius = pr.coerceAtLeast(1f)),
        radius = pr,
        center = Offset(cx, cy)
    )
    drawCircle(Color.White.copy(alpha = 0.72f), radius = pr * 0.24f, center = Offset(cx - pr * 0.28f, cy - pr * 0.28f))
    drawOval(
        color = Color(0xFF00E676),
        topLeft = Offset(cx - pr * 0.45f, cy - pr * 1.25f),
        size = Size(pr * 0.9f, pr * 0.45f)
    )
}

private fun DrawScope.drawRainbowStar(
    cx: Float,
    cy: Float,
    r: Float,
    worldTime: Float,
    scratch: PlatformerRenderScratch
) {
    drawCircle(
        color = Color(0xFFFFEB3B).copy(alpha = 0.34f),
        radius = r * (1.25f + 0.18f * sin(worldTime * 8f)),
        center = Offset(cx, cy)
    )
    val starPath = scratch.pathA
    starPath.reset()
    val rotOffset = sin(worldTime * 4f) * 10f
    for (i in 0 until 10) {
        val rad = if (i % 2 == 0) r else r * 0.45f
        val angle = (i * 36f - 90f + rotOffset) * (PI / 180.0)
        val px = cx + (cos(angle) * rad).toFloat()
        val py = cy + (sin(angle) * rad).toFloat()
        if (i == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
    }
    starPath.close()
    drawPath(starPath, Color(0xFFFFD54F))
    drawCircle(Color(0xFF42210B), radius = r * 0.12f, center = Offset(cx - r * 0.18f, cy))
    drawCircle(Color(0xFF42210B), radius = r * 0.12f, center = Offset(cx + r * 0.18f, cy))
}

private fun DrawScope.drawCheckpointFlag(
    cx: Float,
    baseY: Float,
    tilePx: Float,
    activated: Boolean,
    worldTime: Float,
    scratch: PlatformerRenderScratch
) {
    val poleH = tilePx * 2.1f
    drawLine(
        color = Color(0xFFCFD8DC),
        start = Offset(cx, baseY),
        end = Offset(cx, baseY - poleH),
        strokeWidth = tilePx * 0.12f,
        cap = StrokeCap.Round
    )
    val wave = sin(worldTime * 6f) * tilePx * 0.08f
    val flagColor = if (activated) Color(0xFF00E676) else Color(0xFF90A4AE)
    val flag = scratch.pathA
    flag.reset()
    flag.moveTo(cx, baseY - poleH)
    flag.lineTo(cx + tilePx * 0.95f, baseY - poleH + tilePx * 0.25f + wave)
    flag.lineTo(cx, baseY - poleH + tilePx * 0.55f)
    flag.close()
    drawPath(flag, flagColor)
    drawCircle(
        color = if (activated) Color(0xFFFFD54F) else Color(0xFFB0BEC5),
        radius = tilePx * 0.14f,
        center = Offset(cx, baseY - poleH)
    )
}

private fun DrawScope.drawGoalFlagpole(
    cx: Float,
    baseY: Float,
    heightPx: Float,
    tilePx: Float,
    depthX: Float,
    depthY: Float,
    worldTime: Float,
    cleared: Boolean,
    scratch: PlatformerRenderScratch
) {
    drawExtrudedBox(
        left = cx - tilePx * 0.45f,
        top = baseY - tilePx * 0.45f,
        width = tilePx * 0.9f,
        height = tilePx * 0.45f,
        depthX = depthX,
        depthY = depthY,
        frontColor = Color(0xFF78909C),
        topColor = Color(0xFFB0BEC5),
        sideColor = Color(0xFF455A64),
        scratch = scratch
    )
    drawLine(
        brush = Brush.horizontalGradient(PoleColors, startX = cx - tilePx * 0.1f, endX = cx + tilePx * 0.1f),
        start = Offset(cx, baseY - tilePx * 0.45f),
        end = Offset(cx, baseY - heightPx),
        strokeWidth = tilePx * 0.15f
    )
    drawCircle(Color(0xFFFFD54F), radius = tilePx * 0.22f, center = Offset(cx, baseY - heightPx))
    val flagY = if (cleared) baseY - tilePx * 1.4f else baseY - heightPx + tilePx * 0.25f
    val wave = sin(worldTime * 7f) * tilePx * 0.1f
    val pennant = scratch.pathA
    pennant.reset()
    pennant.moveTo(cx, flagY)
    pennant.lineTo(cx + tilePx * 1.35f, flagY + tilePx * 0.35f + wave)
    pennant.lineTo(cx, flagY + tilePx * 0.72f)
    pennant.close()
    drawPath(pennant, Color(0xFFFF3D71))
}

private fun DrawScope.drawPipeSprout(
    cx: Float,
    bottomY: Float,
    w: Float,
    h: Float,
    worldTime: Float
) {
    drawLine(
        color = Color(0xFF2E7D32),
        start = Offset(cx, bottomY),
        end = Offset(cx, bottomY - h * 0.55f),
        strokeWidth = w * 0.24f
    )
    val chomp = abs(sin(worldTime * 6f)) * 0.25f
    val headCy = bottomY - h * 0.68f
    drawCircle(Color(0xFFFF1744), radius = w * 0.48f, center = Offset(cx, headCy))
    drawCircle(Color.White.copy(alpha = 0.85f), radius = w * 0.1f, center = Offset(cx - w * 0.18f, headCy - w * 0.18f))
    drawCircle(Color.White.copy(alpha = 0.85f), radius = w * 0.08f, center = Offset(cx + w * 0.2f, headCy + w * 0.1f))
    drawArc(
        color = Color.White,
        startAngle = -20f,
        sweepAngle = 40f + chomp * 45f,
        useCenter = true,
        topLeft = Offset(cx - w * 0.42f, headCy - w * 0.36f),
        size = Size(w * 0.84f, w * 0.72f)
    )
}

private fun DrawScope.drawGroundEnemy(
    enemy: EnemyEntity,
    cx: Float,
    bottomY: Float,
    tilePx: Float,
    worldTime: Float
) {
    val w = enemy.width * tilePx
    val h = enemy.height * tilePx

    if (enemy.kind == EnemyKind.JELLY_WALKER) {
        if (enemy.state == EnemyState.SQUASHED) {
            drawOval(
                color = Color(0xFF9C27B0),
                topLeft = Offset(cx - w * 0.6f, bottomY - h * 0.28f),
                size = Size(w * 1.2f, h * 0.28f)
            )
            return
        }
        val bob = abs(sin(worldTime * 8f + enemy.id)) * h * 0.08f
        val stride = sin(worldTime * 11f + enemy.id) * w * 0.14f
        drawOval(Color(0xFF4A148C), Offset(cx - w * 0.32f + stride, bottomY - h * 0.16f), Size(w * 0.26f, h * 0.16f))
        drawOval(Color(0xFF4A148C), Offset(cx + w * 0.06f - stride, bottomY - h * 0.16f), Size(w * 0.26f, h * 0.16f))
        drawRoundRect(
            brush = Brush.radialGradient(JellyColors, center = Offset(cx, bottomY - h * 0.55f - bob), radius = w * 0.75f),
            topLeft = Offset(cx - w * 0.48f, bottomY - h * 0.92f - bob),
            size = Size(w * 0.96f, h * 0.8f),
            cornerRadius = CornerRadius(w * 0.42f)
        )
        drawCircle(Color.White, radius = w * 0.12f, center = Offset(cx - w * 0.16f, bottomY - h * 0.56f - bob))
        drawCircle(Color.White, radius = w * 0.12f, center = Offset(cx + w * 0.16f, bottomY - h * 0.56f - bob))
        drawCircle(Color.Black, radius = w * 0.06f, center = Offset(cx - w * 0.14f, bottomY - h * 0.56f - bob))
        drawCircle(Color.Black, radius = w * 0.06f, center = Offset(cx + w * 0.14f, bottomY - h * 0.56f - bob))
    } else {
        val isShell = enemy.state != EnemyState.ACTIVE
        val shellHeight = if (isShell) h * 0.62f else h * 0.72f
        val topY = bottomY - shellHeight
        if (!isShell) {
            val stride = sin(worldTime * 12f + enemy.id) * w * 0.14f
            drawOval(Color(0xFFFFB74D), Offset(cx - w * 0.34f + stride, bottomY - h * 0.18f), Size(w * 0.26f, h * 0.18f))
            drawOval(Color(0xFFFFB74D), Offset(cx + w * 0.08f - stride, bottomY - h * 0.18f), Size(w * 0.26f, h * 0.18f))
        }
        val shellBrush = if (enemy.state == EnemyState.SLIDING_SHELL) {
            Brush.linearGradient(ShellSlidingColors, start = Offset(cx - w * 0.5f, topY), end = Offset(cx + w * 0.5f, bottomY))
        } else {
            Brush.radialGradient(ShellActiveColors, center = Offset(cx, topY + shellHeight * 0.4f), radius = w * 0.7f)
        }
        drawRoundRect(
            brush = shellBrush,
            topLeft = Offset(cx - w * 0.48f, topY),
            size = Size(w * 0.96f, shellHeight * 0.92f),
            cornerRadius = CornerRadius(w * 0.44f)
        )
        val stripeOffset = if (enemy.state == EnemyState.SLIDING_SHELL) sin(worldTime * 28f) * w * 0.2f else 0f
        drawLine(
            color = Color(0xFFFFF8E1).copy(alpha = 0.7f),
            start = Offset(cx - w * 0.25f + stripeOffset, topY + shellHeight * 0.2f),
            end = Offset(cx + w * 0.25f + stripeOffset, topY + shellHeight * 0.2f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
        if (!isShell) {
            val dir = if (enemy.vx >= 0f) 1f else -1f
            drawCircle(Color.White, radius = w * 0.11f, center = Offset(cx + dir * w * 0.22f, bottomY - h * 0.38f))
            drawCircle(Color.Black, radius = w * 0.055f, center = Offset(cx + dir * w * 0.25f, bottomY - h * 0.38f))
        }
    }
}

private fun DrawScope.drawHeroPip(
    cx: Float,
    bottomY: Float,
    tilePx: Float,
    facingRight: Boolean,
    vx: Float,
    vy: Float,
    onGround: Boolean,
    runPhase: Float,
    squashX: Float,
    squashY: Float,
    hasSuperHeart: Boolean,
    starTimer: Float,
    worldTime: Float,
    scratch: PlatformerRenderScratch
) {
    val dir = if (facingRight) 1f else -1f
    val w = tilePx * 0.72f * squashX
    val h = tilePx * 0.94f * squashY
    val leanDeg = (vx / 9f).coerceIn(-1f, 1f) * 7f

    withTransform({
        rotate(degrees = leanDeg, pivot = Offset(cx, bottomY))
    }) {
        // Star rainbow or Heart Berry aura
        if (starTimer > 0f) {
            val auraColor = if (((worldTime * 14f).toInt() % 2) == 0) Color(0xFFFFEB3B) else Color(0xFF40E0D0)
            drawCircle(
                color = auraColor.copy(alpha = 0.38f),
                radius = max(w, h) * (0.78f + 0.08f * sin(worldTime * 16f)),
                center = Offset(cx, bottomY - h * 0.5f)
            )
        } else if (hasSuperHeart) {
            drawCircle(
                color = Color(0xFFFF80AB).copy(alpha = 0.24f),
                radius = max(w, h) * 0.68f,
                center = Offset(cx, bottomY - h * 0.5f)
            )
        }

        // Dynamic 2-wave trailing scarf/cape
        val speedFactor = (abs(vx) / 7f).coerceIn(0.15f, 1.2f)
        val capeFlap = sin(worldTime * 16f) * tilePx * 0.11f * speedFactor
        val capeFlap2 = cos(worldTime * 19f) * tilePx * 0.07f * speedFactor
        val cape = scratch.pathA
        cape.reset()
        cape.moveTo(cx - dir * w * 0.12f, bottomY - h * 0.55f)
        cape.lineTo(cx - dir * w * (0.65f + speedFactor * 0.38f), bottomY - h * 0.42f + capeFlap - vy * 0.4f)
        cape.lineTo(cx - dir * w * (0.50f + speedFactor * 0.18f), bottomY - h * 0.22f + capeFlap2)
        cape.close()
        drawPath(cape, Color(0xFFFFD54F))

        // Animated boots
        val legSwing = if (onGround && abs(vx) > 0.3f) sin(runPhase) * w * 0.26f else if (!onGround) w * 0.18f else 0f
        drawOval(
            color = Color(0xFF4E342E),
            topLeft = Offset(cx - w * 0.34f + legSwing, bottomY - h * 0.22f),
            size = Size(w * 0.32f, h * 0.22f)
        )
        drawOval(
            color = Color(0xFF3E2723),
            topLeft = Offset(cx + w * 0.02f - legSwing, bottomY - h * 0.22f),
            size = Size(w * 0.32f, h * 0.22f)
        )

        // 2.5D Explorer Body
        drawRoundRect(
            brush = Brush.radialGradient(HeroBodyColors, center = Offset(cx, bottomY - h * 0.42f), radius = w * 0.7f),
            topLeft = Offset(cx - w * 0.44f, bottomY - h * 0.68f),
            size = Size(w * 0.88f, h * 0.52f),
            cornerRadius = CornerRadius(w * 0.3f)
        )

        // Head
        val headCenterY = bottomY - h * 0.72f
        drawCircle(
            brush = Brush.radialGradient(HeroHeadColors, center = Offset(cx, headCenterY), radius = (w * 0.42f).coerceAtLeast(1f)),
            radius = w * 0.38f,
            center = Offset(cx, headCenterY)
        )

        // Crimson Explorer Cap with visor
        drawArc(
            color = Color(0xFFFF1744),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(cx - w * 0.42f, headCenterY - w * 0.44f),
            size = Size(w * 0.84f, w * 0.68f)
        )
        drawRoundRect(
            color = Color(0xFFD50000),
            topLeft = Offset(cx + (if (facingRight) 0f else -w * 0.52f), headCenterY - w * 0.16f),
            size = Size(w * 0.52f, w * 0.13f),
            cornerRadius = CornerRadius(6f)
        )

        // Directional Eyes with periodic natural blink
        val isBlinking = (worldTime % 3.6f) > 3.48f
        val eyeX = cx + dir * w * 0.16f
        if (isBlinking) {
            drawLine(
                color = Color(0xFF1A1A2E),
                start = Offset(eyeX - w * 0.08f, headCenterY + w * 0.02f),
                end = Offset(eyeX + w * 0.08f, headCenterY + w * 0.02f),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )
        } else {
            drawCircle(Color.White, radius = w * 0.1f, center = Offset(eyeX, headCenterY + w * 0.02f))
            drawCircle(Color(0xFF1A1A2E), radius = w * 0.055f, center = Offset(eyeX + dir * w * 0.03f, headCenterY + w * 0.02f))
        }
    }
}
