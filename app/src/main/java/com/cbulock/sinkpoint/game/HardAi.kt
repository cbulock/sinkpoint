package com.cbulock.sinkpoint.game

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.ln
import kotlin.math.sqrt

/** Bounded, deterministic planning using only the opponent's public shot history. */
internal object HardAi {
    private const val SAMPLE_COUNT = 256
    private const val SEARCH_BUDGET = 60_000
    private const val SHORTLIST = 12
    private const val INFORMATION_VALUE = 0.12
    private const val LOOKAHEAD_DISCOUNT = 0.4
    private const val SEARCH_LOOKAHEAD_DISCOUNT = 0.9
    private const val PURSUIT_PRIOR_WEIGHT = 0.8

    internal data class Mask(val a: Long = 0, val b: Long = 0, val c: Long = 0) {
        infix fun or(other: Mask) = Mask(a or other.a, b or other.b, c or other.c)
        fun intersects(other: Mask) = (a and other.a) != 0L || (b and other.b) != 0L || (c and other.c) != 0L
        fun contains(other: Mask) = (a and other.a) == other.a && (b and other.b) == other.b && (c and other.c) == other.c
        fun without(other: Mask) = Mask(a and other.a.inv(), b and other.b.inv(), c and other.c.inv())
        fun empty() = a == 0L && b == 0L && c == 0L
    }

    internal data class Placement(val piece: Piece, val cells: Mask, val blocked: Mask, val unknown: IntArray, val length: Int)
    internal class Hypothesis(val ships: List<Placement>, size: Int) {
        val owners = IntArray(size * size) { -1 }.also { owners ->
            ships.forEachIndexed { index, ship -> ship.unknown.forEach { owners[it] = index } }
        }
    }
    internal data class Belief(val fleets: List<Hypothesis>, val exact: Boolean, val values: DoubleArray = doubleArrayOf(), val searchPrior: DoubleArray = doubleArrayOf())

    private fun hitValue(ship: Placement): Double {
        val damage = ship.length - ship.unknown.size
        return 1.0 + 8.0 * damage * damage
    }

    internal fun cellMask(cell: Int): Mask = when {
        cell < 64 -> Mask(a = 1L shl cell)
        cell < 128 -> Mask(b = 1L shl (cell - 64))
        else -> Mask(c = 1L shl (cell - 128))
    }

    internal fun mask(cells: Iterable<Int>): Mask {
        var a = 0L; var b = 0L; var c = 0L
        for (cell in cells) when {
            cell < 64 -> a = a or (1L shl cell)
            cell < 128 -> b = b or (1L shl (cell - 64))
            else -> c = c or (1L shl (cell - 128))
        }
        return Mask(a, b, c)
    }

    internal fun border(config: GameConfig, cells: List<Int>): Mask = if (config.touching) mask(cells) else mask(
        cells.flatMap { cell -> (-1..1).flatMap { dr -> (-1..1).mapNotNull { dc ->
            val row = cell / config.size + dr
            val col = cell % config.size + dc
            if (row in 0 until config.size && col in 0 until config.size) row * config.size + col else null
        } } }
    )

    suspend fun targets(knowledge: Knowledge, count: Int, random: GameRandom, searchBudget: Int = SEARCH_BUDGET): List<Int> {
        require(count in 1..(knowledge.config.size * knowledge.config.size - knowledge.tried.size))
        currentCoroutineContext().ensureActive()
        // Sampling has its own reproducible stream; spending more inference work
        // must not perturb the saved shot tie-break sequence.
        val belief = infer(knowledge, GameRandom(random.state xor -7046029254386353131L), searchBudget)
        // A pathological custom position may exhaust the deterministic search budget.
        // Keep playing legally; an incomplete search never proves a cell empty.
        if (belief.fleets.isEmpty()) return FleetAi.strategy(Difficulty.NORMAL).targets(knowledge, count, random)
        val simulationRandom = GameRandom(random.state xor 0x524f4c4c4f5554L)
        val baseline = plan(knowledge, belief, count, random)
        if (count == 1) ExactEndgame.target(belief, preferred = baseline.single())?.let { return listOf(it) }
        return RolloutAttack.targets(knowledge, belief, baseline, simulationRandom)
    }

