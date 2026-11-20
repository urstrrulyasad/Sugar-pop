package com.example.game

import kotlin.math.abs
import kotlin.random.Random

enum class CandyType {
    STRAWBERRY,
    LEMON,
    BLUEBERRY,
    GRAPE,
    MINT,
    ORANGE
}

enum class CandySpecial {
    NONE,
    STRIPED_H,
    STRIPED_V,
    COLOR_BOMB
}

data class GridPos(val row: Int, val col: Int)

data class CandyCell(
    val id: Long,
    val type: CandyType,
    val special: CandySpecial = CandySpecial.NONE
)

data class CandyLevelConfig(
    val level: Int,
    val worldName: String,
    val targetScore: Int,
    val maxMoves: Int
)

data class MatchResolutionStep(
    val clearedPositions: Set<GridPos>,
    val pointsEarned: Int,
    val spawnedSpecials: Map<GridPos, CandyCell>,
    val nextBoardAfterClearAndDrop: List<List<CandyCell>>
)

object CandyGameEngine {
    const val BOARD_SIZE = 8
    private var idSeq = 1L

    private fun nextId(): Long = idSeq++

    private val worldNames = listOf(
        "Sugar Meadow",
        "Lemonade Lagoon",
        "Berry Kingdom",
        "Mint Glacier",
        "Tangerine Canyon",
        "Caramel Citadel",
        "Starlight Galaxy"
    )

    fun levelConfig(level: Int): CandyLevelConfig {
        val safeLevel = level.coerceAtLeast(1)
        val worldName = worldNames[(safeLevel - 1) % worldNames.size]
        val targetScore = 1000 + (safeLevel - 1) * 750
        val maxMoves = (20 + (safeLevel % 5)).coerceIn(18, 25)
        return CandyLevelConfig(
            level = safeLevel,
            worldName = worldName,
            targetScore = targetScore,
            maxMoves = maxMoves
        )
    }

    fun createInitialBoard(): List<List<CandyCell>> {
        val types = CandyType.entries
        val grid = MutableList(BOARD_SIZE) {
            MutableList(BOARD_SIZE) {
                CandyCell(nextId(), types[Random.nextInt(types.size)])
            }
        }
        for (r in 0 until BOARD_SIZE) {
            for (c in 0 until BOARD_SIZE) {
                var candidate = types[Random.nextInt(types.size)]
                while (
                    (c >= 2 && grid[r][c - 1].type == candidate && grid[r][c - 2].type == candidate) ||
                    (r >= 2 && grid[r - 1][c].type == candidate && grid[r - 2][c].type == candidate)
                ) {
                    candidate = types[(candidate.ordinal + 1) % types.size]
                }
                grid[r][c] = CandyCell(nextId(), candidate)
            }
        }
        val immutable = grid.map { it.toList() }
        return if (hasValidMove(immutable)) immutable else shuffleBoard(immutable)
    }

    fun areAdjacent(a: GridPos, b: GridPos): Boolean {
        return abs(a.row - b.row) + abs(a.col - b.col) == 1
    }

    fun swap(board: List<List<CandyCell>>, a: GridPos, b: GridPos): List<List<CandyCell>> {
        val mutable = board.map { it.toMutableList() }
        val tmp = mutable[a.row][a.col]
        mutable[a.row][a.col] = mutable[b.row][b.col]
        mutable[b.row][b.col] = tmp
        return mutable.map { it.toList() }
    }

    fun isColorBombSwap(board: List<List<CandyCell>>, a: GridPos, b: GridPos): Boolean {
        return board[a.row][a.col].special == CandySpecial.COLOR_BOMB ||
            board[b.row][b.col].special == CandySpecial.COLOR_BOMB
    }

