package com.cbulock.sinkpoint.game

/** An unsunk contact needs at least one undisclosed cell. If every legal way
 * to cover that contact includes the same untried cell, that cell is occupied. */
internal fun forcedContinuation(knowledge: Knowledge): Int? {
    if (knowledge.config.rule != TurnRule.CLASSIC || knowledge.unresolved.size < 2) return null
    val config = knowledge.config
    val forbidden = HardAi.mask(knowledge.tried - knowledge.hits) or
        HardAi.border(config, knowledge.sunk.flatMap { it.cells })
    val lengths = knowledge.remaining.distinctBy { config.lengths[it] }
    val forced = mutableSetOf<Int>()
    for (hit in knowledge.unresolved) {
        var intersection: Set<Int>? = null
        alternatives@ for (id in lengths) for (horizontal in listOf(true, false)) {
            val stride = if (horizontal) 1 else config.size
            for (offset in 0 until config.lengths[id]) {
                val piece = Piece(id, hit - offset * stride, horizontal)
                val cells = Rules.cells(config, piece)
                if (hit !in cells || HardAi.mask(cells).intersects(forbidden)) continue
                val untried = cells.filterNot { it in knowledge.tried }
                if (untried.isEmpty()) continue
                intersection = intersection?.intersect(untried.toSet()) ?: untried.toSet()
                if (intersection.isEmpty()) break@alternatives
            }
        }
        forced.addAll(intersection ?: return null)
        if (forced.size > 1) return null
    }
    return forced.singleOrNull()
}