    internal suspend fun infer(knowledge: Knowledge, random: GameRandom, searchBudget: Int = SEARCH_BUDGET): Belief {
        currentCoroutineContext().ensureActive()
        val config = knowledge.config
        val hits = mask(knowledge.unresolved)
        val forbidden = mask(knowledge.tried - knowledge.hits) or border(config, knowledge.sunk.flatMap { it.cells })
        val choices = knowledge.remaining.map { id ->
            currentCoroutineContext().ensureActive()
            buildList {
                for (start in 0 until config.size * config.size) for (horizontal in listOf(true, false)) {
                    val piece = Piece(id, start, horizontal)
                    val cells = Rules.cells(config, piece)
                    if (cells.isEmpty()) continue
                    val occupied = mask(cells)
                    if (occupied.intersects(forbidden)) continue
                    val unknown = cells.filter { it !in knowledge.tried }.toIntArray()
                    if (unknown.isEmpty()) continue // It would already have been announced sunk.
                    val blocked = border(config, cells)
                    if (!config.touching && blocked.intersects(hits.without(occupied))) continue
                    add(Placement(piece, occupied, blocked, unknown, cells.size))
                }
            }
        }
        if (choices.isEmpty() || choices.any { it.isEmpty() }) return Belief(emptyList(), false)
        val selected = arrayOfNulls<Placement>(choices.size)
        val fleets = mutableListOf<Hypothesis>()
        var work = 0
        val context = currentCoroutineContext()
        val possibleCount = choices.fold(1L) { total, options -> minOf(4097L, total * options.size) }

        // Small posteriors are enumerated exactly. Larger ones use a Markov chain
        // of complete legal fleets, never independent, overlapping ship guesses.
        if ((possibleCount <= 4096 || choices.size <= 2) && searchBudget > 0) {
            var exhausted = false
            suspend fun enumerate(depth: Int, occupied: Mask) {
                if (++work % 128 == 0) context.ensureActive()
                if (work > searchBudget || fleets.size >= SAMPLE_COUNT) { exhausted = true; return }
                if (depth == choices.size) {
                    if (occupied.contains(hits)) fleets.add(Hypothesis(selected.map { it!! }, config.size))
                    return
                }
                for (candidate in choices[depth]) if (!candidate.blocked.intersects(occupied)) {
                    if (exhausted) return
                    selected[depth] = candidate
                    enumerate(depth + 1, occupied or candidate.cells)
                }
            }
            enumerate(0, Mask())
            if (!exhausted) {
                val values = DoubleArray(config.size * config.size)
                if (fleets.isNotEmpty()) for (fleet in fleets) for (ship in fleet.ships) {
                    for (cell in ship.unknown) values[cell] += hitValue(ship) / fleets.size
                }
                return Belief(fleets, true, values)
            }
            fleets.clear(); selected.fill(null); work = 0
        }

        data class Option(val ship: Int, val placement: Placement)
        val hitOptions = knowledge.unresolved.map { cell ->
            val cellMask = mask(listOf(cell))
            cellMask to choices.flatMapIndexed { ship, candidates ->
                candidates.filter { it.cells.contains(cellMask) }.map { Option(ship, it) }
            }
        }.sortedBy { it.second.size }

        fun solve(occupied: Mask, depth: Int): Boolean {
            if (++work % 128 == 0) context.ensureActive()
            if (work > searchBudget) return false
            if (depth == choices.size) return occupied.contains(hits)
            val uncovered = hitOptions.firstOrNull { !occupied.contains(it.first) }
            val options = if (uncovered != null) {
                uncovered.second.filter { selected[it.ship] == null && !it.placement.blocked.intersects(occupied) }
            } else {
                val ship = choices.indices.filter { selected[it] == null }.minBy { choices[it].size }
                choices[ship].filter { !it.blocked.intersects(occupied) }.map { Option(ship, it) }
            }
            for (option in random.shuffled(options)) {
                selected[option.ship] = option.placement
                if (solve(occupied or option.placement.cells, depth + 1)) return true
                selected[option.ship] = null
                if (work > searchBudget) break
            }
            return false
        }
        if (!solve(Mask(), 0)) return Belief(emptyList(), false)

        fun resampleOne(ship: Int) {
            var others = Mask()
            selected.forEachIndexed { index, placement -> if (index != ship) others = others or placement!!.cells }
            val required = hits.without(others)
            var seen = 0
            for (candidate in choices[ship]) {
                if (!candidate.blocked.intersects(others) && candidate.cells.contains(required)) {
                    if (random.nextInt(++seen) == 0) selected[ship] = candidate
                }
            }
        }

        fun resamplePair(first: Int, second: Int) {
            var others = Mask()
            selected.forEachIndexed { index, placement -> if (index != first && index != second) others = others or placement!!.cells }
            val required = hits.without(others)
            val a = choices[first].filter { !it.blocked.intersects(others) }
            val b = choices[second].filter { !it.blocked.intersects(others) }
            if (!required.empty() || a.size * b.size <= 4096) {
                var seen = 0
                for (one in a) {
                    val missing = required.without(one.cells)
                    for (two in b) {
                        if (two.cells.contains(missing) && !one.blocked.intersects(two.cells)) {
                            if (random.nextInt(++seen) == 0) { selected[first] = one; selected[second] = two }
                        }
                    }
                }
            } else {
                // Symmetric proposals preserve the same conditional distribution.
                repeat(16) {
                    val one = a[random.nextInt(a.size)]; val two = b[random.nextInt(b.size)]
                    if (!one.blocked.intersects(two.cells) && (one.cells or two.cells).contains(required)) {
                        selected[first] = one; selected[second] = two
                    }
                }
            }
        }

        val values = DoubleArray(config.size * config.size)
        repeat(96 + SAMPLE_COUNT * 4) { step ->
            if (step % 16 == 0) context.ensureActive()
            if (step > 96 && (step - 96) % 128 == 0) {
                // Independent starts prevent adjacent-hit assignments from keeping
                // the sampler trapped in a single explanation of a touching fleet.
                val previous = selected.copyOf()
                selected.fill(null)
                if (!solve(Mask(), 0)) previous.copyInto(selected)
                repeat(32) { warmup ->
                    val one = random.nextInt(choices.size)
                    if (choices.size > 1 && warmup % 2 == 0) {
                        resamplePair(one, (one + 1 + random.nextInt(choices.size - 1)) % choices.size)
                    } else resampleOne(one)
                }
            }
            val first = random.nextInt(choices.size)
            if (choices.size > 1 && step % 4 == 0) {
                val second = (first + 1 + random.nextInt(choices.size - 1)) % choices.size
                resamplePair(first, second)
            } else resampleOne(first)
            if (step >= 96 && step % 4 == 3) {
                fleets.add(Hypothesis(selected.map { it!! }, config.size))
                // Rao-Blackwellize the shot estimates: average every legal location
                // of each ship conditional on the others, not just its sampled cell.
                for (ship in choices.indices) {
                    var others = Mask()
                    selected.forEachIndexed { index, p -> if (index != ship) others = others or p!!.cells }
                    val required = hits.without(others)
                    val candidates = choices[ship].filter { !it.blocked.intersects(others) && it.cells.contains(required) }
                    for (candidate in candidates) {
                        val value = hitValue(candidate) / (candidates.size * SAMPLE_COUNT)
                        for (cell in candidate.unknown) values[cell] += value
                    }
                }
            }
        }
        val prior = DoubleArray(config.size * config.size)
        for (candidates in choices) for (candidate in candidates) {
            val damage = candidate.length - candidate.unknown.size
            val weight = if (damage > 0) 100.0 * damage * damage else 1.0
            for (cell in candidate.unknown) prior[cell] += weight
        }
        val scale = values.sum() / prior.sum().coerceAtLeast(1.0)
        for (cell in prior.indices) prior[cell] *= scale
        return Belief(fleets, false, values, prior)
    }

