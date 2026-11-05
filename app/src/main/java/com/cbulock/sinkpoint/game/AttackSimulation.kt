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

    enum class Style { PROBABILITY, PARITY, ENDPOINT }

    private class Scores(size: Int) {
        val coverage = IntArray(size)
        val pursuit = IntArray(size)
        val priority = IntArray(size)
        val line = IntArray(size)
        fun combined() = IntArray(coverage.size) { priority[it] * 1_000_000 + pursuit[it] + coverage[it] }
    }

    private fun analyze(state: State): Scores {
        val scores = Scores(cellMasks.size)
        val forbidden = state.tried.without(state.hits) or state.forbidden
        val unresolved = state.hits.without(state.destroyed)
        for (id in options.indices) if (state.alive[id]) for (p in options[id]) {
            if (p.mask.intersects(forbidden) || state.tried.contains(p.mask)) continue
            // A no-touch ship may not leave a separate public hit in its border.
            if (!config.touching && p.border.intersects(unresolved.without(p.mask))) continue
            val damage = p.mask.count() - p.mask.without(unresolved).count()
            for (index in p.cells.indices) {
                val cell = p.cells[index]
                if (state.tried.intersects(cellMasks[cell])) continue
                scores.coverage[cell]++
                if (damage == 0) continue
                val before = index > 0 && unresolved.intersects(cellMasks[p.cells[index - 1]])
                val after = index < p.cells.lastIndex && unresolved.intersects(cellMasks[p.cells[index + 1]])
                val priority = when {
                    before && after -> 3 // Fill a gap before extending an already connected contact.
                    (before || after) && damage >= 2 -> 2
                    before || after -> 1
                    else -> 0 // Do not jump over an unknown square along a damaged ship.
                }
                scores.priority[cell] = maxOf(scores.priority[cell], priority)
                if (priority > 0) scores.pursuit[cell] += 100 * damage
                scores.line[cell] = maxOf(scores.line[cell], if (priority > 0) damage else 0)
            }
        }
        return scores
    }

    private fun defensiveTargets(
        state: State, count: Int, random: GameRandom,
        style: Style,
    ): List<Int> {
        val analysis = analyze(state)
        val scores = analysis.combined()
        val candidates = scores.indices.filter { !state.tried.intersects(cellMasks[it]) }.toMutableList()
        val hunting = state.hits.without(state.destroyed).empty()
        val stride = config.lengths.indices.filter { state.alive[it] }.minOfOrNull { config.lengths[it] } ?: 2
        val chosen = ArrayList<Int>(minOf(count, candidates.size))
        repeat(minOf(count, candidates.size)) {
            // Parity applies only inside feasible ship coverage, including after sink borders.
            val feasible = candidates.filter { analysis.coverage[it] > 0 }.ifEmpty { candidates }
            val eligible = if (style == Style.PARITY && hunting) {
                feasible.filter { (it / config.size + it % config.size) % stride == 0 }.ifEmpty { feasible }
            } else feasible
            fun value(cell: Int): Double = when {
                style == Style.PARITY && hunting -> 0.0
                style == Style.ENDPOINT && !hunting -> analysis.priority[cell] * 1_000_000.0 + analysis.line[cell] * 10_000.0 + analysis.pursuit[cell] + analysis.coverage[cell]
                else -> scores[cell].toDouble()
            }
            val best = eligible.maxOf { value(it) }
            val tied = eligible.filter { value(it) == best }
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

    suspend fun finish(initial: State, fleet: List<HardAi.Placement>, first: List<Int>, quota: Int, random: GameRandom, parity: Boolean = false, defenseStyle: Style? = null): Int {
        val context = currentCoroutineContext()
        val state = initial.copy()
        var fired = first.size
        observe(state, fleet, first)
        while (state.alive.any { it }) {
            context.ensureActive()
            val shots = if (defenseStyle == null) targets(state, quota, random, parity)
                else defensiveTargets(state, quota, random, defenseStyle)
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
