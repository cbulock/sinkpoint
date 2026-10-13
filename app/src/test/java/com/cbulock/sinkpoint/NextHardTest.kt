package com.cbulock.sinkpoint

import com.cbulock.sinkpoint.game.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class NextHardTest {
    private fun advance(k: Knowledge, fleet: Fleet, shots: List<Int>): Knowledge {
        val turn = k.moves.size
        return k.copy(moves = k.moves + Move("$turn", turn, "ai", shots, Rules.resolve(k.config, fleet, k.tried, shots)))
    }

    @Test fun simulationMatchesPublicRulesAndNormalDecisions() = runBlocking {
        for (touching in listOf(false, true)) for (size in listOf(6, 12)) {
            val config = GameConfig(size, listOf(3, 2), touching)
            val fleet = Rules.place(config, GameRandom(912))!!
            val simulator = AttackSimulation(config)
            var k = Knowledge(config, emptyList())
            val state = AttackSimulation.State(k)
            val pieces = AttackSimulation.placements(config, fleet)
            val random = GameRandom(91)
            while (k.remaining.isNotEmpty()) {
                val resumed = GameRandom(random.state)
                val expected = FleetAi.strategy(Difficulty.NORMAL).targets(k, 1, resumed)
                val actual = simulator.targets(state, 1, random)
                assertEquals(expected, actual)
                assertEquals(resumed.state, random.state)
                simulator.observe(state, pieces, actual)
                k = advance(k, fleet, actual)
                assertEquals(HardAi.mask(k.tried), state.tried)
                assertEquals(HardAi.mask(k.hits), state.hits)
                assertEquals(k.remaining, state.alive.indices.filter { state.alive[it] })
            }
        }
    }

    @Test fun exactEndgameMatchesIndependentExhaustiveOracle() = runBlocking {
        val config = GameConfig(6, listOf(2))
        val fleet = Fleet(listOf(Piece(0, 0, true)))
        val k = advance(Knowledge(config, emptyList()), fleet, (5 until 36).toList())
        val belief = HardAi.infer(k, GameRandom(8))
        assertTrue(belief.exact)
        // Independent set-based recursion over the four possible ships AB, BC, CD, DE.
        val worlds = listOf(setOf(0, 1), setOf(1, 2), setOf(2, 3), setOf(3, 4))
        fun cost(possible: List<Set<Int>>, tried: Set<Int>): Double {
            if (possible.all { tried.containsAll(it) }) return 0.0
            return (possible.flatten().toSet() - tried).minOf { shot ->
                1.0 + possible.groupBy { (shot in it) to ((tried + shot).containsAll(it)) }.values.sumOf {
                    it.size.toDouble() / possible.size * cost(it, tried + shot)
                }
            }
        }
        val selected = ExactEndgame.target(belief)!!
        val selectedCost = 1.0 + worlds.groupBy { selected in it }.values.sumOf { it.size.toDouble() / worlds.size * cost(it, setOf(selected)) }
        assertEquals(cost(worlds, emptySet()), selectedCost, 1e-9)
        assertEquals(ExactEndgame.target(belief, preferred = selected), selected)
        assertNull(ExactEndgame.target(belief, budget = 0))
        assertNull(ExactEndgame.target(belief.copy(exact = false)))
    }

    @Test fun defenseIsLegalDiverseAndReproducible() = runBlocking {
        val config = GameConfig()
        val layouts = mutableSetOf<Fleet>()
        repeat(4) { seed ->
            val firstRandom = GameRandom(seed.toLong())
            val secondRandom = GameRandom(seed.toLong())
            val fleet = DefensivePlacement.place(config, firstRandom)!!
            assertTrue(Rules.validFleet(config, fleet))
            assertEquals(fleet, DefensivePlacement.place(config, secondRandom))
            assertEquals(firstRandom.state, secondRandom.state)
            layouts.add(fleet)
        }
        assertTrue(layouts.size > 1)
        val maximum = GameConfig(12, List(10) { 6 })
        assertTrue(Rules.validFleet(maximum, DefensivePlacement.place(maximum, GameRandom(2))!!))
        assertNull(DefensivePlacement.place(GameConfig(6, List(10) { 6 }, touching = false), GameRandom(2)))
    }

    @Test fun rolloutAndDefenseHonorCancellation() = runBlocking {
        val failure = runCatching { withTimeout(1) { withContext(Dispatchers.Default) {
            repeat(100) { DefensivePlacement.place(GameConfig(), GameRandom(it.toLong())) }
        } } }.exceptionOrNull()
        assertTrue(failure is TimeoutCancellationException)
        val config = GameConfig(10, listOf(3))
        val k = Knowledge(config, emptyList())
        val belief = HardAi.infer(k, GameRandom(3))
        val rolloutFailure = runCatching { withTimeout(1) { withContext(Dispatchers.Default) {
            repeat(100) { RolloutAttack.targets(k, belief, listOf(44), GameRandom(it.toLong())) }
        } } }.exceptionOrNull()
        assertTrue(rolloutFailure is TimeoutCancellationException)
    }

    @Test fun pairedAttackAndDefenseBenchmark() = runBlocking {
        val games = System.getenv("SINKPOINT_NEXT_BENCHMARK_GAMES")?.toInt() ?: 24
        val baseSeed = System.getenv("SINKPOINT_NEXT_BENCHMARK_SEED")?.toLong() ?: 280_000L
        for (touching in listOf(true, false)) {
            val config = GameConfig(touching = touching)
            val totals = IntArray(4)
            val timings = mutableListOf<Long>()
            repeat(games) { index ->
                val seed = baseSeed + index
                // Mix unselected random boards with edge-biased and compact layouts.
                val layouts = (0 until if (index % 3 == 0) 1 else 8).map { Rules.place(config, GameRandom(seed + it * 1000))!! }
                fun shape(f: Fleet): Int {
                    val cells = f.pieces.flatMap { Rules.cells(config, it) }
                    return if (index % 3 == 1) cells.sumOf { minOf(it % 10, 9 - it % 10, it / 10, 9 - it / 10) }
                    else (cells.maxOf { it / 10 } - cells.minOf { it / 10 }) * (cells.maxOf { it % 10 } - cells.minOf { it % 10 })
                }
                val fleet = layouts.minBy(::shape)
                for (version in 0..3) {
                    var k = Knowledge(config, emptyList())
                    val random = GameRandom(seed + 500)
                    while (k.remaining.isNotEmpty()) {
                        val started = System.nanoTime()
                        val shots = if (version == 0) PreviousHardAi.targets(k, 1, random) else {
                            val belief = HardAi.infer(k, GameRandom(random.state xor -7046029254386353131L))
                            val rollRandom = GameRandom(random.state xor 0x524f4c4c4f5554L)
                            val baseline = HardAi.plan(k, belief, 1, random)
                            val exact = if (version != 2) ExactEndgame.target(belief, preferred = baseline.single()) else null
                            if (exact != null) listOf(exact)
                            else if (version >= 2) RolloutAttack.targets(k, belief, baseline, rollRandom) else baseline
                        }
                        if (version == 3) timings.add(System.nanoTime() - started)
                        k = advance(k, fleet, shots)
                    }
                    totals[version] += k.tried.size
                }
            }
            println("Attack touching=$touching games=$games old/exact/rollout/both=${totals.toList()}")
            timings.sort()
            println("New Hard desktop planning p95ms=${timings[(timings.size * .95).toInt()] / 1_000_000.0} maxMs=${timings.last() / 1_000_000.0}")
        }
        val config = GameConfig()
        val simulator = AttackSimulation(config)
        val state = AttackSimulation.State(Knowledge(config, emptyList()))
        val defense = IntArray(2)
        repeat(games) { index ->
            val seed = baseSeed + 110_000L + index
            val layouts = listOf(Rules.place(config, GameRandom(seed))!!, DefensivePlacement.place(config, GameRandom(seed))!!)
            for (i in layouts.indices) for (parity in listOf(false, true)) {
                defense[i] += simulator.finish(state, AttackSimulation.placements(config, layouts[i]), emptyList(), 1, GameRandom(seed + 123456), parity)
            }
        }
        println("Defense games=${games * 2} random/selected=${defense.toList()}")
        assertTrue("Selected defense should last longer against fresh attacks", defense[1] > defense[0])
    }
}