    private data class Observation(val hits: Mask, val sunk: List<Int>)
    private class Branch(val fleets: MutableList<Hypothesis> = mutableListOf())
    private data class Forecast(val immediate: Double, val information: Double, val branches: Collection<Branch>)

    private fun forecast(belief: Belief, volley: List<Int>, values: DoubleArray): Forecast {
        val fleets = belief.fleets
        val branches = linkedMapOf<Observation, Branch>()
        var immediate = 0.0
        for (fleet in fleets) {
            val hitsByShip = IntArray(fleet.ships.size)
            var a = 0L; var b = 0L; var c = 0L
            volley.forEach { cell ->
                val owner = fleet.owners[cell]
                if (owner >= 0) {
                    when {
                        cell < 64 -> a = a or (1L shl cell)
                        cell < 128 -> b = b or (1L shl (cell - 64))
                        else -> c = c or (1L shl (cell - 128))
                    }
                    hitsByShip[owner]++
                }
            }
            val sunk = mutableListOf<Int>()
            fleet.ships.forEachIndexed { index, ship ->
                val hits = hitsByShip[index]
                if (hits == ship.unknown.size) {
                    immediate += 0.25 * hitValue(ship)
                    // Exact sunk cells matter; exchanging labels of equal-length
                    // ships does not provide useful information about future shots.
                    sunk.add(ship.length * 512 + ship.piece.start * 2 + if (ship.piece.horizontal) 0 else 1)
                }
            }
            if (sunk.size == fleet.ships.size) immediate += 4.0
            branches.getOrPut(Observation(Mask(a, b, c), sunk.sorted())) { Branch() }.fleets.add(fleet)
        }
        val total = fleets.size.toDouble()
        val information = branches.values.sumOf { branch ->
            val p = branch.fleets.size / total
            -p * ln(p) / ln(2.0)
        }
        return Forecast(volley.sumOf { values[it] } + immediate / total, information, branches.values)
    }

