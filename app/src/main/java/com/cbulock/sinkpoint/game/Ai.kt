package com.cbulock.sinkpoint.game

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

fun interface AiStrategy {
    suspend fun targets(knowledge: Knowledge, count: Int, random: GameRandom): List<Int>
}

object FleetAi {
    fun strategy(difficulty: Difficulty) = AiStrategy { knowledge, count, random ->
        if (difficulty == Difficulty.HARD) return@AiStrategy HardAi.targets(knowledge, count, random)
        val config = knowledge.config
        val available = (0 until config.size * config.size).filter { it !in knowledge.tried }.toMutableList()
        require(count in 1..available.size)
        val chosen = mutableListOf<Int>()
        repeat(count) {
            currentCoroutineContext().ensureActive()
            val scores = when (difficulty) {
                Difficulty.EASY -> available.associateWith { 1 }
                // Normal preserves the original Hard placement-scoring strategy.
                Difficulty.NORMAL -> normalScores(knowledge, available)
                Difficulty.HARD -> error("Hard uses fleet planning")
            }
            val best = available.maxOf { scores.getValue(it) }
            val tied = available.filter { scores[it] == best }
            val target = tied[random.nextInt(tied.size)]
            chosen.add(target); available.remove(target)
        }
        chosen
    }

    internal suspend fun normalScores(knowledge: Knowledge, available: List<Int>): Map<Int, Int> {
        val config = knowledge.config
        val scores = available.associateWith { 0 }.toMutableMap()
        val misses = knowledge.tried - knowledge.hits
        val destroyed = knowledge.sunk.flatMap { it.cells }.toSet()
        val forbidden = if (config.touching) destroyed else destroyed.flatMap { cell ->
            (0 until config.size * config.size).filter { other ->
                kotlin.math.abs(cell / config.size - other / config.size) <= 1 && kotlin.math.abs(cell % config.size - other % config.size) <= 1
            }
        }.toSet()
        for (id in knowledge.remaining) {
            currentCoroutineContext().ensureActive()
            for (start in 0 until config.size * config.size) for (horizontal in listOf(true, false)) {
                val cells = Rules.cells(config, Piece(id, start, horizontal))
                if (cells.isEmpty() || cells.any { it in misses || it in forbidden }) continue
                val hitCount = cells.count { it in knowledge.unresolved }
                val weight = if (hitCount > 0) 100 * hitCount * hitCount else 1
                cells.forEach { cell -> if (cell in scores) scores[cell] = scores.getValue(cell) + weight }
            }
        }
        return scores
    }
}

