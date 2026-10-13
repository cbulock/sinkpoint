package com.cbulock.sinkpoint

import com.cbulock.sinkpoint.game.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.ln

class HardPlannerTest {
    private fun observed(config: GameConfig, fleet: Fleet, shots: List<Int>): Knowledge =
        Knowledge(config, listOf(Move("public", 0, "player", shots, Rules.resolve(config, fleet, emptySet(), shots))))

    @Test fun everySampleExplainsAllPublicEvidenceAndSpacing() = runBlocking {
        for (touching in listOf(true, false)) {
            val config = GameConfig(10, listOf(4, 3, 3, 2), touching)
            val fleet = Fleet(listOf(Piece(0, 0, true), Piece(1, 25, true), Piece(2, 60, false), Piece(3, 87, true)))
            val knowledge = observed(config, fleet, listOf(0, 1, 10, 25, 26, 40, 60, 87, 88))
            val belief = HardAi.infer(knowledge, GameRandom(333))
            assertFalse(belief.fleets.isEmpty())
            for (sample in belief.fleets) {
                val pieces = sample.ships.map { it.piece } + fleet.pieces.filter { p -> knowledge.sunk.any { it.id == p.id } }
                val possibleFleet = Fleet(pieces)
                assertTrue(Rules.validFleet(config, possibleFleet))
                assertEquals(knowledge.moves.single().outcome, Rules.resolve(config, possibleFleet, emptySet(), knowledge.tried.toList()))
            }
        }
    }

    @Test fun highBoardCellsAndDuplicateShipLengthsRemainDistinct() = runBlocking {
        val config = GameConfig(12, listOf(2, 2))
        val fleet = Fleet(listOf(Piece(0, 132, true), Piece(1, 140, true)))
        val water = (0 until 144).filter { it !in listOf(132, 133, 140, 141) }
        val knowledge = observed(config, fleet, water)
        val targets = HardAi.targets(knowledge, 4, GameRandom(12))
        assertEquals(setOf(132, 133, 140, 141), targets.toSet())
    }

    @Test fun jointSalvoDistinguishesAllRemainingFleets() = runBlocking {
        val config = GameConfig(6, listOf(2, 2), rule = TurnRule.SALVO)
        val fleet = Fleet(listOf(Piece(0, 0, true), Piece(1, 30, true)))
        val water = (5 until 36).toList()
        val knowledge = observed(config, fleet, water)
        val belief = HardAi.infer(knowledge, GameRandom(1))
        assertTrue(belief.exact)
        val targets = HardAi.plan(knowledge, belief, 2, GameRandom(1))
        fun information(shots: List<Int>): Double {
            val outcomes = belief.fleets.groupingBy { sample ->
                Rules.resolve(config, Fleet(sample.ships.map { it.piece } + fleet.pieces.filter { p -> knowledge.sunk.any { it.id == p.id } }), knowledge.tried, shots)
            }.eachCount()
            return outcomes.values.sumOf { count -> val p = count.toDouble() / belief.fleets.size; -p * ln(p) }
        }
        assertEquals(ln(4.0), information(targets), 1e-9)
        assertTrue(information(targets) > information(listOf(1, 3)))
    }

    @Test fun looksAheadToTheBetterReplyWhenImmediateHitChancesTie() = runBlocking {
        val config = GameConfig(6, listOf(2))
        val fleet = Fleet(listOf(Piece(0, 0, true)))
        val knowledge = observed(config, fleet, (5 until 36).toList())
        // Four legal ships: AB, BC, CD, DE. B/C/D each have 50% hit chance.
        // Missing B guarantees D next; missing C leaves two disjoint ships.
        val belief = HardAi.infer(knowledge, GameRandom(1))
        assertTrue(belief.exact)
        assertEquals(4, belief.fleets.size)
        repeat(20) { seed ->
            val shot = HardAi.plan(knowledge, belief, 1, GameRandom(seed.toLong())).single()
            assertTrue("Look-ahead should prefer B or D to C", shot == 1 || shot == 3)
        }
    }

    @Test fun emptySearchBudgetFallsBackLegallyAndReproducibly() = runBlocking {
        val knowledge = Knowledge(GameConfig(), emptyList())
        assertTrue(HardAi.infer(knowledge, GameRandom(42), searchBudget = 0).fleets.isEmpty())
        val first = HardAi.targets(knowledge, 5, GameRandom(42), searchBudget = 0)
        assertEquals(first, FleetAi.strategy(Difficulty.NORMAL).targets(knowledge, 5, GameRandom(42)))
        assertEquals(5, first.distinct().size)
    }

    @Test fun planningHonorsCancellation() = runBlocking {
        val job = launch(start = CoroutineStart.LAZY) {
            HardAi.targets(Knowledge(GameConfig(12, List(10) { 6 }), emptyList()), 10, GameRandom(1))
            fail("Cancelled work must not finish planning")
        }
        job.cancel(); job.join()
        assertTrue(job.isCancelled)
        val result = runCatching {
            withTimeout(1) {
                withContext(Dispatchers.Default) {
                    repeat(100) { HardAi.targets(Knowledge(GameConfig(12, List(10) { 6 }), emptyList()), 10, GameRandom(it.toLong())) }
                }
            }
        }
        assertTrue(result.exceptionOrNull() is TimeoutCancellationException)
    }

    @Test fun maximumCustomSalvosStayBoundedAndLegal() = runBlocking {
        val config = GameConfig(12, List(10) { 6 }, rule = TurnRule.SALVO)
        val fleet = Fleet(List(10) { row -> Piece(row, row * 12 + if (row % 2 == 0) 0 else 6, true) })
        for (shots in listOf(emptyList(), (0 until 144 step 3).toList())) {
            val knowledge = if (shots.isEmpty()) Knowledge(config, emptyList()) else observed(config, fleet, shots)
            val started = System.nanoTime()
            val targets = withTimeout(5000) { withContext(Dispatchers.Default) { HardAi.targets(knowledge, 10, GameRandom(456)) } }
            println("Maximum custom salvo, known=${knowledge.tried.size}, ms=${(System.nanoTime() - started) / 1_000_000.0}")
            assertEquals(10, targets.distinct().size)
            assertTrue(targets.none { it in knowledge.tried })
            Rules.resolve(config, fleet, knowledge.tried, targets)
        }
    }
}
