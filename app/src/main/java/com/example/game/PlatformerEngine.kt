package com.example.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object PlatformerEngine {

    const val GRAVITY = 34f
    const val EARLY_RELEASE_GRAVITY = 58f
    const val MAX_FALL_SPEED = -20f
    const val MAX_WALK_SPEED = 6.2f
    const val MAX_DASH_SPEED = 9.0f
    const val ACCEL_GROUND = 44f
    const val ACCEL_ICE = 14f
    const val ACCEL_AIR = 28f
    const val FRICTION_GROUND = 36f
    const val FRICTION_ICE = 6.5f
    const val JUMP_VY = 16.4f
    const val SPRING_VY = 22.8f
    const val STOMP_BOUNCE_VY = 11.8f
    const val STOMP_HIGH_VY = 16.0f
    const val COYOTE_TIME = 0.11f
    const val JUMP_BUFFER_TIME = 0.12f

    fun step(
        state: PlatformerWorldState,
        input: PlatformerInput,
        rawDt: Float
    ): PlatformerWorldState {
        if (state.status != GameStatus.PLAYING) return state.copy(lastEvents = emptyList())

        val dt = rawDt.coerceIn(0.001f, 0.033f)
        val events = mutableListOf<PlatformerEvent>()
        var nextId = state.nextEntityId
        var score = state.score
        var coinsCollected = state.coinsCollected
        var enemiesDefeated = state.enemiesDefeated
        val worldTime = state.worldTime + dt
        var timeLeft = (state.timeLeftSec - dt).coerceAtLeast(0f)

        var mutableTiles: MutableMap<Long, WorldTile>? = null
        fun activeTiles(): Map<Long, WorldTile> = mutableTiles ?: state.tiles
        fun editTiles(): MutableMap<Long, WorldTile> =
            mutableTiles ?: state.tiles.toMutableMap().also { mutableTiles = it }

        val particles = if (state.particles.isEmpty()) ArrayList(8) else state.particles.toMutableList()
        val popups = if (state.popups.isEmpty()) ArrayList(4) else state.popups.toMutableList()
        val powerUps = if (state.powerUps.isEmpty()) ArrayList(2) else state.powerUps.toMutableList()
        var player = state.player
        var checkpoint = state.checkpoint
        var respawnX = state.respawnX
        var respawnY = state.respawnY

        // 1. Update only tiles with active animations (block bonks & crumbling bridges)
        for (tile in state.tiles.values) {
            val needsBump = tile.bumpOffset != 0f || tile.bumpVelocity != 0f
            val needsCrumble = tile.kind == TileKind.CRUMBLE_BRIDGE &&
                (tile.crumbleTimer >= 0f || tile.respawnTimer >= 0f)
            if (!needsBump && !needsCrumble) continue

            var updated = tile
            if (needsBump) {
                val newVel = tile.bumpVelocity - 22f * dt
                val newOff = tile.bumpOffset + newVel * dt
                updated = if (newOff <= 0f) {
                    updated.copy(bumpOffset = 0f, bumpVelocity = 0f)
                } else {
                    updated.copy(bumpOffset = newOff, bumpVelocity = newVel)
                }
            }

            if (needsCrumble) {
                if (tile.crumbleTimer >= 0f) {
                    val remaining = tile.crumbleTimer - dt
                    if (remaining <= 0f) {
                        updated = updated.copy(crumbleTimer = -1f, respawnTimer = 3.0f)
                        particles += spawnBurst(
                            startId = nextId,
                            kind = ParticleKind.DUST,
                            x = tile.col + 0.5f,
                            y = tile.row + 0.6f,
                            count = 5,
                            colorHex = 0xFFD7A87EL
                        ).also { nextId += it.size }
                    } else {
                        updated = updated.copy(crumbleTimer = remaining)
                    }
                } else if (tile.respawnTimer >= 0f) {
                    val remaining = tile.respawnTimer - dt
                    updated = if (remaining <= 0f) {
                        updated.copy(respawnTimer = -1f)
                    } else {
                        updated.copy(respawnTimer = remaining)
                    }
                }
            }

            if (updated !== tile) {
                editTiles()[tileKey(tile.col, tile.row)] = updated
            }
        }

        // 2. Update moving platforms
        val platforms = state.movingPlatforms.map { plat ->
            val prevX = plat.x
            val prevY = plat.y
            val offset = sin(worldTime * plat.speed + plat.phase) * plat.range
            when (plat.axis) {
                PlatformMotionAxis.HORIZONTAL -> plat.copy(prevX = prevX, prevY = prevY, x = plat.baseX + offset)
                PlatformMotionAxis.VERTICAL -> plat.copy(prevX = prevX, prevY = prevY, y = plat.baseY + offset)
            }
        }

        // Carry player if riding a moving platform on previous frame
        if (player.onGround) {
            val standingPlat = platforms.firstOrNull { plat ->
                val pLeft = player.x - player.width * 0.45f
                val pRight = player.x + player.width * 0.45f
                pRight > plat.prevX && pLeft < plat.prevX + plat.width &&
                    abs(player.y - (plat.prevY + plat.height)) < 0.16f
            }
            if (standingPlat != null) {
                player = player.copy(
                    x = player.x + (standingPlat.x - standingPlat.prevX),
                    y = standingPlat.y + standingPlat.height
                )
            }
        }

        // 3. Player timers & squash recovery
        val coyote = if (player.onGround) COYOTE_TIME else (player.coyoteTimer - dt).coerceAtLeast(0f)
        val jumpBuf = if (input.jumpJustPressed) JUMP_BUFFER_TIME else (player.jumpBufferTimer - dt).coerceAtLeast(0f)
        val hurtInv = (player.invincibleHurtTimer - dt).coerceAtLeast(0f)
        val starTime = (player.starTimer - dt).coerceAtLeast(0f)
        val sqX = player.squashX + (1f - player.squashX) * (12f * dt).coerceAtMost(1f)
        val sqY = player.squashY + (1f - player.squashY) * (12f * dt).coerceAtMost(1f)

        // Star sparkle trail
        if (starTime > 0f && ((worldTime * 30f).toInt() != ((worldTime - dt) * 30f).toInt())) {
            particles += ParticleEffect(
                id = nextId++,
                kind = ParticleKind.STAR_TRAIL,
                x = player.x,
                y = player.y + player.height * 0.5f,
                vx = -player.vx * 0.2f,
                vy = 1.2f,
                life = 0.35f,
                maxLife = 0.35f,
                colorHex = if (((worldTime * 10).toInt() % 2) == 0) 0xFFFFEB3BL else 0xFF40E0D0L,
                size = 0.24f
            )
        }

        // 4. Horizontal velocity with ground/ice/air handling
        val speedBoost = if (starTime > 0f) 1.2f else 1f
        val maxSpeed = (if (input.dash) MAX_DASH_SPEED else MAX_WALK_SPEED) * speedBoost
        val moveDir = (if (input.right) 1f else 0f) - (if (input.left) 1f else 0f)
        var vx = player.vx
        var facingRight = player.facingRight

        if (moveDir != 0f) {
            facingRight = moveDir > 0f
            val accel = when {
                !player.onGround -> ACCEL_AIR
                player.onIce -> ACCEL_ICE
                else -> ACCEL_GROUND
            }
            vx += moveDir * accel * dt
            vx = vx.coerceIn(-maxSpeed, maxSpeed)
        } else {
            val friction = when {
                !player.onGround -> FRICTION_AIR
                player.onIce -> FRICTION_ICE
                else -> FRICTION_GROUND
            }
            if (abs(vx) <= friction * dt) {
                vx = 0f
            } else {
                vx -= kotlin.math.sign(vx) * friction * dt
            }
        }

        // 5. Jump & Gravity
        var vy = player.vy
        var usedCoyote = coyote
        var usedJumpBuffer = jumpBuf
        var newSqX = sqX
        var newSqY = sqY

        if (usedJumpBuffer > 0f && usedCoyote > 0f) {
            vy = JUMP_VY + (if (abs(vx) > MAX_WALK_SPEED * 0.9f) 0.9f else 0f)
            usedCoyote = 0f
            usedJumpBuffer = 0f
            newSqX = 0.78f
            newSqY = 1.26f
            events += PlatformerEvent.JUMP
            particles += spawnBurst(
                startId = nextId,
                kind = ParticleKind.DUST,
                x = player.x,
                y = player.y + 0.05f,
                count = 4,
                colorHex = 0xFFFFFFFFL
            ).also { nextId += it.size }
        }

        val gravityToApply = if (vy > 0f && !input.jumpHeld) EARLY_RELEASE_GRAVITY else GRAVITY
        vy = (vy - gravityToApply * dt).coerceAtLeast(MAX_FALL_SPEED)

        // 6. Move X & resolve solid tiles
        val halfW = player.width * 0.5f
        var px = (player.x + vx * dt).coerceIn(halfW, state.widthTiles - halfW)
        var py = player.y

        val minRowX = floor(py + 0.06f).toInt()
        val maxRowX = floor(py + player.height - 0.06f).toInt()
        if (vx > 0f) {
            val rightCol = floor(px + halfW).toInt()
            for (r in minRowX..maxRowX) {
                val tile = activeTiles()[tileKey(rightCol, r)]
                if (tile != null && tile.isSolid) {
                    px = rightCol.toFloat() - halfW - 0.001f
                    vx = 0f
                    break
                }
            }
        } else if (vx < 0f) {
            val leftCol = floor(px - halfW).toInt()
            for (r in minRowX..maxRowX) {
                val tile = activeTiles()[tileKey(leftCol, r)]
                if (tile != null && tile.isSolid) {
                    px = (leftCol + 1).toFloat() + halfW + 0.001f
                    vx = 0f
                    break
                }
            }
        }

        // 7. Move Y & resolve solid tiles + moving platforms + head bonk
        val prevY = py
        py += vy * dt
        var onGround = false
        var onIce = false

        val minColY = floor(px - halfW + 0.04f).toInt()
        val maxColY = floor(px + halfW - 0.04f).toInt()

        if (vy <= 0f) {
            val footRow = floor(py).toInt()
            var landedTileY: Float? = null
            for (c in minColY..maxColY) {
                val tile = activeTiles()[tileKey(c, footRow)]
                if (tile != null && tile.isSolid) {
                    val topY = (footRow + 1).toFloat()
                    if (prevY >= topY - 0.32f) {
                        landedTileY = max(landedTileY ?: topY, topY)
                        if (tile.kind == TileKind.ICE) onIce = true
                        if (tile.kind == TileKind.CRUMBLE_BRIDGE && tile.crumbleTimer < 0f) {
                            editTiles()[tileKey(c, footRow)] = tile.copy(crumbleTimer = 0.55f)
                        }
                        if (tile.kind == TileKind.SPRING) {
                            editTiles()[tileKey(c, footRow)] = tile.copy(bumpOffset = -0.18f, bumpVelocity = 3.2f)
                            py = topY
                            vy = SPRING_VY
                            onGround = false
                            newSqX = 0.7f
                            newSqY = 1.35f
                            events += PlatformerEvent.SPRING
                            landedTileY = null
                            break
                        }
                    }
                }
            }
            if (landedTileY != null) {
                if (!player.onGround && vy < -7.5f) {
                    newSqX = 1.22f
                    newSqY = 0.80f
                    particles += spawnBurst(
                        startId = nextId,
                        kind = ParticleKind.DUST,
                        x = px,
                        y = landedTileY + 0.04f,
                        count = 4,
                        colorHex = 0xFFEFEBE9L
                    ).also { nextId += it.size }
                }
                py = landedTileY
                vy = 0f
                onGround = true
            }

            // Moving platforms landing check
            if (!onGround && vy <= 0f) {
                for (plat in platforms) {
                    val platTop = plat.y + plat.height
                    val prevPlatTop = plat.prevY + plat.height
                    if (px + halfW - 0.04f > plat.x && px - halfW + 0.04f < plat.x + plat.width) {
                        if (prevY >= prevPlatTop - 0.26f && py <= platTop + 0.04f) {
                            py = platTop
                            vy = 0f
                            onGround = true
                            break
                        }
                    }
                }
            }
        } else {
            // Head bonk check against blocks above
            val headRow = floor(py + player.height).toInt()
            var bonkedRowY: Float? = null
            for (c in minColY..maxColY) {
                val key = tileKey(c, headRow)
                val tile = activeTiles()[key] ?: continue
                if (!tile.isSolid || tile.kind == TileKind.CRUMBLE_BRIDGE) continue
                bonkedRowY = headRow.toFloat() - player.height - 0.001f

                // Handle interactive blocks (? / M / S / Brick)
                when (tile.kind) {
                    TileKind.QUESTION_COIN -> {
                        editTiles()[key] = tile.copy(kind = TileKind.USED_BLOCK, bumpOffset = 0.08f, bumpVelocity = 2.6f)
                        coinsCollected++
                        score += 100
                        events += PlatformerEvent.COIN
                        popups += FloatingPopup(nextId++, "+100", c + 0.5f, headRow + 1.3f, colorHex = 0xFFFFD54FL)
                        particles += spawnBurst(nextId, ParticleKind.COIN_SPARKLE, c + 0.5f, headRow + 1.2f, 6, 0xFFFFD54FL)
                            .also { nextId += it.size }
                    }
                    TileKind.QUESTION_BERRY -> {
                        editTiles()[key] = tile.copy(kind = TileKind.USED_BLOCK, bumpOffset = 0.08f, bumpVelocity = 2.6f)
                        powerUps += PowerUpItem(
                            id = nextId++,
                            kind = PowerUpKind.BERRY,
                            x = c + 0.5f,
                            y = headRow + 1.0f,
                            vx = 2.4f,
                            vy = 0f
                        )
                        events += PlatformerEvent.BLOCK_BONK
                    }
                    TileKind.QUESTION_STAR -> {
                        editTiles()[key] = tile.copy(kind = TileKind.USED_BLOCK, bumpOffset = 0.08f, bumpVelocity = 2.6f)
                        powerUps += PowerUpItem(
                            id = nextId++,
                            kind = PowerUpKind.STAR,
                            x = c + 0.5f,
                            y = headRow + 1.0f,
                            vx = 3.1f,
                            vy = 5.5f
                        )
                        events += PlatformerEvent.BLOCK_BONK
                    }
                    TileKind.BRICK -> {
                        if (player.maxHearts >= 3 || starTime > 0f) {
                            editTiles().remove(key)
                            score += 50
                            events += PlatformerEvent.BREAK_BRICK
                            particles += spawnBurst(nextId, ParticleKind.BRICK_SHARD, c + 0.5f, headRow + 0.5f, 8, 0xFFD97252L)
                                .also { nextId += it.size }
                        } else {
                            editTiles()[key] = tile.copy(bumpOffset = 0.08f, bumpVelocity = 2.4f)
                            events += PlatformerEvent.BLOCK_BONK
                        }
                    }
                    else -> Unit
                }
            }
            if (bonkedRowY != null) {
                py = bonkedRowY
                vy = -1.5f
            }
        }

        val runPhase = if (onGround && abs(vx) > 0.35f) {
            player.runAnimPhase + abs(vx) * dt * 2.2f
        } else if (!onGround) {
            player.runAnimPhase
        } else {
            0f
        }

        player = player.copy(
            x = px,
            y = py,
            vx = vx,
            vy = vy,
            facingRight = facingRight,
            onGround = onGround,
            onIce = onIce,
            coyoteTimer = usedCoyote,
            jumpBufferTimer = usedJumpBuffer,
            invincibleHurtTimer = hurtInv,
            starTimer = starTime,
            runAnimPhase = runPhase,
            squashX = newSqX,
            squashY = newSqY,
            shellCombo = if (onGround) 0 else player.shellCombo
        )

        // 8. Collect Coins (only allocate list if a coin was collected)
        var anyCoinHit = false
        for (coin in state.coins) {
            if (!coin.collected &&
                abs(coin.x - player.x) < (player.width * 0.55f + 0.35f) &&
                abs(coin.y - (player.y + player.height * 0.5f)) < (player.height * 0.55f + 0.35f)
            ) {
                anyCoinHit = true
                break
            }
        }
        val updatedCoins = if (!anyCoinHit) {
            state.coins
        } else {
            state.coins.map { coin ->
                if (!coin.collected &&
                    abs(coin.x - player.x) < (player.width * 0.55f + 0.35f) &&
                    abs(coin.y - (player.y + player.height * 0.5f)) < (player.height * 0.55f + 0.35f)
                ) {
                    coinsCollected++
                    score += 100
                    events += PlatformerEvent.COIN
                    popups += FloatingPopup(nextId++, "+100", coin.x, coin.y + 0.4f, colorHex = 0xFFFFD54FL)
                    particles += spawnBurst(nextId, ParticleKind.COIN_SPARKLE, coin.x, coin.y, 5, 0xFFFFD54FL)
                        .also { nextId += it.size }
                    coin.copy(collected = true)
                } else {
                    coin
                }
            }
        }

        // 9. Update & Collect PowerUps
        val survivingPowerUps = if (powerUps.isEmpty()) emptyList() else mutableListOf<PowerUpItem>()
        if (survivingPowerUps is MutableList) {
            for (item in powerUps) {
                if (item.spawnRiseTimer > 0f) {
                    val risen = item.copy(
                        y = item.y + dt * 1.4f,
                        spawnRiseTimer = (item.spawnRiseTimer - dt).coerceAtLeast(0f)
                    )
                    survivingPowerUps += risen
                    continue
                }

                var ix = item.x + item.vx * dt
                var iy = item.y
                var ivx = item.vx
                var ivy = (item.vy - GRAVITY * 0.85f * dt).coerceAtLeast(MAX_FALL_SPEED)

                // Wall turn
                val checkCol = floor(ix + if (ivx >= 0f) 0.35f else -0.35f).toInt()
                val midRow = floor(iy + 0.35f).toInt()
                val sideTile = activeTiles()[tileKey(checkCol, midRow)]
                if (sideTile != null && sideTile.isSolid) {
                    ivx = -ivx
                    ix = item.x
                }

                // Floor check
                iy += ivy * dt
                val underRow = floor(iy).toInt()
                val underCol = floor(ix).toInt()
                val floorTile = activeTiles()[tileKey(underCol, underRow)]
                if (ivy <= 0f && floorTile != null && floorTile.isSolid && iy <= underRow + 1.05f) {
                    iy = (underRow + 1).toFloat()
                    ivy = if (item.kind == PowerUpKind.STAR) 9.6f else 0f
                }

            if (iy < -2f) continue

            // Player pickup check
                if (abs(ix - player.x) < (player.width * 0.55f + 0.4f) &&
                    abs((iy + 0.35f) - (player.y + player.height * 0.5f)) < (player.height * 0.55f + 0.4f)
                ) {
                    events += PlatformerEvent.POWER_UP
                    if (item.kind == PowerUpKind.BERRY) {
                        val newMax = max(player.maxHearts, 3)
                        val newHearts = min(player.hearts + 1, newMax)
                        player = player.copy(maxHearts = newMax, hearts = newHearts)
                        score += 500
                        popups += FloatingPopup(nextId++, "HEART +1", ix, iy + 0.7f, colorHex = 0xFFFF6E91L)
                    } else {
                        player = player.copy(starTimer = 8.0f)
                        score += 750
                        popups += FloatingPopup(nextId++, "STAR POWER!", ix, iy + 0.7f, colorHex = 0xFFFFEB3BL)
                    }
                } else {
                    survivingPowerUps += item.copy(x = ix, y = iy, vx = ivx, vy = ivy)
                }
            }
        }

        // 10. Update Enemies & Collisions
        val updatedEnemies = mutableListOf<EnemyEntity>()
        var hurtTriggered = false

        for (enemy in state.enemies) {
            if (enemy.state == EnemyState.SQUASHED) {
                val rem = enemy.stateTimer - dt
                if (rem > 0f) {
                    updatedEnemies += enemy.copy(stateTimer = rem)
                }
                continue
            }

            var ex = enemy.x
            var ey = enemy.y
            var evx = enemy.vx
            var evy = enemy.vy
            var eState = enemy.state

            when (enemy.kind) {
                EnemyKind.PIPE_SPROUT -> {
                    // Emerge out of pipe periodically; pause inside pipe when player stands directly over it
                    val cycle = (worldTime + enemy.sproutCycleOffset) % 3.6f
                    val targetOffset = when {
                        cycle < 1.2f -> -0.65f
                        cycle < 2.4f -> ((cycle - 1.2f) / 1.2f) * 1.15f - 0.65f
                        cycle < 3.0f -> 0.50f
                        else -> (1f - (cycle - 3.0f) / 0.6f) * 1.15f - 0.65f
                    }
                    ey = enemy.anchorY + targetOffset
                }

                EnemyKind.JELLY_WALKER, EnemyKind.SHELL_BEETLE -> {
                    ex += evx * dt
                    evy = (evy - GRAVITY * dt).coerceAtLeast(MAX_FALL_SPEED)

                    // Wall collision
                    val eHalf = enemy.width * 0.48f
                    val wallCol = floor(ex + if (evx >= 0f) eHalf else -eHalf).toInt()
                    val bodyRow = floor(ey + 0.35f).toInt()
                    val wallTile = activeTiles()[tileKey(wallCol, bodyRow)]
                    if (wallTile != null && wallTile.isSolid) {
                        evx = -evx
                        ex = enemy.x
                    }

                    // Floor & ledge patrol check
                    ey += evy * dt
                    val footRow = floor(ey).toInt()
                    val centerCol = floor(ex).toInt()
                    val floorTile = activeTiles()[tileKey(centerCol, footRow)]
                    if (evy <= 0f && floorTile != null && floorTile.isSolid && ey <= footRow + 1.08f) {
                        ey = (footRow + 1).toFloat()
                        evy = 0f

                        // Active walkers turn around before walking off ledges (sliding shells don't!)
                        if (eState == EnemyState.ACTIVE && evx != 0f) {
                            val aheadCol = floor(ex + kotlin.math.sign(evx) * (eHalf + 0.12f)).toInt()
                            val aheadTile = activeTiles()[tileKey(aheadCol, footRow)]
                            if (aheadTile == null || !aheadTile.isSolid) {
                                evx = -evx
                            }
                        }
                    }

                    if (ey < -2.5f) {
                        continue
                    }
                }
            }

            // Player vs Enemy overlap check
            val overlapX = abs(ex - player.x) < (player.width * 0.45f + enemy.width * 0.44f)
            val enemyTop = ey + enemy.height
            val playerTop = player.y + player.height
            val overlapY = player.y < enemyTop - 0.04f && playerTop > ey + 0.04f

            if (overlapX && overlapY) {
                if (player.starTimer > 0f) {
                    // Star power launches/eliminates any enemy!
                    score += 300
                    enemiesDefeated++
                    events += PlatformerEvent.STOMP
                    popups += FloatingPopup(nextId++, "+300", ex, ey + 0.9f, colorHex = 0xFFFFEB3BL)
                    particles += spawnBurst(nextId, ParticleKind.COIN_SPARKLE, ex, ey + 0.4f, 6, 0xFFFFEB3BL)
                        .also { nextId += it.size }
                    continue
                } else if (enemy.kind == EnemyKind.PIPE_SPROUT) {
                    // Pipe sprout only hurts when actually emerged above pipe lip
                    if (ey > enemy.anchorY + 0.1f) {
                        hurtTriggered = true
                    }
                } else {
                    val isStomp = player.vy < 0f && (prevY >= enemyTop - 0.44f)
                    if (isStomp) {
                        val bounceVy = if (input.jumpHeld) STOMP_HIGH_VY else STOMP_BOUNCE_VY
                        player = player.copy(
                            y = enemyTop + 0.02f,
                            vy = bounceVy,
                            squashX = 0.78f,
                            squashY = 1.24f,
                            shellCombo = player.shellCombo + 1
                        )
                        events += PlatformerEvent.STOMP

                        if (enemy.kind == EnemyKind.JELLY_WALKER) {
                            val pts = 150 * player.shellCombo.coerceAtMost(4)
                            score += pts
                            enemiesDefeated++
                            popups += FloatingPopup(nextId++, "+$pts", ex, enemyTop + 0.3f)
                            eState = EnemyState.SQUASHED
                            updatedEnemies += enemy.copy(
                                x = ex,
                                y = ey,
                                vx = 0f,
                                vy = 0f,
                                state = EnemyState.SQUASHED,
                                stateTimer = 0.25f
                            )
                            continue
                        } else if (enemy.kind == EnemyKind.SHELL_BEETLE) {
                            when (eState) {
                                EnemyState.ACTIVE -> {
                                    score += 100
                                    popups += FloatingPopup(nextId++, "+100", ex, enemyTop + 0.3f)
                                    eState = EnemyState.RETRACTED_SHELL
                                    evx = 0f
                                }
                                EnemyState.RETRACTED_SHELL -> {
                                    val kickDir = if (player.x <= ex) 1f else -1f
                                    eState = EnemyState.SLIDING_SHELL
                                    evx = kickDir * 11.5f * state.speedMultiplier
                                    events += PlatformerEvent.KICK_SHELL
                                }
                                EnemyState.SLIDING_SHELL -> {
                                    eState = EnemyState.RETRACTED_SHELL
                                    evx = 0f
                                }
                                else -> Unit
                            }
                        }
                    } else if (enemy.kind == EnemyKind.SHELL_BEETLE && eState == EnemyState.RETRACTED_SHELL) {
                        // Kick stationary shell from side!
                        val kickDir = if (player.x <= ex) 1f else -1f
                        eState = EnemyState.SLIDING_SHELL
                        evx = kickDir * 11.5f * state.speedMultiplier
                        ex += kickDir * 0.25f
                        score += 150
                        events += PlatformerEvent.KICK_SHELL
                        popups += FloatingPopup(nextId++, "KICK!", ex, enemyTop + 0.3f)
                    } else {
                        hurtTriggered = true
                    }
                }
            }

            updatedEnemies += enemy.copy(
                x = ex,
                y = ey,
                vx = evx,
                vy = evy,
                state = eState
            )
        }

        // 10b. Sliding shell knocking out other active walkers/beetles
        val shellFinalEnemies = updatedEnemies.toMutableList()
        val knockedOutIds = mutableSetOf<Int>()
        for (shell in updatedEnemies) {
            if (shell.kind == EnemyKind.SHELL_BEETLE && shell.state == EnemyState.SLIDING_SHELL) {
                for (other in updatedEnemies) {
                    if (other.id == shell.id || other.id in knockedOutIds) continue
                    if (other.kind == EnemyKind.PIPE_SPROUT || other.state == EnemyState.SQUASHED) continue
                    if (abs(shell.x - other.x) < 0.72f && abs(shell.y - other.y) < 0.72f) {
                        knockedOutIds += other.id
                        score += 400
                        enemiesDefeated++
                        events += PlatformerEvent.STOMP
                        popups += FloatingPopup(nextId++, "+400", other.x, other.y + 0.8f, colorHex = 0xFFFF9E40L)
                        particles += spawnBurst(nextId, ParticleKind.COIN_SPARKLE, other.x, other.y + 0.4f, 6, 0xFFFF9E40L)
                            .also { nextId += it.size }
                    }
                }
            }
        }
        if (knockedOutIds.isNotEmpty()) {
            shellFinalEnemies.removeAll { it.id in knockedOutIds }
        }

        // 11. Spike overlap check
        if (!hurtTriggered && player.starTimer <= 0f) {
            val spikeRow = floor(player.y + 0.25f).toInt()
            for (c in minColY..maxColY) {
                val tile = activeTiles()[tileKey(c, spikeRow)]
                if (tile != null && tile.kind == TileKind.SPIKES && player.y < spikeRow + 0.52f) {
                    hurtTriggered = true
                    break
                }
            }
        }

        // 12. Checkpoint check
        if (checkpoint != null && !checkpoint.activated && player.x >= checkpoint.x - 0.4f) {
            checkpoint = checkpoint.copy(activated = true)
            respawnX = checkpoint.x
            respawnY = checkpoint.y + 0.1f
            score += 250
            events += PlatformerEvent.CHECKPOINT
            popups += FloatingPopup(nextId++, "CHECKPOINT!", checkpoint.x, checkpoint.y + 1.6f, colorHex = 0xFF69F0AEL)
        }

        // 13. Apply Damage / Pit / Timeout
        var nextStatus = state.status
        val fellInPit = player.y < -1.2f
        val timedOut = timeLeft <= 0f

        if (fellInPit || timedOut || (hurtTriggered && player.invincibleHurtTimer <= 0f)) {
            if (fellInPit || timedOut || player.hearts <= 1) {
                val nextLives = player.lives - 1
                events += PlatformerEvent.HURT
                if (nextLives <= 0) {
                    player = player.copy(hearts = 0, lives = 0)
                    nextStatus = GameStatus.GAME_OVER
                } else {
                    player = PlayerState(
                        x = respawnX,
                        y = respawnY + 0.1f,
                        hearts = 2,
                        maxHearts = 2,
                        lives = nextLives,
                        invincibleHurtTimer = 1.8f
                    )
                    if (timedOut) {
                        timeLeft = state.timeLimitSec
                    }
                    popups += FloatingPopup(nextId++, "LIVES: $nextLives", respawnX, respawnY + 1.4f, colorHex = 0xFFFF5252L)
                }
            } else {
                events += PlatformerEvent.HURT
                player = player.copy(
                    hearts = player.hearts - 1,
                    vy = 8.5f,
                    onGround = false,
                    invincibleHurtTimer = 1.6f,
                    squashX = 1.25f,
                    squashY = 0.78f
                )
                popups += FloatingPopup(nextId++, "OUCH!", player.x, player.y + 1.3f, colorHex = 0xFFFF5252L)
            }
        }

        // 14. Goal Flagpole check
        var flagBonus = state.flagGrabBonus
        var timeBonus = state.timeBonus
        var stars = state.starsEarned
        if (nextStatus == GameStatus.PLAYING) {
            val goal = state.goal
            if (player.x + halfW >= goal.x - 0.35f &&
                player.x - halfW <= goal.x + 0.55f &&
                player.y <= goal.baseY + goal.height + 0.6f
            ) {
                val heightRatio = ((player.y - goal.baseY) / goal.height).coerceIn(0f, 1f)
                flagBonus = 500 + (heightRatio * 1500f).toInt()
                timeBonus = (timeLeft.toInt() * 10).coerceAtLeast(0)
                score += flagBonus + timeBonus
                val totalCoins = updatedCoins.size.coerceAtLeast(1)
                val coinRatio = coinsCollected.toFloat() / totalCoins.toFloat()
                stars = when {
                    player.lives >= 3 && (coinRatio >= 0.45f || heightRatio >= 0.55f) -> 3
                    player.lives >= 2 || coinRatio >= 0.3f -> 2
                    else -> 1
                }
                nextStatus = GameStatus.LEVEL_CLEAR
                events += PlatformerEvent.LEVEL_CLEAR
                particles += spawnBurst(nextId, ParticleKind.FIREWORK, goal.x, goal.baseY + goal.height, 16, 0xFFFFD54FL)
                    .also { nextId += it.size }
            }
        }

        // 15. Step Particles & Popups
        val nextParticles = particles.mapNotNull { p ->
            val life = p.life - dt
            if (life <= 0f) null
            else {
                val gravityScale = if (p.kind == ParticleKind.BRICK_SHARD) 28f else 12f
                p.copy(
                    x = p.x + p.vx * dt,
                    y = p.y + p.vy * dt,
                    vy = p.vy - gravityScale * dt,
                    life = life
                )
            }
        }

        val nextPopups = popups.mapNotNull { pop ->
            val life = pop.life - dt
            if (life <= 0f) null
            else pop.copy(y = pop.y + dt * 1.1f, life = life)
        }

        // 16. Smooth camera look-ahead
        val lookAhead = if (player.facingRight) 2.2f else -1.2f
        val targetCamX = (player.x - 5.2f + lookAhead).coerceIn(0f, (state.widthTiles - 13f).coerceAtLeast(0f))
        val newCamX = state.cameraX + (targetCamX - state.cameraX) * (7.5f * dt).coerceAtMost(1f)

        return state.copy(
            timeLeftSec = timeLeft,
            respawnX = respawnX,
            respawnY = respawnY,
            status = nextStatus,
            score = score,
            coinsCollected = coinsCollected,
            enemiesDefeated = enemiesDefeated,
            starsEarned = stars,
            flagGrabBonus = flagBonus,
            timeBonus = timeBonus,
            player = player,
            tiles = mutableTiles ?: state.tiles,
            movingPlatforms = platforms,
            coins = updatedCoins,
            powerUps = survivingPowerUps,
            enemies = shellFinalEnemies,
            particles = nextParticles,
            popups = nextPopups,
            checkpoint = checkpoint,
            cameraX = newCamX,
            worldTime = worldTime,
            nextEntityId = nextId,
            lastEvents = events
        )
    }

    private fun spawnBurst(
        startId: Int,
        kind: ParticleKind,
        x: Float,
        y: Float,
        count: Int,
        colorHex: Long
    ): List<ParticleEffect> {
        return List(count) { i ->
            val angle = (i.toFloat() / count.toFloat()) * (2f * PI.toFloat()) + 0.3f
            val speed = 2.8f + (i % 3) * 1.4f
            ParticleEffect(
                id = startId + i,
                kind = kind,
                x = x,
                y = y,
                vx = cos(angle) * speed,
                vy = abs(sin(angle)) * speed + 2.5f,
                life = 0.55f,
                maxLife = 0.55f,
                colorHex = colorHex,
                size = if (kind == ParticleKind.BRICK_SHARD) 0.28f else 0.2f
            )
        }
    }

    private const val FRICTION_AIR = 4f
}