    /** Evaluate joint volley outcomes, then the best reply after each revealed outcome. */
    internal suspend fun plan(knowledge: Knowledge, belief: Belief, count: Int, random: GameRandom): List<Int> {
        val size = knowledge.config.size * knowledge.config.size
        val available = (0 until size).filter { it !in knowledge.tried }.toMutableList()
        val selected = mutableListOf<Int>()
        val priorWeight = when {
            belief.exact -> 0.0
            knowledge.unresolved.isEmpty() -> 1.0
            knowledge.config.touching -> PURSUIT_PRIOR_WEIGHT
            else -> 0.0
        }
        val values = if (priorWeight > 0.0) DoubleArray(size) {
            priorWeight * belief.searchPrior[it] + (1.0 - priorWeight) * belief.values[it]
        } else belief.values
        val hunting = count == 1 && knowledge.unresolved.isEmpty()
        fun utility(volley: Set<Int>, result: Forecast): Double {
            var future = 0.0
            for (branch in result.branches) {
                // Search aims to discover a ship promptly. A hit ends that search;
                // only the miss branch needs another hunting shot.
                if (hunting && volley.any { branch.fleets.first().owners[it] >= 0 }) continue
                val scores = DoubleArray(size)
                for (fleet in branch.fleets) for (ship in fleet.ships) {
                    val left = ship.unknown.count { it !in volley }
                    if (left == 0) continue
                    val value = hitValue(ship) * if (left == 1) 1.25 else 1.0
                    for (cell in ship.unknown) if (cell !in volley) scores[cell] += value
                }
                if (count == 1) future += scores.max() else {
                    scores.sort()
                    future += scores.takeLast(count).sum()
                }
            }
            val continuation = if (hunting) SEARCH_LOOKAHEAD_DISCOUNT else LOOKAHEAD_DISCOUNT
            return result.immediate + INFORMATION_VALUE * result.information + continuation * future / belief.fleets.size
        }
        suspend fun alternatives(base: List<Int>, candidates: List<Int>): Pair<Int, Double> {
            val forecasts = linkedMapOf<Int, Forecast>()
            candidates.forEachIndexed { index, cell ->
                if (index % 8 == 0) currentCoroutineContext().ensureActive()
                forecasts[cell] = forecast(belief, base + cell, values)
            }
            val shortlist = candidates.sortedByDescending { forecasts.getValue(it).let { f -> f.immediate + INFORMATION_VALUE * f.information } }.take(SHORTLIST)
            var best = Double.NEGATIVE_INFINITY
            val tied = mutableListOf<Int>()
            for (candidate in shortlist) {
                currentCoroutineContext().ensureActive()
                val score = utility((base + candidate).toSet(), forecasts.getValue(candidate))
                when {
                    score > best + 1e-9 -> { best = score; tied.clear(); tied.add(candidate) }
                    kotlin.math.abs(score - best) <= 1e-9 -> tied.add(candidate)
                }
            }
            return tied[random.nextInt(tied.size)] to best
        }
        repeat(count) {
            currentCoroutineContext().ensureActive()
            val referenceRandom = GameRandom(random.state)
            val (proposed, proposedUtility) = alternatives(selected, available)
            var target = proposed
            if (count == 1 && !belief.exact) {
                val baseline = FleetAi.normalScores(knowledge, available)
                val best = baseline.values.max()
                val tied = available.filter { baseline[it] == best }
                val reference = tied[referenceRandom.nextInt(tied.size)]
                if (reference != proposed) {
                    var total = 0.0; var squared = 0.0
                    for (fleet in belief.fleets) {
                        fun value(cell: Int): Double = fleet.owners[cell].let { if (it < 0) 0.0 else hitValue(fleet.ships[it]) }
                        val delta = value(proposed) - value(reference)
                        total += delta; squared += delta * delta
                    }
                    val mean = total / belief.fleets.size
                    val variance = (squared / belief.fleets.size - mean * mean).coerceAtLeast(0.0)
                    // Account for correlated samples, rather than treating a tiny
                    // estimated advantage as certainty over the stable baseline.
                    val margin = 1.5 * sqrt(variance * 4.0 / belief.fleets.size)
                    val referenceUtility = utility(setOf(reference), forecast(belief, listOf(reference), values))
                    if (proposedUtility <= referenceUtility + margin) target = reference
                }
            }
            selected.add(target); available.remove(target)
        }
        if (count > 1 && available.isNotEmpty()) {
            // Revisit early choices after the entire volley is known. A shot can
            // be individually attractive yet redundant beside the other targets.
            var score = utility(selected.toSet(), forecast(belief, selected, values))
            for (index in selected.indices) {
                val base = selected.filterIndexed { i, _ -> i != index }
                val (replacement, revised) = alternatives(base, available)
                if (revised > score + 1e-9) {
                    available.add(selected[index]); available.remove(replacement)
                    selected[index] = replacement; score = revised
                }
                if (available.isEmpty()) break
            }
        }
        return selected
    }
}
