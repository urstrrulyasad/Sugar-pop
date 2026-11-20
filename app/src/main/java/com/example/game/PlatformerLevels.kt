package com.example.game

object PlatformerLevels {

    const val TOTAL_CAMPAIGN_LEVELS = 10

    private data class LevelTemplate(
        val worldLabel: String,
        val title: String,
        val subtitle: String,
        val theme: WorldTheme,
        val timeLimitSec: Float,
        val rows: List<String>
    )

    fun buildLevel(levelNumber: Int): PlatformerLevelSpec {
        val safeLevel = levelNumber.coerceAtLeast(1)
        val templateIdx = (safeLevel - 1) % templates.size
        val loopCycle = (safeLevel - 1) / templates.size
        val template = templates[templateIdx]

        val isHardLevel = safeLevel % 5 == 0

        // Master Mode scales speed after stage 10; every 5th stage is a Hard Level
        val speedMultiplier = (1f + loopCycle * 0.18f) * (if (isHardLevel) 1.24f else 1f)
        val timeLimit = (template.timeLimitSec - loopCycle * 10f - (if (isHardLevel) 25f else 0f)).coerceAtLeast(70f)
        val worldLabel = if (loopCycle == 0) {
            template.worldLabel
        } else {
            "${template.worldLabel}+${loopCycle}"
        }
        val title = if (loopCycle == 0) {
            template.title
        } else {
            "${template.title} ★"
        }
        val subtitle = if (isHardLevel) {
            "HARD LEVEL — Faster foes, tighter clock & 2 lives!"
        } else {
            template.subtitle
        }

        val rows = template.rows
        val heightTiles = rows.size
        val widthTiles = rows.maxOf { it.length }

        val tiles = mutableMapOf<Long, WorldTile>()
        val platforms = mutableListOf<MovingPlatform>()
        val coins = mutableListOf<CoinItem>()
        val enemies = mutableListOf<EnemyEntity>()

        var nextId = 1
        var spawnX = 2.5f
        var spawnY = 2.0f
        var checkpoint: CheckpointFlag? = null
        var goal: GoalFlagpole? = null

        for (asciiRow in rows.indices) {
            val line = rows[asciiRow]
            val worldRow = heightTiles - 1 - asciiRow
            for (col in line.indices) {
                val ch = line[col]
                val cx = col + 0.5f
                val wy = worldRow.toFloat()

                when (ch) {
                    '#' -> tiles[tileKey(col, worldRow)] = WorldTile(col, worldRow, TileKind.GROUND)
                    'I' -> tiles[tileKey(col, worldRow)] = WorldTile(col, worldRow, TileKind.ICE)
                    'B' -> tiles[tileKey(col, worldRow)] = WorldTile(col, worldRow, TileKind.BRICK)
                    '?' -> tiles[tileKey(col, worldRow)] = WorldTile(col, worldRow, TileKind.QUESTION_COIN)
                    'M' -> tiles[tileKey(col, worldRow)] = WorldTile(col, worldRow, TileKind.QUESTION_BERRY)
                    'S' -> tiles[tileKey(col, worldRow)] = WorldTile(col, worldRow, TileKind.QUESTION_STAR)
                    'P' -> tiles[tileKey(col, worldRow)] = WorldTile(col, worldRow, TileKind.PIPE_BODY)
                    'p' -> tiles[tileKey(col, worldRow)] = WorldTile(col, worldRow, TileKind.PIPE_TOP)
                    'T' -> {
                        tiles[tileKey(col, worldRow)] = WorldTile(col, worldRow, TileKind.PIPE_TOP)
                        enemies += EnemyEntity(
                            id = nextId++,
                            kind = EnemyKind.PIPE_SPROUT,
                            x = cx,
                            y = wy + 0.35f,
                            vx = 0f,
                            vy = 0f,
                            width = 0.62f,
                            height = 0.85f,
                            anchorY = wy + 0.25f,
                            sproutCycleOffset = (col % 5) * 0.7f
                        )
                    }
                    'W' -> tiles[tileKey(col, worldRow)] = WorldTile(col, worldRow, TileKind.CRUMBLE_BRIDGE)
                    '^' -> tiles[tileKey(col, worldRow)] = WorldTile(col, worldRow, TileKind.SPIKES)
                    'J' -> tiles[tileKey(col, worldRow)] = WorldTile(col, worldRow, TileKind.SPRING)
                    'C' -> coins += CoinItem(id = nextId++, x = cx, y = wy + 0.5f)
                    'G' -> enemies += EnemyEntity(
                        id = nextId++,
                        kind = EnemyKind.JELLY_WALKER,
                        x = cx,
                        y = wy,
                        vx = -2.2f * speedMultiplier,
                        vy = 0f
                    )
                    'K' -> enemies += EnemyEntity(
                        id = nextId++,
                        kind = EnemyKind.SHELL_BEETLE,
                        x = cx,
                        y = wy,
                        vx = -2.5f * speedMultiplier,
                        vy = 0f
                    )
                    'H' -> platforms += MovingPlatform(
                        id = nextId++,
                        baseX = col.toFloat(),
                        baseY = wy + 0.35f,
                        x = col.toFloat(),
                        y = wy + 0.35f,
                        prevX = col.toFloat(),
                        prevY = wy + 0.35f,
                        axis = PlatformMotionAxis.HORIZONTAL,
                        range = 2.8f,
                        speed = 1.35f * speedMultiplier,
                        phase = (col % 4) * 1.1f
                    )
                    'V' -> platforms += MovingPlatform(
                        id = nextId++,
                        baseX = col.toFloat(),
                        baseY = wy + 0.35f,
                        x = col.toFloat(),
                        y = wy + 0.35f,
                        prevX = col.toFloat(),
                        prevY = wy + 0.35f,
                        axis = PlatformMotionAxis.VERTICAL,
                        range = 2.4f,
                        speed = 1.45f * speedMultiplier,
                        phase = (col % 3) * 1.2f
                    )
                    '@' -> {
                        spawnX = cx
                        spawnY = wy
                    }
                    'R' -> checkpoint = CheckpointFlag(x = cx, y = wy)
                    'F' -> goal = GoalFlagpole(x = cx, baseY = wy)
                }
            }
        }

        val resolvedGoal = goal ?: GoalFlagpole(x = (widthTiles - 4).toFloat(), baseY = 2f)

        return PlatformerLevelSpec(
            levelNumber = safeLevel,
            worldLabel = worldLabel,
            title = title,
            subtitle = subtitle,
            theme = template.theme,
            widthTiles = widthTiles,
            heightTiles = heightTiles,
            timeLimitSec = timeLimit,
            speedMultiplier = speedMultiplier,
            isHardLevel = isHardLevel,
            spawnX = spawnX,
            spawnY = spawnY,
            checkpoint = checkpoint,
            goal = resolvedGoal,
            initialTiles = tiles,
            initialPlatforms = platforms,
            initialCoins = coins,
            initialEnemies = enemies
        )
    }

