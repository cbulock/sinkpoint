package com.cbulock.sinkpoint

import com.cbulock.sinkpoint.game.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DefensivePlacementTest {
    @Test fun layoutsRemainLegalAcrossCustomFleetAndTouchingRules() = runBlocking {
        val configs = listOf(
            GameConfig(6, listOf(6, 4, 2)),
            GameConfig(6, listOf(3, 2), touching = false),
            GameConfig(12, List(10) { 6 }, rule = TurnRule.SALVO),
            GameConfig(12, List(10) { 2 }, touching = false),
        )
        for (config in configs) {
            val fleet = withTimeout(5000) { withContext(Dispatchers.Default) { DefensivePlacement.place(config, GameRandom(7201)) } }
            assertNotNull(fleet)
            assertTrue("Illegal layout for $config: $fleet", Rules.validFleet(config, fleet!!))
        }
    }

    @Test fun savedRandomStateReplaysBothLayoutAndFollowingRandomChoices() = runBlocking {
        val config = GameConfig(6, listOf(3, 2))
        val first = GameRandom(9201)
        val replay = GameRandom(9201)
        assertEquals(DefensivePlacement.place(config, first), DefensivePlacement.place(config, replay))
        assertEquals(first.state, replay.state)
        assertEquals(first.nextInt(1000), replay.nextInt(1000))
    }

    @Test fun differentSeedsKeepBothSpatialAndDirectionalVariety() = runBlocking {
        val config = GameConfig(6, listOf(3, 2), touching = false)
        val fleets = (0 until 12).map { DefensivePlacement.place(config, GameRandom(1000L + it))!! }
        assertTrue(fleets.map { fleet -> fleet.pieces.flatMap { Rules.cells(config, it) }.toSet() }.distinct().size >= 8)
        assertTrue(fleets.any { fleet -> fleet.pieces.any { it.horizontal } })
        assertTrue(fleets.any { fleet -> fleet.pieces.any { !it.horizontal } })
    }

    @Test fun cancelledPlacementDoesNotConsumeRandomState() = runBlocking {
        val random = GameRandom(18)
        val cancelled = Job().apply { cancel() }
        val result = runCatching { withContext(cancelled) { DefensivePlacement.place(GameConfig(), random) } }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(18L, random.state)
    }

    @Test fun activeDensePlacementRespondsToCancellation() = runBlocking {
        val result = runCatching {
            withTimeout(1) {
                withContext(Dispatchers.Default) {
                    repeat(50) { DefensivePlacement.place(GameConfig(12, List(10) { 6 }), GameRandom(it.toLong())) }
                }
            }
        }
        assertTrue(result.exceptionOrNull() is TimeoutCancellationException)
    }
}
