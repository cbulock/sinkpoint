package com.cbulock.sinkpoint.game

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Public-information rollout policy. The assumed fleet is used only to reveal shot results. */
internal class AttackSimulation(private val config: GameConfig) {
    private val cellMasks = Array(config.size * config.size) { HardAi.cellMask(it) }
    private data class Option(val cells: IntArray, val mask: HardAi.Mask, val border: HardAi.Mask)
    private val options = config.lengths.indices.map { id ->
        (0 until config.size * config.size).flatMap { start -> listOf(true, false).mapNotNull { horizontal ->
            val cells = Rules.cells(config, Piece(id, start, horizontal))
            if (cells.isEmpty()) null else Option(cells.toIntArray(), HardAi.mask(cells), HardAi.border(config, cells))
        } }
    }

    internal class State private constructor(
        var tried: HardAi.Mask, var hits: HardAi.Mask, var destroyed: HardAi.Mask,
        var forbidden: HardAi.Mask, val alive: BooleanArray,
    ) {
        constructor(knowledge: Knowledge) : this(
            HardAi.mask(knowledge.tried), HardAi.mask(knowledge.hits),
            HardAi.mask(knowledge.sunk.flatMap { it.cells }),
            HardAi.border(knowledge.config, knowledge.sunk.flatMap { it.cells }),
            BooleanArray(knowledge.config.lengths.size) { it in knowledge.remaining },
        )
        fun copy() = State(tried, hits, destroyed, forbidden, alive.copyOf())
    }

    private fun HardAi.Mask.count() = java.lang.Long.bitCount(a) + java.lang.Long.bitCount(b) + java.lang.Long.bitCount(c)

    fun scores(state: State): IntArray {
        val scores = IntArray(config.size * config.size)
        val forbidden = state.tried.without(state.hits) or state.forbidden
        val unresolved = state.hits.without(state.destroyed)
        for (id in options.indices) if (state.alive[id]) for (p in options[id]) {
            if (p.mask.intersects(forbidden)) continue
            val damage = p.mask.count() - p.mask.without(unresolved).count()
            val weight = if (damage > 0) 100 * damage * damage else 1
            for (cell in p.cells) if (!state.tried.intersects(cellMasks[cell])) scores[cell] += weight
        }
        return scores
    }

    fun targets(state: State, count: Int, random: GameRandom, parity: Boolean = false): List<Int> {
        val scores = scores(state)
        val candidates = scores.indices.filter { !state.tried.intersects(cellMasks[it]) }.toMutableList()
        val hunting = state.hits.without(state.destroyed).empty()
        val chosen = ArrayList<Int>(count)
        repeat(minOf(count, candidates.size)) {
            val eligible = if (parity && hunting) candidates.filter { (it / config.size + it % config.size) % 2 == 0 }.ifEmpty { candidates } else candidates
            val best = if (parity && hunting) 0 else eligible.maxOf { scores[it] }
            val tied = if (parity && hunting) eligible else eligible.filter { scores[it] == best }
            val cell = tied[random.nextInt(tied.size)]
            chosen.add(cell); candidates.remove(cell)
        }
        return chosen
    }

    fun observe(state: State, fleet: List<HardAi.Placement>, targets: List<Int>) {
        val shots = HardAi.mask(targets)
        state.tried = state.tried or shots
        for (ship in fleet) {
            for (cell in targets) if (ship.cells.intersects(cellMasks[cell])) state.hits = state.hits or cellMasks[cell]
            if (state.alive[ship.piece.id] && state.tried.contains(ship.cells)) {
                state.alive[ship.piece.id] = false
                state.destroyed = state.destroyed or ship.cells
                state.forbidden = state.forbidden or ship.blocked
            }
        }
    }

    suspend fun finish(initial: State, fleet: List<HardAi.Placement>, first: List<Int>, quota: Int, random: GameRandom, parity: Boolean = false): Int {
        val context = currentCoroutineContext()
        val state = initial.copy()
        var fired = first.size
        observe(state, fleet, first)
        while (state.alive.any { it }) {
            context.ensureActive()
            val shots = targets(state, quota, random, parity)
            check(shots.isNotEmpty())
            observe(state, fleet, shots)
            fired += shots.size
        }
        return fired
    }

    companion object {
        fun placements(config: GameConfig, fleet: Fleet) = fleet.pieces.map { piece ->
            val cells = Rules.cells(config, piece)
            HardAi.Placement(piece, HardAi.mask(cells), HardAi.border(config, cells), cells.toIntArray(), cells.size)
        }
    }
}