    fun createWorldState(levelNumber: Int, carriedScore: Int = 0): PlatformerWorldState {
        val spec = buildLevel(levelNumber)
        return PlatformerWorldState(
            levelNumber = spec.levelNumber,
            worldLabel = spec.worldLabel,
            title = spec.title,
            subtitle = spec.subtitle,
            theme = spec.theme,
            widthTiles = spec.widthTiles,
            heightTiles = spec.heightTiles,
            timeLeftSec = spec.timeLimitSec,
            timeLimitSec = spec.timeLimitSec,
            speedMultiplier = spec.speedMultiplier,
            isHardLevel = spec.isHardLevel,
            spawnX = spec.spawnX,
            spawnY = spec.spawnY,
            respawnX = spec.spawnX,
            respawnY = spec.spawnY,
            score = carriedScore,
            player = PlayerState(
                x = spec.spawnX,
                y = spec.spawnY,
                lives = if (spec.isHardLevel) 2 else 3
            ),
            tiles = spec.initialTiles,
            movingPlatforms = spec.initialPlatforms,
            coins = spec.initialCoins,
            enemies = spec.initialEnemies,
            checkpoint = spec.checkpoint,
            goal = spec.goal
        )
    }

    // 12 rows per level (row 0 = top sky y=11, row 11 = bottom floor y=0).
    // Every mandatory pit <= 4 tiles (or crossed with MovingPlatform / Spring).
    private val templates = listOf(
        // Level 1 (W1-1): Sunny Meadow - Classic Intro with Bricks, ? Blocks, Pipes, Shells & Goal
        LevelTemplate(
            worldLabel = "W1-1",
            title = "Frosting Fields",
            subtitle = "Stomp jellies, bonk ? blocks & reach the flag!",
            theme = WorldTheme.SUNNY_MEADOW,
            timeLimitSec = 140f,
            rows = listOf(
                "...........................................................................",
                ".......................C.C.C...............................................",
                ".....................B?BMB?B........................CCC.........CCC........",
                "...................................................#####.......#####.......",
                "............C.?............................................................",
                ".........B?BMB................................p.............R..............",
                "....................p............C.C..........P.....C.......#......C...C...",
                ".....@......G.......P.....K......###....p.....P..K..#..G....#......#...#..F",
                "########..######..#####..####..........###..#####.#########..###..#########",
                "########..######..#####..####..........###..#####.#########..###..#########",
                "########..######..#####..####..........###..#####.#########..###..#########",
                "########..######..#####..####..........###..#####.#########..###..#########"
            )
        ),

        // Level 2 (W1-2): Pipe Valley - Thorn Sprouts, Shell Bowling & Secret High Road
        LevelTemplate(
            worldLabel = "W1-2",
            title = "Pipe Valley",
            subtitle = "Time the pipe sprouts & kick shells across gaps!",
            theme = WorldTheme.SUNNY_MEADOW,
            timeLimitSec = 145f,
            rows = listOf(
                "..............................................................................",
                "......................C.C.C.C...........................C.C.C.................",
                ".....................#########.........................#B?SB#.................",
                "..............................................................................",
                ".............C.M...................................T............T.............",
                "...........B?B?B.........T..........T..............P............P......C......",
                "......T..................P..........P......R.......P......K.....P.....###.....",
                "..@...P....G....K........P....G.....P......#..G....P.....###....P..........F..",
                "#######..###########..#####..###..#####..############...........############..",
                "#######..###########..#####..###..#####..############...........############..",
                "#######..###########..#####..###..#####..############...........############..",
                "#######..###########..#####..###..#####..############...........############.."
            )
        ),

        // Level 3 (W2-1): Canyon Bridges - Crumbling Wafers & Bounce Springs
        LevelTemplate(
            worldLabel = "W2-1",
            title = "Canyon Bridges",
            subtitle = "Don't linger on crumbling wafer bridges!",
            theme = WorldTheme.SUNSET_CANYON,
            timeLimitSec = 140f,
            rows = listOf(
                "................................................................................",
                ".......................................C.C.C....................................",
                "...................C.C................B?BMB?B....................C.C.C..........",
                ".................#######........................................#######.........",
                "..........C.M.........................................C.S.......................",
                ".........#####...........WWWW....WWWW.......R........#####...............C.C....",
                ".....J......................................#...K..............J........#####...",
                ".@...#...........K...G................G.....#..###....G........#..............F.",
                "######..WWW.....######..............#####..###.....######..#####...WWWW..#######",
                "######..........######..............#####..###.....######..#####.........#######",
                "######..........######..............#####..###.....######..#####.........#######",
                "######..........######..............#####..###.....######..#####.........#######"
            )
        ),

        // Level 4 (W2-2): Sky Elevators - Moving Horizontal & Vertical Platforms Over Abyss
        LevelTemplate(
            worldLabel = "W2-2",
            title = "Sky Elevators",
            subtitle = "Ride moving lifts across open canyon drops!",
            theme = WorldTheme.SUNSET_CANYON,
            timeLimitSec = 150f,
            rows = listOf(
                "...................................................................................",
                "...........................C.C.C..............................C.C.C................",
                "..........................B?BMB?B............................#######...............",
                ".............C.C.....................................C.C...........................",
                "............#####.................V.........R.......#####......H...................",
                "....................H.......................#.........................V............",
                "......................................C.C...#..K...............................C...",
                "..@....G....H............G...........#####..#.###....G..................K.....###.F",
                "#######................######.......................######............######..#####",
                "#######................######.......................######............######..#####",
                "#######................######.......................######............######..#####",
                "#######................######.......................######............######..#####"
            )
        ),

        // Level 5 (W3-1): Twilight Cavern - Low Brick Ceilings, Spikes & Tight Precision Jumps
        LevelTemplate(
            worldLabel = "W3-1",
            title = "Crystal Cavern",
            subtitle = "Watch overhead bricks, crystal spikes & ambush beetles!",
            theme = WorldTheme.TWILIGHT_CAVERN,
            timeLimitSec = 150f,
            rows = listOf(
                "##################################################################################",
                "##################################################################################",
                "..................................................................................",
                "...............C.C.C............................C.S.C.............................",
                ".............B?BMB?B............................B?B?B............C.C..............",
                "......................................R.........................#####.............",
                ".......p................p.............#.......p............p......................",
                "..@....P...G..^^^..K....P....G..^^^...#..K....P...G..^^^...P...G....K.......J...F.",
                "#######..############..####..########.###..#####..######..###..#######..##########",
                "#######..############..####..########.###..#####..######..###..#######..##########",
                "#######..############..####..########.###..#####..######..###..#######..##########",
                "#######..############..####..########.###..#####..######..###..#######..##########"
            )
        ),

        // Level 6 (W3-2): Twin Towers - Vertical Climbing & Sky Crossing
        LevelTemplate(
            worldLabel = "W3-2",
            title = "Twin Towers",
            subtitle = "Scale the twin crystal towers & watch for pipe sprouts!",
            theme = WorldTheme.TWILIGHT_CAVERN,
            timeLimitSec = 155f,
            rows = listOf(
                "....................................................................................",
                ".................C.C.C.....................................C.C.C....................",
                "................#?M?S#....................................#######...................",
                "...........C...........................................C............................",
                "..........###............###........R........###......###............H..............",
                ".......C.................#.#........#........#.#....................................",
                "......###......J.........#.#...G....#...K....#.#....T........J..............C.C.....",
                "..@............#....K....#.#..###...#..###...#.#....P...G....#.....K...WWW.#####..F.",
                "#######...########.###..##.#.......###.......#.##..######..#####..####.........#####",
                "#######...########.###..##.#.......###.......#.##..######..#####..####.........#####",
                "#######...########.###..##.#.......###.......#.##..######..#####..####.........#####",
                "#######...########.###..##.#.......###.......#.##..######..#####..####.........#####"
            )
        ),

        // Level 7 (W4-1): Aurora Iceway - Slippery Ice Physics & Crumbling Bridges
        LevelTemplate(
            worldLabel = "W4-1",
            title = "Aurora Iceway",
            subtitle = "Control your momentum on slick glacier ice!",
            theme = WorldTheme.AURORA_GLACIER,
            timeLimitSec = 150f,
            rows = listOf(
                "......................................................................................",
                "........................C.C.C.................................C.C.C...................",
                ".......................I?M?S?I...............................I?B?B?I..................",
                "......................................................................................",
                "...........C.C...............................R.........C.C............................",
                "..........IIIII.......WWWW.....WWWW..........I........IIIII..........H................",
                ".............................................I...K....................................",
                "..@...G...........K........G............G....I..III........G...............K...J....F.",
                "IIIIIII..WWWWW..IIIIII...IIIII..^^^...IIII..III.....IIIIII....WWW....IIIIIII..########",
                "IIIIIII.........IIIIII...IIIII........IIII..III.....IIIIII...........IIIIIII..########",
                "IIIIIII.........IIIIII...IIIII........IIII..III.....IIIIII...........IIIIIII..########",
                "IIIIIII.........IIIIII...IIIII........IIII..III.....IIIIII...........IIIIIII..########"
            )
        ),

        // Level 8 (W4-2): Frostbite Gauntlet - Aerial Stomps, Moving Lifts & Double Pipe Sprouts
        LevelTemplate(
            worldLabel = "W4-2",
            title = "Frostbite Gauntlet",
            subtitle = "Chain aerial jumps across moving lifts & ice spikes!",
            theme = WorldTheme.AURORA_GLACIER,
            timeLimitSec = 160f,
            rows = listOf(
                ".......................................................................................",
                "...............................C.S.C...................................................",
                "..............................#######..............................C.C.C...............",
                "..............C.M.................................................I?B?M?I..............",
                ".............#####...................V........R........................................",
                "........................T.....................I......T.........H.......................",
                "...........J............P.......C.............I......P.......................T.........",
                "..@...G....#....K.......P......###....G..WWW..I..K...P....G...........K......P......F..",
                "IIIIIII..####..###..II..P..II.......IIII......I.III..P..IIII..WWW...IIIIII..###..######",
                "IIIIIII..####..###..II..P..II.......IIII......I.III..P..IIII........IIIIII..###..######",
                "IIIIIII..####..###..II..P..II.......IIII......I.III..P..IIII........IIIIII..###..######",
                "IIIIIII..####..###..II..P..II.......IIII......I.III..P..IIII........IIIIII..###..######"
            )
        ),

        // Level 9 (W5-1): Molten Moat - Lava Hazards, Crumbling Stones & Precision Spikes
        LevelTemplate(
            worldLabel = "W5-1",
            title = "Molten Moat",
            subtitle = "Leap over bubbling magma pits & crumbling bridges!",
            theme = WorldTheme.LAVA_CITADEL,
            timeLimitSec = 160f,
            rows = listOf(
                "........................................................................................",
                "............................C.C.C.................................C.S.C.................",
                "...........................B?M?B?B...............................#######................",
                "........................................................................................",
                "..........C.M...............................R..........V.......................C.C......",
                ".........#####..........H...................#......................WWWW.......#####.....",
                "....................J.......................#....T.........J............................",
                "..@...G.......^^^...#.......K......G..^^^...#....P...K.....#....G......^^^..K.........F.",
                "#######..WWW..#####...WWW..####..#########..#..###..###..#####..####..#######...########",
                "#######.......#####........####..#########..#..###..###..#####..####..#######...########",
                "#######.......#####........####..#########..#..###..###..#####..####..#######...########",
                "#######.......#####........####..#########..#..###..###..#####..####..#######...########"
            )
        ),

        // Level 10 (W5-2): Sugar Citadel Finale - Multi-Tier Gauntlet & Fortress Finale
        LevelTemplate(
            worldLabel = "W5-2",
            title = "Sugar Citadel",
            subtitle = "The ultimate 2.5D gauntlet — conquer the citadel!",
            theme = WorldTheme.LAVA_CITADEL,
            timeLimitSec = 175f,
            rows = listOf(
                ".............................................................................................",
                "................................C.S.C.....................................C.C.C..............",
                "...............................B?B?B?B...................................B?M?B?B.............",
                ".............C.M..............................................C.C............................",
                "............#####.....................V...........R..........#####......H....................",
                "......................T...........................#.............................V............",
                "..........J...........P.....K...............T.....#.....K...........J..................C.....",
                "..@...G...#..^^^..G...P....###..^^^...G.....P.....#....###..^^^..G..#.....K.......G...###..F.",
                "#######..##.######.###P###.....####..###..##P##..###..#####.....###...##.####..#####..#######",
                "#######..##.######.###P###.....####..###..##P##..###..#####.....###...##.####..#####..#######",
                "#######..##.######.###P###.....####..###..##P##..###..#####.....###...##.####..#####..#######",
                "#######..##.######.###P###.....####..###..##P##..###..#####.....###...##.####..#####..#######"
            )
        )
    )
}
