package com.cbulock.sinkpoint

import com.cbulock.sinkpoint.game.*

/** Only public shot knowledge crosses into Compose during an active hunt. */
data class DailyBoard(
    val id: String, val date: String, val config: GameConfig, val knowledge: Knowledge,
    val scored: Boolean, val finished: Boolean, val abandoned: Boolean, val revealedFleet: Fleet?,
) {
    val active get() = !finished && !abandoned
}
data class DailyUi(
    val today: String = "",
    val hunt: DailyBoard? = null,
    val canScoreToday: Boolean = true,
    val results: List<DailyResult> = emptyList(),
    val stats: DailyStats = DailyStats(),
) {
    val todayResult get() = results.find { it.date == today }
    companion object {
        fun from(progress: DailyProgress, today: String) = DailyUi(today,
            progress.active?.let { DailyBoard(it.id, it.date, it.config, it.knowledge, it.scored, it.finished, it.abandoned,
                it.fleet.takeUnless { _ -> it.active }) },
            progress.lastScoredDate == null || today > progress.lastScoredDate, progress.results, progress.stats)
    }
}
