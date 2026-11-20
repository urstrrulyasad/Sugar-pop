package com.example.game

import kotlin.math.abs
import kotlin.random.Random

enum class CandyType {
    STRAWBERRY,
    LEMON,
    MINT,
    BLUEBERRY,
    GRAPE,
    TANGERINE
}

enum class CandySpecial {
    NONE,
    STRIPED_H,
    STRIPED_V,
    COLOR_BOMB
}

data class CandyCell(
    val id: Long,
    val type: CandyType,
    val special: CandySpecial = CandySpecial.NONE
)

data class GridPos(val row: Int, val col: Int)

data class CandyLevelConfig(
    val level: Int,
    val name: String,
    val targetScore: Int,
    val maxMoves: Int
)

data class MatchResolutionStep(
    val clearedPositions: Set<GridPos>,
    val spawnedSpecials: Map<GridPos, CandyCell>,
    val pointsEarned: Int,
    val nextBoardAfterClearAndDrop: List<List<CandyCell>>
)

object CandyGameEngine {
    const val BOARD_SIZE = 8
    private var nextIdCounter = 1L

    private fun nextId(): Long = nextIdCounter++

    fun levelConfig(level: Int): CandyLevelConfig {
        val safeLevel = level.coerceAtLeast(1)
        val names = listOf(
            "Sugar Meadow",
            "Caramel Falls",
            "Berry Citadel",
            "Mint Glacier",
            "Starlight Bakery",
            "Tangerine Grove",
            "Velvet Kingdom"
        )
        val name = names[(safeLevel - 1) % names.size]
        val targetScore = 900 + (safeLevel - 1) * 750
        val moves = (20 + ((safeLevel - 1) % 3) * 2).coerceAtMost(26)
        return CandyLevelConfig(
            level = safeLevel,
            name = name,
            targetScore = targetScore,
            maxMoves = moves
        )
    }

