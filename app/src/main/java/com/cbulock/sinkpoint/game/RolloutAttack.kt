package com.cbulock.sinkpoint.game

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.sqrt

/** Paired full-game rollouts; every continuation selects from its simulated public history. */
internal object RolloutAttack {
    suspend fun targets(knowledge: Knowledge, belief: HardAi.Belief, baseline: List<Int>, random: GameRandom): List<Int> {
        // Correlated approximate fleets made rollout rankings too noisy. Spend this
        // budget only where enumeration established the complete set of possibilities.
        if (!belief.exact || knowledge.remaining.size > 3 || baseline.size > 5) return baseline
        val simulator = AttackSimulation(knowledge.config)
        val initial = AttackSimulation.State(knowledge)
        val scores = simulator.scores(initial)
        val available = scores.indices.filter { it !in knowledge.tried && it !in baseline }
        val candidates = mutableListOf(baseline)
        val promising = available.sortedByDescending { scores[it] }.take(4)
        if (baseline.size == 1) promising.forEach { candidates.add(listOf(it)) }
        else for (cell in promising.take(2)) for (index in baseline.indices.take(2)) {
            candidates.add(baseline.toMutableList().apply { this[index] = cell })
        }
        if (candidates.size == 1) return baseline
        val samples = random.shuffled(belief.fleets.indices.toList()).take(128)
        val totals = DoubleArray(candidates.size)
        val differencesSquared = DoubleArray(candidates.size)
        val context = currentCoroutineContext()
        for (sample in samples) {
            context.ensureActive()
            val seed = random.state
            random.nextInt(Int.MAX_VALUE)
            val fleet = belief.fleets[sample].ships
            val reference = simulator.finish(initial, fleet, baseline, baseline.size, GameRandom(seed))
            for (i in 1 until candidates.size) {
                val cost = simulator.finish(initial, fleet, candidates[i], baseline.size, GameRandom(seed))
                val saved = (reference - cost).toDouble()
                totals[i] += saved; differencesSquared[i] += saved * saved
            }
        }
        var best = 0
        var advantage = 0.0
        for (i in 1 until candidates.size) {
            val mean = totals[i] / samples.size
            val variance = (differencesSquared[i] / samples.size - mean * mean).coerceAtLeast(0.0)
            // A conservative selection margin, not a claim that correlated samples are independent.
            val supported = mean - 1.5 * sqrt(2.0 * variance / samples.size) - 0.2
            if (supported > advantage) { best = i; advantage = supported }
        }
        return candidates[best]
    }
}
