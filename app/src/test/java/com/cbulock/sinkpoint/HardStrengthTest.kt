package com.cbulock.sinkpoint

import com.cbulock.sinkpoint.game.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

/** Paired acceptance seeds are separate from calibration and the original 80 fleets. */
class HardStrengthTest {
    @Test fun benchmarkClassicAndJointSalvos() = runBlocking {
        val games = System.getenv("SINKPOINT_AI_BENCHMARK_GAMES")?.toInt() ?: 100
        val baseSeed = System.getenv("SINKPOINT_AI_BENCHMARK_SEED")?.toLong() ?: 90_000L
        val worker = Dispatchers.Default.limitedParallelism(4)
        val results = (0 until 4).map { scenario -> async(worker) {
            val touching = scenario % 2 == 0
            val rule = if (scenario < 2) TurnRule.CLASSIC else TurnRule.SALVO
            val config = GameConfig(touching = touching, rule = rule)
            var normalShots = 0; var hardShots = 0
            var differencesSquared = 0.0
            var hardWins = 0
            val timings = mutableListOf<Long>()
            repeat(games) { index ->
                val seed = baseSeed + scenario * 10_000L + index
                val fleet = Rules.place(config, GameRandom(seed))!!
                suspend fun finish(difficulty: Difficulty): Int {
                    var k = Knowledge(config, emptyList())
                    val random = GameRandom(seed + 1_000_000)
                    while (k.remaining.isNotEmpty()) {
                        val count = minOf(if (rule == TurnRule.SALVO) config.lengths.size else 1, config.size * config.size - k.tried.size)
                        val started = System.nanoTime()
                        val targets = FleetAi.strategy(difficulty).targets(k, count, random)
                        if (difficulty == Difficulty.HARD) timings.add(System.nanoTime() - started)
                        val turn = k.moves.size
                        k = k.copy(moves = k.moves + Move("$turn", turn, "ai", targets, Rules.resolve(config, fleet, k.tried, targets)))
                    }
                    return k.tried.size
                }
                val normal = finish(Difficulty.NORMAL)
                val hard = finish(Difficulty.HARD)
                normalShots += normal; hardShots += hard
                differencesSquared += (normal - hard).toDouble() * (normal - hard)
                if (hard < normal) hardWins++
            }
            val mean = (normalShots - hardShots).toDouble() / games
            val standardError = sqrt(((differencesSquared / games - mean * mean).coerceAtLeast(0.0)) / games)
            timings.sort()
            println("$rule touching=$touching games=$games Normal=$normalShots Hard=$hardShots gain=${100.0 * (normalShots - hardShots) / normalShots}% pairedDelta=$mean +/- ${1.96 * standardError} hardFaster=$hardWins p95ms=${timings[(timings.size * .95).toInt()] / 1_000_000.0} maxMs=${timings.last() / 1_000_000.0}")
            normalShots to hardShots
        } }.awaitAll()
        assertTrue("Hard should use fewer shots overall: $results", results.sumOf { it.second } < results.sumOf { it.first })
    }
}
