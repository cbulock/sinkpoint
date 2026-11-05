package com.cbulock.sinkpoint.game

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs

/** Select resilient, varied layouts using only the public configuration. */
internal object DefensivePlacement {
    private enum class LayoutStyle { SCATTERED, EDGE, CENTER, CLUSTERED, PARALLEL, MIXED }
    private data class Option(val piece: Piece, val cells: List<Int>, val occupied: HardAi.Mask, val blocked: HardAi.Mask)

    suspend fun place(config: GameConfig, random: GameRandom): Fleet? {
        val context = currentCoroutineContext()
        context.ensureActive()
        val first = Rules.place(config, random) ?: return null
        // Exact feasibility is paid only once. Other candidates use fixed-work greedy construction
        // or legal board symmetries, including unusually dense custom fleets.
        val dense = config.lengths.size > 6 || config.lengths.sum() * 3 > config.size * config.size
        val limit = if (dense) 6 else 14
        val candidates = mutableListOf(first)
        val options = config.lengths.indices.associateWith { id ->
            (0 until config.size * config.size).flatMap { start -> listOf(true, false).mapNotNull { horizontal ->
                val piece = Piece(id, start, horizontal)
                val cells = Rules.cells(config, piece)
                if (cells.isEmpty()) null else Option(piece, cells, HardAi.mask(cells), HardAi.border(config, cells))
            } }
        }
        repeat(if (dense) 3 else 10) { trial ->
            context.ensureActive()
            greedy(config, options, LayoutStyle.entries[trial % LayoutStyle.entries.size], random)?.let { candidates.add(it) }
        }
        for (symmetry in random.shuffled((1..7).toList())) {
            if (candidates.size >= limit) break
            candidates.add(transform(config, first, symmetry))
        }
        val unique = candidates.distinctBy { signature(config, it) }.take(limit)
        val simulator = AttackSimulation(config)
        val state = AttackSimulation.State(Knowledge(config, emptyList()))
        val evaluationSeed = random.state
        val quota = if (config.rule == TurnRule.SALVO) config.lengths.size else 1
        // Run the probability hunter twice with independent tie-break streams.
        val trials = listOf(AttackSimulation.Style.PROBABILITY, AttackSimulation.Style.PARITY,
            AttackSimulation.Style.ENDPOINT, AttackSimulation.Style.PROBABILITY)
        val scored = unique.map { fleet ->
            context.ensureActive()
            val ships = AttackSimulation.placements(config, fleet)
            val costs = trials.mapIndexed { index, style ->
                simulator.finish(state, ships, emptyList(), quota, GameRandom(evaluationSeed + index * 7919L), defenseStyle = style)
            }
            // A brittle layout must not win just by fooling one weak hunter.
            fleet to (costs.average() + costs.min() * 0.5)
        }.sortedByDescending { it.second }
        val best = scored.first().second
        // Retain spatial variety among similarly strong candidates rather than a fixed best pattern.
        val finalists = scored.filter { it.second >= best * 0.94 }.take(4)
        context.ensureActive()
        return finalists[random.nextInt(finalists.size)].first
    }

    private suspend fun greedy(
        config: GameConfig, options: Map<Int, List<Option>>, style: LayoutStyle, random: GameRandom,
    ): Fleet? {
        var blocked = HardAi.Mask()
        val placed = mutableListOf<Option>()
        val preferredHorizontal = random.nextInt(2) == 0
        val anchorRow = random.nextInt(config.size)
        val anchorCol = random.nextInt(config.size)
        for (id in config.lengths.indices.sortedByDescending { config.lengths[it] }) {
            currentCoroutineContext().ensureActive()
            val legal = options.getValue(id).filter { !it.occupied.intersects(blocked) }
            if (legal.isEmpty()) return null
            fun score(option: Option): Double {
                val row = option.cells.map { it / config.size }.average()
                val col = option.cells.map { it % config.size }.average()
                val center = (config.size - 1) / 2.0
                val edge = option.cells.map { minOf(it / config.size, it % config.size, config.size - 1 - it / config.size, config.size - 1 - it % config.size) }.average()
                val separation = placed.minOfOrNull { other ->
                    abs(row - other.cells.map { it / config.size }.average()) + abs(col - other.cells.map { it % config.size }.average())
                } ?: 0.0
                val bias = when (style) {
                    LayoutStyle.SCATTERED -> separation
                    LayoutStyle.EDGE -> -2.0 * edge
                    LayoutStyle.CENTER -> -abs(row - center) - abs(col - center)
                    LayoutStyle.CLUSTERED -> -abs(row - anchorRow) - abs(col - anchorCol)
                    LayoutStyle.PARALLEL -> (if (option.piece.horizontal == preferredHorizontal) config.size.toDouble() else 0.0) + separation * 0.5
                    LayoutStyle.MIXED -> (if (option.piece.horizontal == (placed.size % 2 == 0)) 2.0 else 0.0) + separation * 0.5
                }
                return bias + random.nextInt(2000) / 1000.0
            }
            val selected = legal.maxBy { score(it) }
            placed.add(selected)
            blocked = blocked or selected.blocked
        }
        return Fleet(placed.map { it.piece }.sortedBy { it.id })
    }

    private fun transform(config: GameConfig, fleet: Fleet, symmetry: Int): Fleet = Fleet(fleet.pieces.map { piece ->
        val cells = Rules.cells(config, piece).map { cell ->
            var row = cell / config.size
            var col = cell % config.size
            if (symmetry >= 4) col = config.size - 1 - col
            repeat(symmetry % 4) {
                val nextRow = col
                col = config.size - 1 - row
                row = nextRow
            }
            row * config.size + col
        }
        Piece(piece.id, cells.min(), cells.all { it / config.size == cells.first() / config.size })
    })

    private fun signature(config: GameConfig, fleet: Fleet): String = fleet.pieces
        .map { Rules.cells(config, it).sorted().joinToString(",") }.sorted().joinToString(";")
}
