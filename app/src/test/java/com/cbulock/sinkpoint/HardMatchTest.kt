package com.cbulock.sinkpoint

import com.cbulock.sinkpoint.game.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Complete matches: alternate starters, reduce Salvo quotas after losses, never expose the opposing fleet to the policy. */
class HardMatchTest {
    @Test fun completeMatchesAgainstNormal() = runBlocking {
        val games = System.getenv("SINKPOINT_MATCH_BENCHMARK_GAMES")?.toInt() ?: 4
        for (rule in TurnRule.entries) for (touching in listOf(true, false)) for (again in if (rule == TurnRule.CLASSIC) listOf(true, false) else listOf(false)) {
            val config = GameConfig(rule = rule, touching = touching, fireAgainOnHit = again)
            val wins = IntArray(2)
            val turns = IntArray(2)
            repeat(games) { index ->
                val seed = 490_000L + index
                val normalFleet = Rules.place(config, GameRandom(seed))!!
                val oldFleet = Rules.place(config, GameRandom(seed + 10_000))!!
                val newFleet = DefensivePlacement.place(config, GameRandom(seed + 10_000))!!
                for (version in 0..1) for (starter in 0..1) {
                    val fleets = listOf(normalFleet, if (version == 0) oldFleet else newFleet)
                    val histories = MutableList(2) { Knowledge(config, emptyList()) }
                    val random = listOf(GameRandom(seed + 20_000), GameRandom(seed + 30_000))
                    var player = starter
                    var moves = 0
                    while (true) {
                        val k = histories[player]
                        val afloat = config.lengths.size - histories[1 - player].sunk.size
                        val quota = minOf(if (rule == TurnRule.SALVO) afloat else 1, config.size * config.size - k.tried.size)
                        val targets = if (player == 0) FleetAi.strategy(Difficulty.NORMAL).targets(k, quota, random[player])
                            else if (version == 0) PreviousHardAi.targets(k, quota, random[player])
                            else FleetAi.strategy(Difficulty.HARD).targets(k, quota, random[player])
                        assertEquals(quota, targets.distinct().size)
                        assertTrue(targets.none { it in k.tried })
                        val result = Rules.resolve(config, fleets[1 - player], k.tried, targets)
                        histories[player] = k.copy(moves = k.moves + Move("$moves", moves, "$player", targets, result))
                        moves++
                        if (histories[player].remaining.isEmpty()) {
                            if (player == 1) wins[version]++
                            turns[version] += moves
                            break
                        }
                        check(moves <= 200)
                        if (!(rule == TurnRule.CLASSIC && config.fireAgainOnHit && result.hits.isNotEmpty())) player = 1 - player
                    }
                }
            }
            println("Match $rule touching=$touching fireAgain=$again perVersion=${games * 2} old/new Hard wins=${wins.toList()} totalTurns=${turns.toList()}")
        }
    }
}
