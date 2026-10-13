package com.cbulock.sinkpoint

import com.cbulock.sinkpoint.game.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    @Test fun classicHitsRepeatForBothPlayersUntilAMissOrVictory() {
        val c = GameConfig(6, listOf(2, 2))
        assertTrue(c.fireAgainOnHit)
        var m = MatchState("repeat", c, "you", "peer", "you", Fleet(listOf(Piece(0, 0, true), Piece(1, 6, true))), true, true)
        fun shot(target: Int, hits: List<Int>, sunk: List<SunkPiece> = emptyList()) {
            m = m.append(Move("shot-${m.moves.size}", m.moves.size, m.current, listOf(target), Outcome(hits, sunk)))
            m.validate()
        }
        shot(12, listOf(12)); assertEquals("you", m.current)
        shot(13, listOf(13), listOf(SunkPiece(0, listOf(12, 13)))); assertEquals("you", m.current)
        assertTrue(runCatching { m.checkTargets("peer", listOf(0)) }.isFailure)
        assertTrue(runCatching { m.checkTargets("you", listOf(12)) }.isFailure)
        shot(35, emptyList()); assertEquals("peer", m.current)
        shot(0, listOf(0)); assertEquals("peer", m.current)
        shot(1, listOf(1), listOf(SunkPiece(0, listOf(0, 1)))); assertEquals("peer", m.current)
        shot(34, emptyList()); assertEquals("you", m.current)
        shot(18, listOf(18)); shot(19, listOf(19), listOf(SunkPiece(1, listOf(18, 19))))
        assertEquals(Phase.FINISHED, m.phase)
        assertTrue(runCatching { m.checkTargets("you", listOf(33)) }.isFailure)
    }
    @Test fun disabledClassicAndSalvoStillAlternateAfterHits() {
        for (c in listOf(GameConfig(6, listOf(2), fireAgainOnHit = false), GameConfig(6, listOf(2), rule = TurnRule.SALVO))) {
            val m = MatchState("alternate", c, "you", "peer", "peer", Fleet(listOf(Piece(0, 0, true))), true, true)
                .append(Move("hit", 0, "peer", listOf(0), Outcome(listOf(0), emptyList())))
            assertEquals("you", m.current)
            m.validate()
        }
    }
    @Test fun placementRejectsOverlapWrapAndTouching() {
        val c = GameConfig(size = 6, lengths = listOf(2, 2), touching = false)
        assertFalse(Rules.validFleet(c, Fleet(listOf(Piece(0, 0, true), Piece(1, 7, true)))))
        assertFalse(Rules.validFleet(c, Fleet(listOf(Piece(0, 5, true), Piece(1, 18, true)))))
        assertTrue(Rules.validFleet(c, Fleet(listOf(Piece(0, 0, true), Piece(1, 18, true)))))
        assertTrue(Rules.validFleet(c.copy(touching = true), Fleet(listOf(Piece(0, 0, true), Piece(1, 6, true)))))
    }
    @Test fun randomizedPlacementsAcrossBoardSizesRulesAndSpacing() = runBlocking {
        withTimeout(10_000) {
            for (size in 6..12) for (touching in listOf(true, false)) for (rule in TurnRule.entries) {
                val c = GameConfig(size, if (size == 6 && !touching) listOf(3, 3, 2) else listOf(5, 4, 3, 3, 2), touching, rule)
                val fleet = Rules.place(c, GameRandom(size.toLong()))
                assertNotNull("$c", fleet); assertTrue(Rules.validFleet(c, fleet!!))
            }
        }
    }
    @Test fun impossibleFleetReturnsNull() = runBlocking {
        assertNull(Rules.place(GameConfig(6, List(10) { 6 }), GameRandom(1)))
        assertNull(withTimeout(5000) { Rules.place(GameConfig(6, List(4) { 6 }, false), GameRandom(1)) })
    }
    @Test fun searchHonorsCancellation() = runBlocking {
        val result = runCatching { withTimeout(1) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { repeat(1000) { Rules.place(GameConfig(), GameRandom(it.toLong())) } } } }
        assertTrue(result.exceptionOrNull() is kotlinx.coroutines.TimeoutCancellationException)
    }
    @Test fun duplicateTargetsAndRepeatedShotsAreRejected() {
        val c = GameConfig(6, listOf(2))
        val fleet = Fleet(listOf(Piece(0, 0, true)))
        assertTrue(runCatching { Rules.resolve(c, fleet, emptySet(), listOf(0, 0)) }.isFailure)
        assertTrue(runCatching { Rules.resolve(c, fleet, setOf(0), listOf(0)) }.isFailure)
        assertTrue(runCatching { Rules.resolve(c, fleet, emptySet(), listOf(36)) }.isFailure)
    }
    @Test fun salvoQuotaShrinksWithFleetAndEndsAfterWholeVolley() {
        val c = GameConfig(6, listOf(2, 2), rule = TurnRule.SALVO)
        var m = MatchState("match", c, "you", "peer", "peer", Fleet(listOf(Piece(0, 0, true), Piece(1, 6, true))), true, true)
        assertEquals(2, m.quota())
        m = m.append(Move("a", 0, "peer", listOf(0, 1), Outcome(listOf(0, 1), listOf(SunkPiece(0, listOf(0, 1))))))
        assertEquals(1, m.quota())
        m = m.append(Move("b", 1, "you", listOf(35), Outcome(emptyList(), emptyList())))
        m = m.append(Move("c", 2, "peer", listOf(6, 7), Outcome(listOf(6, 7), listOf(SunkPiece(1, listOf(6, 7))))))
        assertEquals("peer", m.winner); assertEquals(Phase.FINISHED, m.phase)
        assertTrue(runCatching { m.checkTargets("you", listOf(34)) }.isFailure)
        m.validate()
    }
    @Test fun malformedSunkAnnouncementsAreRejected() {
        val c = GameConfig(6, listOf(2), rule = TurnRule.SALVO)
        val m = MatchState("m", c, "you", "peer", "you", Fleet(listOf(Piece(0, 0, true))), true, true)
        assertTrue(runCatching { m.append(Move("a", 0, "you", listOf(3), Outcome(listOf(3), listOf(SunkPiece(0, listOf(3, 5)))))) }.isFailure)
    }
    @Test fun placementScoringModesBeatRandomSearch() = runBlocking {
        val totals = Difficulty.entries.associateWith { 0 }.toMutableMap()
        repeat(80) { seed ->
            val config = GameConfig()
            val fleet = Rules.place(config, GameRandom(seed.toLong()))!!
            Difficulty.entries.forEach { difficulty ->
                var k = Knowledge(config, emptyList())
                val random = GameRandom((seed + 200).toLong())
                while (k.sunk.size < config.lengths.size) {
                    val target = FleetAi.strategy(difficulty).targets(k, 1, random)
                    val outcome = Rules.resolve(config, fleet, k.tried, target)
                    k = k.copy(moves = k.moves + Move("${k.moves.size}", k.moves.size, "ai", target, outcome))
                }
                totals[difficulty] = totals.getValue(difficulty) + k.tried.size
            }
        }
        println("Shots over 80 seeded fleets (lower is stronger): $totals")
        // Normal is the former Hard. Their close ranking varies by fleet sample;
        // HardAiTest covers the larger paired comparison between those strategies.
        assertTrue(totals.getValue(Difficulty.HARD) < totals.getValue(Difficulty.EASY))
        assertTrue(totals.getValue(Difficulty.NORMAL) < totals.getValue(Difficulty.EASY))
    }
    @Test fun aiRestorationAndCustomSalvosAreDeterministic() = runBlocking {
        for (size in 6..12) for (difficulty in Difficulty.entries) {
            val c = GameConfig(size, listOf(6, 2, 2), rule = TurnRule.SALVO)
            val k = Knowledge(c, emptyList())
            val a = FleetAi.strategy(difficulty).targets(k, 3, GameRandom(123))
            val b = FleetAi.strategy(difficulty).targets(k, 3, GameRandom(123))
            assertEquals(a, b); assertEquals(3, a.distinct().size)
        }
    }
}
