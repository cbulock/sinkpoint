package com.cbulock.sinkpoint.data

import com.cbulock.sinkpoint.game.*
import kotlinx.serialization.Serializable

@Serializable data class BestCategory(val mode: String, val difficulty: Difficulty?, val config: GameConfig) {
    val key: String get() = "$mode:${difficulty?.name ?: "LOCAL"}:${config.size}:${config.lengths.joinToString(",")}:${config.touching}:${config.rule}:${config.fireAgainOnHit}"
    fun validate() {
        config.validate()
        require(mode in listOf("AI", "NEARBY") && (mode == "AI") == (difficulty != null))
        require(this == of(mode, difficulty, config))
    }
    companion object {
        fun of(mode: String, difficulty: Difficulty?, config: GameConfig) = BestCategory(mode, difficulty,
            config.copy(lengths = config.lengths.sortedDescending(), fireAgainOnHit = config.rule == TurnRule.CLASSIC && config.fireAgainOnHit))
    }
}

@Serializable data class ShotScore(val matchId: String, val shots: Int, val hits: Int, val date: Long) {
    fun moreAccurateThan(other: ShotScore) = hits.toLong() * other.shots > other.hits.toLong() * shots
    fun validate() { require(matchId.isNotBlank() && shots > 0 && hits in 1..shots && date >= 0) }
}

@Serializable data class PersonalBest(
    val category: BestCategory,
    val fewestShots: ShotScore? = null,
    val accuracy: ShotScore? = null,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val streakMatchId: String? = null,
) {
    fun record(match: MatchState, now: Long): PersonalBest {
        require(match.phase == Phase.FINISHED)
        val won = match.winner == match.localId
        val streak = if (won) currentStreak + 1 else 0
        val score = if (won && match.resigned == null) ShotScore(match.id, match.outgoing.tried.size, match.outgoing.hits.size, now) else null
        return copy(
            fewestShots = if (score != null && (fewestShots == null || score.shots < fewestShots.shots)) score else fewestShots,
            accuracy = if (score != null && (accuracy == null || score.moreAccurateThan(accuracy))) score else accuracy,
            currentStreak = streak, longestStreak = maxOf(longestStreak, streak),
            streakMatchId = if (streak > longestStreak) match.id else streakMatchId,
        )
    }
    fun improvedBy(id: String) = fewestShots?.matchId == id || accuracy?.matchId == id || streakMatchId == id
    fun validate() {
        category.validate(); fewestShots?.validate(); accuracy?.validate()
        require(currentStreak in 0..longestStreak)
    }
}