    fun resolveColorBombSwap(
        board: List<List<CandyCell>>,
        a: GridPos,
        b: GridPos
    ): MatchResolutionStep {
        val cellA = board[a.row][a.col]
        val cellB = board[b.row][b.col]
        val cleared = mutableSetOf(a, b)
        if (cellA.special == CandySpecial.COLOR_BOMB && cellB.special == CandySpecial.COLOR_BOMB) {
            for (r in 0 until BOARD_SIZE) {
                for (c in 0 until BOARD_SIZE) {
                    cleared += GridPos(r, c)
                }
            }
        } else {
            val targetType = if (cellA.special == CandySpecial.COLOR_BOMB) cellB.type else cellA.type
            for (r in 0 until BOARD_SIZE) {
                for (c in 0 until BOARD_SIZE) {
                    if (board[r][c].type == targetType) {
                        cleared += GridPos(r, c)
                    }
                }
            }
        }
        val expanded = expandSpecialClears(board, cleared)
        val nextBoard = clearAndDrop(board, expanded, emptyMap())
        return MatchResolutionStep(
            clearedPositions = expanded,
            pointsEarned = expanded.size * 90,
            spawnedSpecials = emptyMap(),
            nextBoardAfterClearAndDrop = nextBoard
        )
    }

    fun resolveMatches(
        board: List<List<CandyCell>>,
        preferredPivotA: GridPos? = null,
        preferredPivotB: GridPos? = null,
        comboMultiplier: Int = 1
    ): MatchResolutionStep? {
        val runs = findMatchRuns(board)
        if (runs.isEmpty()) return null

        val matchedCells = mutableSetOf<GridPos>()
        val spawnedSpecials = mutableMapOf<GridPos, CandyCell>()

        for (run in runs) {
            matchedCells += run.positions
            if (run.positions.size >= 5) {
                val pivot = listOfNotNull(preferredPivotA, preferredPivotB)
                    .firstOrNull { it in run.positions } ?: run.positions[run.positions.size / 2]
                spawnedSpecials[pivot] = CandyCell(
                    id = nextId(),
                    type = run.type,
                    special = CandySpecial.COLOR_BOMB
                )
            } else if (run.positions.size == 4) {
                val pivot = listOfNotNull(preferredPivotA, preferredPivotB)
                    .firstOrNull { it in run.positions } ?: run.positions[1]
                val special = if (run.isHorizontal) CandySpecial.STRIPED_V else CandySpecial.STRIPED_H
                spawnedSpecials[pivot] = CandyCell(
                    id = nextId(),
                    type = run.type,
                    special = special
                )
            }
        }

        val finalCleared = expandSpecialClears(board, matchedCells)
        val effectiveCleared = finalCleared - spawnedSpecials.keys
        val points = (effectiveCleared.size * 60 + spawnedSpecials.size * 120) * comboMultiplier
        val nextBoard = clearAndDrop(board, finalCleared, spawnedSpecials)

        return MatchResolutionStep(
            clearedPositions = finalCleared,
            pointsEarned = points,
            spawnedSpecials = spawnedSpecials,
            nextBoardAfterClearAndDrop = nextBoard
        )
    }

    fun smashSingleCell(board: List<List<CandyCell>>, target: GridPos): MatchResolutionStep {
        val expanded = expandSpecialClears(board, setOf(target))
        val nextBoard = clearAndDrop(board, expanded, emptyMap())
        return MatchResolutionStep(
            clearedPositions = expanded,
            pointsEarned = expanded.size * 80,
            spawnedSpecials = emptyMap(),
            nextBoardAfterClearAndDrop = nextBoard
        )
    }

    fun hasValidMove(board: List<List<CandyCell>>): Boolean {
        for (r in 0 until BOARD_SIZE) {
            for (c in 0 until BOARD_SIZE) {
                if (board[r][c].special == CandySpecial.COLOR_BOMB) return true
                if (c + 1 < BOARD_SIZE) {
                    val swapped = swap(board, GridPos(r, c), GridPos(r, c + 1))
                    if (findMatchRuns(swapped).isNotEmpty()) return true
                }
                if (r + 1 < BOARD_SIZE) {
                    val swapped = swap(board, GridPos(r, c), GridPos(r + 1, c))
                    if (findMatchRuns(swapped).isNotEmpty()) return true
                }
            }
        }
        return false
    }

