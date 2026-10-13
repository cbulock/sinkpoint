package com.cbulock.sinkpoint.game

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Minimum expected remaining shots, only when the complete belief fits the bounded solver. */
internal object ExactEndgame {
    private data class Key(val fleets: Long, val fired: Long)
    private class BudgetExceeded : RuntimeException()

    suspend fun target(belief: HardAi.Belief, budget: Int = 25_000, preferred: Int? = null): Int? {
        if (!belief.exact || belief.fleets.isEmpty() || belief.fleets.size > 32) return null
        val cells = belief.fleets.flatMap { f -> f.ships.flatMap { it.unknown.toList() } }.distinct().sorted()
        if (cells.size !in 1..18) return null
        val ships = belief.fleets.map { f -> f.ships.map { ship ->
            ship.unknown.fold(0L) { bits, cell -> bits or (1L shl cells.indexOf(cell)) }
        } }
        val occupied = ships.map { it.fold(0L) { a, b -> a or b } }
        val context = currentCoroutineContext()
        val memo = HashMap<Key, Double>()
        var work = 0
        fun evaluate(fleets: Long, fired: Long): Pair<Int, Double> {
            if (++work % 128 == 0) context.ensureActive()
            if (work > budget) throw BudgetExceeded()
            val members = occupied.indices.filter { fleets and (1L shl it) != 0L }
            if (members.all { occupied[it] and fired == occupied[it] }) return -1 to 0.0
            val available = members.fold(0L) { bits, i -> bits or occupied[i] } and fired.inv()
            var bestCell = -1
            var bestCost = Double.POSITIVE_INFINITY
            for (cell in cells.indices) if (available and (1L shl cell) != 0L) {
                val nextFired = fired or (1L shl cell)
                val outcomes = HashMap<Int, Long>()
                for (i in members) {
                    val owner = ships[i].indexOfFirst { it and (1L shl cell) != 0L }
                    val outcome = if (owner < 0) 0 else if (ships[i][owner] and nextFired != ships[i][owner]) 1 else {
                        val piece = belief.fleets[i].ships[owner].piece
                        2 + piece.id * 512 + piece.start * 2 + if (piece.horizontal) 0 else 1
                    }
                    outcomes[outcome] = (outcomes[outcome] ?: 0L) or (1L shl i)
                }
                var cost = 1.0
                for (branch in outcomes.values) {
                    val key = Key(branch, nextFired)
                    val continuation = memo[key] ?: evaluate(branch, nextFired).second.also { memo[key] = it }
                    cost += java.lang.Long.bitCount(branch).toDouble() / members.size * continuation
                    if (cost > bestCost + 1e-9) break
                }
                if (cost < bestCost - 1e-9 || (fired == 0L && cells[cell] == preferred && kotlin.math.abs(cost - bestCost) <= 1e-9)) {
                    bestCost = cost; bestCell = cell
                }
            }
            return bestCell to bestCost
        }
        context.ensureActive()
        return try {
            val index = evaluate((1L shl belief.fleets.size) - 1, 0).first
            cells.getOrNull(index)
        } catch (_: BudgetExceeded) { null }
    }
}
