package com.cbulock.sinkpoint

import com.cbulock.sinkpoint.game.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class HardAiTest {
    @Test fun normalExactlyMatchesOriginalHardIncludingSalvos() = runBlocking {
        for (size in listOf(6, 8, 10, 12)) for (touching in listOf(true, false)) for (rule in TurnRule.entries) {
            val config = GameConfig(size, listOf(3, 3, 2), touching, rule)
            val fleet = Rules.place(config, GameRandom(size.toLong()))!!
            var knowledge = Knowledge(config, emptyList())
            val random = GameRandom(456)
            val legacyRandom = GameRandom(456)
            while (knowledge.remaining.isNotEmpty()) {
                val count = minOf(if (rule == TurnRule.SALVO) 3 else 1, size * size - knowledge.tried.size)
                val expected = mutableListOf<Int>()
                repeat(count) { expected.add(legacyTarget(knowledge, legacyRandom, expected.toSet())) }
                val actual = FleetAi.strategy(Difficulty.NORMAL).targets(knowledge, count, random)
                assertEquals(expected, actual)
                assertEquals(legacyRandom.state, random.state)
                val turn = knowledge.moves.size
                val outcome = Rules.resolve(config, fleet, knowledge.tried, actual)
                knowledge = knowledge.copy(moves = knowledge.moves + Move("$turn", turn, "ai", actual, outcome))
            }
        }
    }

    @Test fun takesGuaranteedShipCellsBeforeSpeculativeShots() = runBlocking {
        val config = GameConfig(6, listOf(6, 2))
        val misses = (6 until 36).filter { it !in listOf(18, 19) }
        val knowledge = Knowledge(config, listOf(Move("misses", 0, "ai", misses, Outcome(emptyList(), emptyList()))))
        repeat(20) { seed ->
            val shot = FleetAi.strategy(Difficulty.HARD).targets(knowledge, 1, GameRandom(seed.toLong())).single()
            assertTrue("Both ships have fully determined locations", shot in 0..5 || shot in 18..19)
        }
    }

    @Test fun noTouchHitsMustBelongToTheSameStraightShip() = runBlocking {
        val config = GameConfig(6, listOf(3, 2), touching = false)
        val knowledge = Knowledge(config, listOf(Move("hits", 0, "ai", listOf(14, 15, 16), Outcome(listOf(14, 15), emptyList()))))
        repeat(20) { seed ->
            assertEquals(listOf(13), FleetAi.strategy(Difficulty.HARD).targets(knowledge, 1, GameRandom(seed.toLong())))
        }
    }

    @Test fun finishesCustomClassicAndSalvoGamesWithoutRepeats() = runBlocking {
        for (size in listOf(6, 8, 10, 12)) for (touching in listOf(true, false)) for (rule in TurnRule.entries) {
            val config = GameConfig(size, listOf(3, 3, 2), touching, rule)
            val fleet = Rules.place(config, GameRandom(size.toLong()))!!
            val random = GameRandom(123)
            var knowledge = Knowledge(config, emptyList())
            while (knowledge.remaining.isNotEmpty()) {
                val count = minOf(if (rule == TurnRule.SALVO) 3 else 1, size * size - knowledge.tried.size)
                val restored = GameRandom(random.state)
                val shots = FleetAi.strategy(Difficulty.HARD).targets(knowledge, count, random)
                assertEquals(shots, FleetAi.strategy(Difficulty.HARD).targets(knowledge, count, restored))
                assertEquals(count, shots.distinct().size)
                assertTrue(shots.none { it in knowledge.tried })
                val outcome = Rules.resolve(config, fleet, knowledge.tried, shots)
                val turn = knowledge.moves.size
                knowledge = knowledge.copy(moves = knowledge.moves + Move("$turn", turn, "ai", shots, outcome))
            }
        }
    }

    @Test fun strongerAcrossIndependentFleets() = runBlocking {
        for (touching in listOf(true, false)) {
            var before = 0
            var after = 0
            repeat(400) { index ->
                val seed = 1000L + index
                val config = GameConfig(touching = touching)
                val fleet = Rules.place(config, GameRandom(seed))!!
                for (legacy in listOf(true, false)) {
                    var knowledge = Knowledge(config, emptyList())
                    val random = GameRandom(seed + 200)
                    while (knowledge.remaining.isNotEmpty()) {
                        val targets = if (legacy) listOf(legacyTarget(knowledge, random))
                            else FleetAi.strategy(Difficulty.HARD).targets(knowledge, 1, random)
                        val outcome = Rules.resolve(config, fleet, knowledge.tried, targets)
                        val turn = knowledge.moves.size
                        knowledge = knowledge.copy(moves = knowledge.moves + Move("$turn", turn, "ai", targets, outcome))
                    }
                    if (legacy) before += knowledge.tried.size else after += knowledge.tried.size
                }
            }
            println("Hard shots across 400 independent fleets, touching=$touching: before=$before after=$after")
            assertTrue("Hard should improve with touching=$touching: $before -> $after", after < before)
        }
    }

    /** Frozen previous Hard strategy for a paired strength regression benchmark. */
    private fun legacyTarget(k: Knowledge, random: GameRandom, excluded: Set<Int> = emptySet()): Int {
        val c = k.config
        val scores = (0 until c.size * c.size).filter { it !in k.tried && it !in excluded }.associateWith { 0 }.toMutableMap()
        val misses = k.tried - k.hits
        val destroyed = k.sunk.flatMap { it.cells }.toSet()
        val forbidden = if (c.touching) destroyed else destroyed.flatMap { cell ->
            (0 until c.size * c.size).filter { other -> abs(cell / c.size - other / c.size) <= 1 && abs(cell % c.size - other % c.size) <= 1 }
        }.toSet()
        for (id in k.remaining) for (start in 0 until c.size * c.size) for (horizontal in listOf(true, false)) {
            val cells = Rules.cells(c, Piece(id, start, horizontal))
            if (cells.isEmpty() || cells.any { it in misses || it in forbidden }) continue
            val hits = cells.count { it in k.unresolved }
            val weight = if (hits > 0) 100 * hits * hits else 1
            cells.forEach { if (it in scores) scores[it] = scores.getValue(it) + weight }
        }
        val best = scores.values.max()
        val tied = scores.keys.filter { scores[it] == best }
        return tied[random.nextInt(tied.size)]
    }
}
