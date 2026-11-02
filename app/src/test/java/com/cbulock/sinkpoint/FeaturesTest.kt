package com.cbulock.sinkpoint

import com.cbulock.sinkpoint.game.*
import com.cbulock.sinkpoint.data.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class FeaturesTest {
    @Test fun dailyHistoryCapDoesNotLoseLifetimeRecordsOrAllowOldScoredAttempts() {
        var progress = DailyProgress()
        repeat(105) { day ->
            progress = progress.start(LocalDate.of(2026, 1, 1).plusDays(day.toLong()).toString())
            val hunt = progress.active!!
            hunt.fleet.pieces.flatMap { Rules.cells(hunt.config, it) }.forEach { progress = progress.fire(it) }
        }
        assertEquals(100, progress.results.size)
        assertEquals(105, progress.stats.completed)
        assertEquals("2026-01-01", progress.stats.best!!.date)
        progress = progress.start("2026-01-01")
        assertFalse(progress.active!!.scored)
        progress.validate()
    }

    @Test fun legacyBackupKeepsStatisticsAndDoesNotInventBests() {
        val backup = """{"version":1,"preferences":{},"solo":null,"records":[],"totals":{"AI:NORMAL:CLASSIC":{"played":20,"wins":10,"shots":300,"hits":200}},"recordedSoloId":null}"""
        val current = AppData(daily = DailyProgress().start("2026-11-02"))
        val restored = SaveFormat.restore(current, SaveFormat.decode(backup))
        assertEquals(20, restored.totals.values.single().played)
        assertTrue(restored.bests.isEmpty())
        assertNull(restored.daily.active)
    }

    @Test fun corruptedDailyOutcomeAndVersionAreRejected() {
        val original = DailyProgress().start("2026-11-02").fire(1)
        val hunt = original.active!!
        assertTrue(runCatching { original.copy(active = hunt.copy(version = 2)).validate() }.isFailure)
        val wrong = hunt.moves.single().copy(outcome = Outcome(emptyList(), emptyList()))
        assertTrue(runCatching { original.copy(active = hunt.copy(moves = listOf(wrong))).validate() }.isFailure)
        assertTrue(runCatching { original.copy(lastScoredDate = null).validate() }.isFailure)
        assertTrue(runCatching { original.fire(1) }.isFailure)
    }
    @Test fun presetsHaveFixedRulesAndCustomIsUntouched() {
        val custom = GameConfig(7, listOf(6, 2), false)
        assertEquals(custom, QuickPreset.CUSTOM.config(custom))
        assertEquals(GameConfig(6, listOf(3, 2, 2)), QuickPreset.SKIRMISH.config(custom))
        assertEquals(GameConfig(), QuickPreset.CLASSIC.config(custom))
        assertEquals(TurnRule.SALVO, QuickPreset.SALVO.config(custom).rule)
    }

    @Test fun dailyFleetIsStableAndOnlyFirstAttemptCounts() {
        val first = DailyHunt.create("2026-11-02")
        assertEquals(first.fleet, DailyHunt.create("2026-11-02").fleet)
        assertEquals(Fleet(listOf(Piece(0, 1, true), Piece(1, 10, false), Piece(2, 45, false))), first.fleet)
        assertTrue(Rules.validFleet(first.config, first.fleet))
        assertEquals(GameConfig(8, listOf(4, 3, 2)), first.config)
        var progress = DailyProgress().start("2026-11-02")
        assertTrue(progress.active!!.scored)
        val cells = progress.active!!.fleet.pieces.flatMap { Rules.cells(first.config, it) }
        cells.forEach { progress = progress.fire(it) }
        assertEquals(9, progress.results.single().shots)
        assertEquals(1, progress.stats.completed)
        assertTrue(progress.active!!.finished)
        progress = progress.start("2026-11-02")
        assertFalse(progress.active!!.scored)
        cells.forEach { progress = progress.fire(it) }
        assertEquals(1, progress.stats.completed)
        assertEquals(1, progress.results.size)
    }

    @Test fun rolloverRequiresExplicitAbandonAndClockRollbackIsPractice() {
        val day = DailyProgress().start("2026-11-02").fire(0)
        assertEquals(day, day.start("2026-11-02"))
        assertTrue(runCatching { day.start("2026-11-03") }.isFailure)
        val next = day.start("2026-11-03", replace = true)
        assertNull(next.results.single().shots)
        assertEquals("2026-11-02", next.results.single().date)
        assertTrue(next.active!!.scored)
        val past = next.start("2026-11-01", replace = true)
        assertFalse(past.active!!.scored)
        assertEquals("2026-11-03", past.lastScoredDate)
    }

    private fun victory(id: String = "win", miss: Boolean = false): MatchState {
        val config = GameConfig(6, listOf(2))
        var match = MatchState(id, config, "p", "ai", "p", Fleet(listOf(Piece(0, 6, true))), true, true)
        if (miss) {
            match = match.append(Move("m", 0, "p", listOf(35), Outcome(emptyList(), emptyList())))
            match = match.append(Move("a", 1, "ai", listOf(0), Outcome(emptyList(), emptyList())))
        }
        match = match.append(Move("h", match.moves.size, "p", listOf(0), Outcome(listOf(0), emptyList())))
        return match.append(Move("s", match.moves.size, "p", listOf(1), Outcome(listOf(1), listOf(SunkPiece(0, listOf(0, 1))))))
    }

    @Test fun bestsNormalizeRulesCompareFractionsAndExcludeResignedShotRecords() {
        val config = GameConfig(8, listOf(2, 3), rule = TurnRule.SALVO)
        assertEquals(BestCategory.of("AI", Difficulty.NORMAL, config),
            BestCategory.of("AI", Difficulty.NORMAL, config.copy(lengths = listOf(3, 2), fireAgainOnHit = false)))
        val category = BestCategory.of("AI", Difficulty.NORMAL, victory().config)
        var best = PersonalBest(category).record(victory("first", true), 1)
        best = best.record(victory("second"), 2)
        assertEquals("second", best.fewestShots!!.matchId)
        assertEquals("second", best.accuracy!!.matchId)
        best = best.record(victory("tie"), 3)
        assertEquals("second", best.accuracy!!.matchId)
        assertEquals(3, best.longestStreak)
        best = best.record(victory("resigned").copy(moves = emptyList(), resigned = "ai"), 4)
        assertEquals(4, best.longestStreak)
        assertEquals(2, best.fewestShots!!.shots)
        best = best.record(victory("loss").copy(moves = emptyList(), resigned = "p"), 5)
        assertEquals(0, best.currentStreak)
        assertTrue(ShotScore("a", 201, 100, 0).moreAccurateThan(ShotScore("b", 203, 100, 0)))
    }

    @Test fun repositoryAndBackupPreserveOnceOnlyRecordsAndIndependentProgress() {
        val match = victory()
        val solo = SoloSave(match, Fleet(listOf(Piece(0, 0, true))), Difficulty.NORMAL, 1)
        val data = AppData(playerId = "p", solo = solo, daily = DailyProgress().start("2026-11-02")).withResults(1)
        assertEquals(1, data.bests.size)
        assertEquals(data, data.withResults(2))
        val restored = SaveFormat.restore(AppData(playerId = "p"), SaveFormat.decode(SaveFormat.encode(data)))
        assertEquals(data.bests, restored.bests)
        assertEquals(data.daily, restored.daily)
        assertEquals(data, restored.withResults(3))
        val legacyFinished = data.copy(bests = emptyMap())
        assertTrue(legacyFinished.withResults(4).bests.isEmpty())
        assertEquals(legacyFinished.records, legacyFinished.withResults(4).records)
    }
}