    fun createInitialBoard(): List<List<CandyCell>> {
        val types = CandyType.entries
        val grid = MutableList(BOARD_SIZE) {
            MutableList(BOARD_SIZE) {
                CandyCell(nextId(), types[Random.nextInt(types.size)])
            }
        }
        // Eliminate initial 3-in-a-row matches
        for (r in 0 until BOARD_SIZE) {
            for (c in 0 until BOARD_SIZE) {
                var candidate = grid[r][c].type
                while (
                    (c >= 2 && grid[r][c - 1].type == candidate && grid[r][c - 2].type == candidate) ||
                    (r >= 2 && grid[r - 1][c].type == candidate && grid[r - 2][c].type == candidate)
                ) {
                    candidate = types[Random.nextInt(types.size)]
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
        val temp = mutable[a.row][a.col]
        mutable[a.row][a.col] = mutable[b.row][b.col]
        mutable[b.row][b.col] = temp
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
        val toClear = mutableSetOf(a, b)

        if (cellA.special == CandySpecial.COLOR_BOMB && cellB.special == CandySpecial.COLOR_BOMB) {
            for (r in 0 until BOARD_SIZE) {
                for (c in 0 until BOARD_SIZE) {
                    toClear += GridPos(r, c)
                }
            }
        } else {
            val targetType = if (cellA.special == CandySpecial.COLOR_BOMB) cellB.type else cellA.type
            for (r in 0 until BOARD_SIZE) {
                for (c in 0 until BOARD_SIZE) {
                    if (board[r][c].type == targetType) {
                        toClear += GridPos(r, c)
                    }
                }
            }
        }

        val expanded = expandSpecials(board, toClear)
        val dropped = applyGravityAndRefill(board, expanded, emptyMap())
        return MatchResolutionStep(
            clearedPositions = expanded,
            spawnedSpecials = emptyMap(),
            pointsEarned = expanded.size * 45,
            nextBoardAfterClearAndDrop = dropped
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

        val allMatched = mutableSetOf<GridPos>()
        val spawnedSpecials = mutableMapOf<GridPos, CandyCell>()

        for (run in runs) {
            allMatched.addAll(run.positions)
            if (run.positions.size >= 5) {
                val pivot = run.positions.firstOrNull { it == preferredPivotA || it == preferredPivotB }
                    ?: run.positions[run.positions.size / 2]
                spawnedSpecials[pivot] = CandyCell(
                    id = nextId(),
                    type = run.type,
                    special = CandySpecial.COLOR_BOMB
                )
            } else if (run.positions.size == 4) {
                val pivot = run.positions.firstOrNull { it == preferredPivotA || it == preferredPivotB }
                    ?: run.positions[1]
                val special = if (run.isHorizontal) CandySpecial.STRIPED_V else CandySpecial.STRIPED_H
                spawnedSpecials[pivot] = CandyCell(
                    id = nextId(),
                    type = run.type,
                    special = special
                )
            }
        }

        // Don't destroy a newly spawned special in the same tick
        val clearPositions = expandSpecials(board, allMatched) - spawnedSpecials.keys
        val dropped = applyGravityAndRefill(board, clearPositions, spawnedSpecials)
        val points = (clearPositions.size * 30 + spawnedSpecials.size * 60) * comboMultiplier

        return MatchResolutionStep(
            clearedPositions = clearPositions,
            spawnedSpecials = spawnedSpecials,
            pointsEarned = points,
            nextBoardAfterClearAndDrop = dropped
        )
    }

    fun smashSingleCell(board: List<List<CandyCell>>, target: GridPos): MatchResolutionStep {
        val expanded = expandSpecials(board, setOf(target))
        val dropped = applyGravityAndRefill(board, expanded, emptyMap())
        return MatchResolutionStep(
            clearedPositions = expanded,
            spawnedSpecials = emptyMap(),
            pointsEarned = expanded.size * 35,
            nextBoardAfterClearAndDrop = dropped
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
        val flat = board.flatten().toMutableList()
        repeat(8) {
            flat.shuffle()
            val candidate = flat.chunked(BOARD_SIZE)
            if (findMatchRuns(candidate).isEmpty() && hasValidMove(candidate)) {
                return candidate
            }
        }
        return createInitialBoard()
    }

    private data class MatchRun(
        val type: CandyType,
        val positions: List<GridPos>,
        val isHorizontal: Boolean
    )

    private fun findMatchRuns(board: List<List<CandyCell>>): List<MatchRun> {
        val runs = mutableListOf<MatchRun>()

        // Horizontal runs
        for (r in 0 until BOARD_SIZE) {
            var c = 0
            while (c < BOARD_SIZE) {
                val startType = board[r][c].type
                val positions = mutableListOf(GridPos(r, c))
                var nc = c + 1
                while (nc < BOARD_SIZE && board[r][nc].type == startType) {
                    positions += GridPos(r, nc)
                    nc++
                }
                if (positions.size >= 3) {
                    runs += MatchRun(startType, positions, isHorizontal = true)
                }
                c = nc
            }
        }

        // Vertical runs
        for (c in 0 until BOARD_SIZE) {
            var r = 0
            while (r < BOARD_SIZE) {
                val startType = board[r][c].type
                val positions = mutableListOf(GridPos(r, c))
                var nr = r + 1
                while (nr < BOARD_SIZE && board[nr][c].type == startType) {
                    positions += GridPos(nr, c)
                    nr++
                }
                if (positions.size >= 3) {
                    runs += MatchRun(startType, positions, isHorizontal = false)
                }
                r = nr
            }
        }

        return runs
    }

    private fun expandSpecials(
        board: List<List<CandyCell>>,
        initial: Set<GridPos>
    ): Set<GridPos> {
        val result = initial.toMutableSet()
        val queue = ArrayDeque(initial)
        val processed = mutableSetOf<GridPos>()

        while (queue.isNotEmpty()) {
            val pos = queue.removeFirst()
            if (!processed.add(pos)) continue
            val cell = board[pos.row][pos.col]
            when (cell.special) {
                CandySpecial.STRIPED_H -> {
                    for (c in 0 until BOARD_SIZE) {
                        val p = GridPos(pos.row, c)
                        if (result.add(p)) queue.addLast(p)
                    }
                }
                CandySpecial.STRIPED_V -> {
                    for (r in 0 until BOARD_SIZE) {
                        val p = GridPos(r, pos.col)
                        if (result.add(p)) queue.addLast(p)
                    }
                }
                CandySpecial.COLOR_BOMB -> {
                    val targetType = cell.type
                    for (r in 0 until BOARD_SIZE) {
                        for (c in 0 until BOARD_SIZE) {
                            if (board[r][c].type == targetType) {
                                val p = GridPos(r, c)
                                if (result.add(p)) queue.addLast(p)
                            }
                        }
                    }
                }
                CandySpecial.NONE -> Unit
            }
        }
        return result
    }

    private fun applyGravityAndRefill(
        board: List<List<CandyCell>>,
        cleared: Set<GridPos>,
        spawnedSpecials: Map<GridPos, CandyCell>
    ): List<List<CandyCell>> {
        val mutable = MutableList(BOARD_SIZE) { r ->
            MutableList(BOARD_SIZE) { c -> board[r][c] }
        }
        // Apply spawned specials before gravity
        for ((pos, specialCell) in spawnedSpecials) {
            mutable[pos.row][pos.col] = specialCell
        }

        val types = CandyType.entries
        for (c in 0 until BOARD_SIZE) {
            val surviving = mutableListOf<CandyCell>()
            for (r in (BOARD_SIZE - 1) downTo 0) {
                val pos = GridPos(r, c)
                if (pos !in cleared) {
                    surviving += mutable[r][c]
                }
            }
            val missingCount = BOARD_SIZE - surviving.size
            val columnCells = mutableListOf<CandyCell>()
            repeat(missingCount) {
                columnCells += CandyCell(nextId(), types[Random.nextInt(types.size)])
            }
            columnCells.addAll(surviving.reversed())
            for (r in 0 until BOARD_SIZE) {
                mutable[r][c] = columnCells[r]
            }
        }
        return mutable.map { it.toList() }
    }
}
