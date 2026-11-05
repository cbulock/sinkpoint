package com.cbulock.sinkpoint

import com.cbulock.sinkpoint.game.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ForcedContinuationTest {
    private fun board(c: GameConfig, hits: List<Int>, misses: List<Int> = emptyList()) =
        Knowledge(c, (hits + misses).mapIndexed { turn, cell ->
            val result = Outcome(if (cell in hits) listOf(cell) else emptyList(), emptyList())
            Move(turn.toString(), turn, "ai", listOf(cell), result)
        })

    @Test fun pursuesTheProvenShipAcrossAllSymmetries() = runBlocking {
        for (reflected in listOf(false, true)) for (turns in 0 until 4) {
            fun transform(cell: Int): Int {
                var row = cell / 10
                var col = if (reflected) 9 - cell % 10 else cell % 10
                repeat(turns) {
                    val previous = row
                    row = col
                    col = 9 - previous
                }
                return row * 10 + col
            }
            val k = board(GameConfig(), listOf(15, 25, 35, 44, 45).map(::transform),
                listOf(43, 34, 54).map(::transform))
            for (seed in 0 until 4) {
                val shot = FleetAi.strategy(Difficulty.HARD).targets(k, 1, GameRandom(seed.toLong()))
                assertEquals("reflected=$reflected turns=$turns seed=$seed", listOf(transform(46)), shot)
            }
        }
    }

    @Test fun adjacentHitsCanBelongToDifferentShips() {
        val config = GameConfig(6, listOf(3, 2), touching = true)
        val k = board(config, listOf(14, 15))
        val fleets = listOf(
            Fleet(listOf(Piece(0, 2, false), Piece(1, 15, true))),
            Fleet(listOf(Piece(0, 14, false), Piece(1, 9, false))),
        )
        val unexplored = fleets.map { fleet ->
            val previous = mutableSetOf<Int>()
            for (move in k.moves) {
                assertEquals(move.outcome, Rules.resolve(config, fleet, previous, move.targets))
                previous.addAll(move.targets)
            }
            fleet.pieces.flatMap { Rules.cells(config, it) }.toSet() - k.tried
        }
        assertTrue(unexplored[0].intersect(unexplored[1]).isEmpty())
        assertNull(forcedContinuation(k))
    }
}
