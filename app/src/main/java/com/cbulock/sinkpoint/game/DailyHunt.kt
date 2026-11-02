package com.cbulock.sinkpoint.game

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.Random
import java.util.UUID

/** Version 1 is deliberately independent of the ordinary/AI fleet generator. */
@Serializable data class DailyHunt(
    val id: String,
    val date: String,
    val version: Int = 1,
    val config: GameConfig = CONFIG,
    val fleet: Fleet,
    val moves: List<Move> = emptyList(),
    val scored: Boolean = true,
    val abandoned: Boolean = false,
) {
    val knowledge get() = Knowledge(config, moves)
    val finished get() = knowledge.sunk.size == config.lengths.size
    val active get() = !finished && !abandoned
    fun fire(cell: Int): DailyHunt {
        require(active)
        val outcome = Rules.resolve(config, fleet, knowledge.tried, listOf(cell))
        return copy(moves = moves + Move("$id:${moves.size}", moves.size, "hunter", listOf(cell), outcome))
    }
    fun validate() {
        require(id.isNotBlank() && LocalDate.parse(date).toString() == date && version == 1 && config == CONFIG)
        require(Rules.validFleet(config, fleet) && !(finished && abandoned))
        var replay = copy(moves = emptyList(), abandoned = false)
        moves.forEach { move ->
            require(move.targets.size == 1)
            replay = replay.fire(move.targets.single())
            require(replay.moves.last() == move)
        }
    }
    companion object {
        val CONFIG = GameConfig(8, listOf(4, 3, 2))
        fun create(date: String): DailyHunt {
            val random = Random(LocalDate.parse(date).toEpochDay() xor 0x53494E4B504F494EL)
            val pieces = mutableListOf<Piece>()
            val occupied = mutableSetOf<Int>()
            CONFIG.lengths.indices.forEach { id ->
                val candidates = (0 until 64).flatMap { start -> listOf(Piece(id, start, true), Piece(id, start, false)) }
                    .filter { piece -> Rules.cells(CONFIG, piece).let { it.isNotEmpty() && it.none(occupied::contains) } }
                val piece = candidates[random.nextInt(candidates.size)]
                pieces += piece; occupied += Rules.cells(CONFIG, piece)
            }
            return DailyHunt(UUID.randomUUID().toString(), date, fleet = Fleet(pieces))
        }
    }
}

@Serializable data class DailyResult(val date: String, val shots: Int?, val version: Int = 1, val config: GameConfig = DailyHunt.CONFIG) {
    val hits get() = config.lengths.sum()
    fun validate() {
        require(LocalDate.parse(date).toString() == date && version == 1 && config == DailyHunt.CONFIG)
        require(shots == null || shots in hits..config.size * config.size)
    }
}

@Serializable data class DailyStats(val completed: Int = 0, val best: DailyResult? = null) {
    fun record(result: DailyResult): DailyStats {
        val shots = result.shots ?: return this
        return copy(completed = completed + 1, best = if (best == null || shots < requireNotNull(best.shots)) result else best)
    }
    fun validate() { require(completed >= 0 && (completed == 0) == (best == null)); best?.validate(); require(best == null || best.shots != null) }
}

@Serializable data class DailyProgress(
    val active: DailyHunt? = null,
    val lastScoredDate: String? = null,
    val results: List<DailyResult> = emptyList(),
    val stats: DailyStats = DailyStats(),
) {
    fun start(date: String, replace: Boolean = false): DailyProgress {
        LocalDate.parse(date)
        if (active?.active == true && active.date == date && !replace) return this
        require(active?.active != true || replace) { "Confirm abandoning the unfinished hunt" }
        val previous = if (active?.active == true) abandon() else this
        val scored = lastScoredDate == null || date > lastScoredDate
        val hunt = if (active?.date == date) active.copy(id = UUID.randomUUID().toString(), moves = emptyList(), abandoned = false, scored = scored)
            else DailyHunt.create(date).copy(scored = scored)
        return previous.copy(active = hunt, lastScoredDate = if (scored) date else lastScoredDate)
    }
    fun fire(cell: Int): DailyProgress {
        val hunt = requireNotNull(active).fire(cell)
        val progress = copy(active = hunt)
        return if (hunt.finished && hunt.scored) progress.record(DailyResult(hunt.date, hunt.knowledge.tried.size, hunt.version, hunt.config)) else progress
    }
    fun abandon(): DailyProgress {
        val hunt = requireNotNull(active)
        if (!hunt.active) return this
        val progress = copy(active = hunt.copy(abandoned = true))
        return if (hunt.scored) progress.record(DailyResult(hunt.date, null, hunt.version, hunt.config)) else progress
    }
    private fun record(result: DailyResult): DailyProgress {
        require(results.none { it.date == result.date })
        return copy(results = (listOf(result) + results).take(100), stats = stats.record(result))
    }
    fun validate() {
        active?.validate(); stats.validate()
        lastScoredDate?.let { require(LocalDate.parse(it).toString() == it) }
        require(results.size <= 100 && results.map { it.date }.distinct().size == results.size)
        results.forEach { it.validate(); require(lastScoredDate != null && it.date <= lastScoredDate) }
        active?.takeIf { it.scored }?.let { hunt ->
            require(lastScoredDate != null && hunt.date <= lastScoredDate)
            val result = results.find { it.date == hunt.date }
            require(if (hunt.active) result == null else result != null && result.shots == if (hunt.finished) hunt.knowledge.tried.size else null)
        }
    }
}
