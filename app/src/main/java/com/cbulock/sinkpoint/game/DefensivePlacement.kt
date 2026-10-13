package com.cbulock.sinkpoint.game

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Select diverse resilient layouts without consulting the player's fleet or current shots. */
internal object DefensivePlacement {
    suspend fun place(config: GameConfig, random: GameRandom): Fleet? {
        val first = Rules.place(config, random) ?: return null
        // Keep exact placement search manageable for unusually dense custom configurations.
        if (config.lengths.size > 6 || config.lengths.sum() * 3 > config.size * config.size) return first
        val candidates = mutableListOf(first)
        repeat(11) { Rules.place(config, random)?.let { candidates.add(it) } }
        val simulator = AttackSimulation(config)
        val state = AttackSimulation.State(Knowledge(config, emptyList()))
        val evaluationSeed = random.state
        val scored = candidates.distinct().map { fleet ->
            currentCoroutineContext().ensureActive()
            val ships = AttackSimulation.placements(config, fleet)
            val costs = (0 until 4).map { trial ->
                simulator.finish(state, ships, emptyList(), if (config.rule == TurnRule.SALVO) config.lengths.size else 1,
                    GameRandom(evaluationSeed + trial * 7919L), parity = trial >= 2)
            }
            // Reward layouts that survive both probability targeting and parity hunting.
            fleet to (costs.average() + costs.min() * 0.5)
        }.sortedByDescending { it.second }
        val finalists = scored.take(3)
        return finalists[random.nextInt(finalists.size)].first
    }
}