    fun shuffleBoard(board: List<List<CandyCell>>): List<List<CandyCell>> {
        val types = CandyType.entries
        repeat(15) {
            val flat = board.flatten().shuffled().toMutableList()
            val candidate = List(BOARD_SIZE) { r ->
                List(BOARD_SIZE) { c ->
                    flat[r * BOARD_SIZE + c]
                }
            }
            if (findMatchRuns(candidate).isEmpty() && hasValidMove(candidate)) {
                return candidate
            }
        }
        val fallback = MutableList(BOARD_SIZE) { r ->
            MutableList(BOARD_SIZE) { c ->
                CandyCell(nextId(), types[(r * 2 + c) % types.size])
            }
        }
        // Guarantee a valid move at (0,0)-(0,1) swap
        fallback[0][0] = CandyCell(nextId(), CandyType.STRAWBERRY)
        fallback[0][1] = CandyCell(nextId(), CandyType.BLUEBERRY)
        fallback[0][2] = CandyCell(nextId(), CandyType.STRAWBERRY)
        fallback[0][3] = CandyCell(nextId(), CandyType.STRAWBERRY)
        return fallback.map { it.toList() }
    }

    private data class MatchRun(
        val type: CandyType,
        val positions: List<GridPos>,
        val isHorizontal: Boolean
    )

    private fun findMatchRuns(board: List<List<CandyCell>>): List<MatchRun> {
        val runs = mutableListOf<MatchRun>()
        for (r in 0 until BOARD_SIZE) {
            var c = 0
            while (c < BOARD_SIZE) {
                val type = board[r][c].type
                var end = c + 1
                while (end < BOARD_SIZE && board[r][end].type == type) end++
                if (end - c >= 3) {
                    runs += MatchRun(type, (c until end).map { GridPos(r, it) }, isHorizontal = true)
                }
                c = end
            }
        }
        for (c in 0 until BOARD_SIZE) {
            var r = 0
            while (r < BOARD_SIZE) {
                val type = board[r][c].type
                var end = r + 1
                while (end < BOARD_SIZE && board[end][c].type == type) end++
                if (end - r >= 3) {
                    runs += MatchRun(type, (r until end).map { GridPos(it, c) }, isHorizontal = false)
                }
                r = end
            }
        }
        return runs
    }

    private fun expandSpecialClears(
        board: List<List<CandyCell>>,
        initial: Set<GridPos>
    ): Set<GridPos> {
        val cleared = initial.toMutableSet()
        val queue = ArrayDeque(initial)
        val processed = mutableSetOf<GridPos>()

        while (queue.isNotEmpty()) {
            val pos = queue.removeFirst()
            if (!processed.add(pos)) continue
            when (board[pos.row][pos.col].special) {
                CandySpecial.STRIPED_H -> {
                    for (col in 0 until BOARD_SIZE) {
                        val next = GridPos(pos.row, col)
                        if (cleared.add(next)) queue.add(next)
                    }
                }
                CandySpecial.STRIPED_V -> {
                    for (row in 0 until BOARD_SIZE) {
                        val next = GridPos(row, pos.col)
                        if (cleared.add(next)) queue.add(next)
                    }
                }
                CandySpecial.COLOR_BOMB -> {
                    val targetType = board[pos.row][pos.col].type
                    for (r in 0 until BOARD_SIZE) {
                        for (c in 0 until BOARD_SIZE) {
                            if (board[r][c].type == targetType) {
                                val next = GridPos(r, c)
                                if (cleared.add(next)) queue.add(next)
                            }
                        }
                    }
                }
                CandySpecial.NONE -> Unit
            }
        }
        return cleared
    }

    private fun clearAndDrop(
        board: List<List<CandyCell>>,
        cleared: Set<GridPos>,
        spawnedSpecials: Map<GridPos, CandyCell>
    ): List<List<CandyCell>> {
        val types = CandyType.entries
        val newGrid = Array(BOARD_SIZE) { Array<CandyCell?>(BOARD_SIZE) { null } }

        for (c in 0 until BOARD_SIZE) {
            val surviving = mutableListOf<CandyCell>()
            for (r in BOARD_SIZE - 1 downTo 0) {
                val pos = GridPos(r, c)
                if (pos in spawnedSpecials) {
                    surviving += spawnedSpecials[pos]!!
                } else if (pos !in cleared) {
                    surviving += board[r][c]
                }
            }
            var writeRow = BOARD_SIZE - 1
            for (cell in surviving) {
                newGrid[writeRow][c] = cell
                writeRow--
            }
            while (writeRow >= 0) {
                newGrid[writeRow][c] = CandyCell(nextId(), types[Random.nextInt(types.size)])
                writeRow--
            }
        }
        return newGrid.map { row -> row.map { it!! } }
    }
}
