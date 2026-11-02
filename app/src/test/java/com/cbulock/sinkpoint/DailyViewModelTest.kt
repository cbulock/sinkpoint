package com.cbulock.sinkpoint

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.cbulock.sinkpoint.data.*
import com.cbulock.sinkpoint.game.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DailyViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var today = LocalDate.of(2026, 11, 2)
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private fun model(repo: MemoryRepository) = GameViewModel(ApplicationProvider.getApplicationContext<Application>(), repo, NoRadio(), { today })
        .also { store.put("daily", it) }

    @Test fun quickPlayPreservesCustomConfigurationAndRemembersChoice() = runTest {
        val custom = GameConfig(7, listOf(5, 2), false)
        val repo = MemoryRepository(AppData(preferences = Preferences(config = custom)))
        val vm = model(repo); runCurrent()
        vm.startQuick(QuickPreset.SKIRMISH, Difficulty.EASY)
        vm.ui.first { it.solo != null }
        assertEquals(GameConfig(6, listOf(3, 2, 2)), repo.data.value.solo!!.match.config)
        assertEquals(custom, repo.data.value.preferences.config)
        assertEquals(QuickPreset.SKIRMISH, repo.data.value.preferences.quickPreset)
        assertEquals(Difficulty.EASY, repo.data.value.preferences.quickDifficulty)
        vm.home()
    }

    @Test fun practiceAfterMidnightReplaysOriginalDateWithoutReservingToday() = runTest {
        val repo = MemoryRepository(); val vm = model(repo); runCurrent()
        vm.startDaily(); runCurrent(); vm.abandonDaily(); runCurrent()
        today = today.plusDays(1); vm.foreground(true); vm.practiceDaily(); runCurrent()
        assertEquals("2026-11-02", vm.ui.value.daily.hunt!!.date)
        assertFalse(vm.ui.value.daily.hunt!!.scored)
        assertTrue(vm.ui.value.daily.canScoreToday)
        assertEquals("2026-11-02", repo.data.value.daily.lastScoredDate)
    }

    @Test fun dailyHasSeparateSaveAndOnlyRevealsAfterCompletion() = runTest {
        val custom = GameConfig(7, listOf(3, 2), false)
        val solo = SoloSave(MatchState("solo", custom, "p", "ai", "p"),
            Fleet(listOf(Piece(0, 0, true), Piece(1, 21, true))), Difficulty.EASY, 1)
        val repo = MemoryRepository(AppData(playerId = "p", preferences = Preferences(config = custom), solo = solo))
        val vm = model(repo); runCurrent()
        vm.startDaily(); runCurrent()
        assertEquals(Screen.DAILY, vm.ui.value.screen)
        assertNull(vm.ui.value.daily.hunt!!.revealedFleet)
        val hunt = repo.data.value.daily.active!!
        hunt.fleet.pieces.flatMap { Rules.cells(hunt.config, it) }.forEach { vm.fireDaily(it); runCurrent() }
        assertTrue(vm.ui.value.daily.hunt!!.finished)
        assertEquals(hunt.fleet, vm.ui.value.daily.hunt!!.revealedFleet)
        assertEquals(1, repo.data.value.daily.stats.completed)
        vm.home(); vm.resumeDaily(); runCurrent()
        assertEquals(1, repo.data.value.daily.stats.completed)
        assertEquals(solo, repo.data.value.solo)
        assertEquals(custom, repo.data.value.preferences.config)
        vm.startDaily(); runCurrent()
        assertFalse(vm.ui.value.daily.hunt!!.scored)
        assertNull(vm.ui.value.daily.hunt!!.revealedFleet)
    }

    @Test fun failedWriteDoesNotExposeUnsavedHuntAndRetryReservesOnce() = runTest {
        val repo = MemoryRepository(); val vm = model(repo); runCurrent()
        repo.failWrites = true; vm.startDaily(); runCurrent()
        assertTrue(vm.ui.value.storageBlocked)
        assertNull(vm.ui.value.daily.hunt)
        assertNull(repo.data.value.daily.lastScoredDate)
        repo.failWrites = false; vm.retryStorage(); runCurrent()
        assertEquals("2026-11-02", repo.data.value.daily.lastScoredDate)
        vm.resumeDaily(); runCurrent()
        val hunt = repo.data.value.daily.active!!
        val targets = hunt.fleet.pieces.flatMap { Rules.cells(hunt.config, it) }
        targets.dropLast(1).forEach { vm.fireDaily(it); runCurrent() }
        repo.failWrites = true; vm.fireDaily(targets.last()); runCurrent()
        assertEquals(0, repo.data.value.daily.stats.completed)
        repo.failWrites = false; vm.retryStorage(); runCurrent()
        assertEquals(1, repo.data.value.daily.stats.completed)
        vm.retryStorage(); runCurrent()
        assertEquals(1, repo.data.value.daily.stats.completed)
    }

    @Test fun queuedDuplicateFireDoesNotShowAnErrorOrRecordTwice() = runTest {
        val repo = MemoryRepository(); val vm = model(repo); runCurrent()
        vm.startDaily(); runCurrent()
        val hunt = repo.data.value.daily.active!!
        val targets = hunt.fleet.pieces.flatMap { Rules.cells(hunt.config, it) }
        targets.forEach { cell -> vm.fireDaily(cell); vm.fireDaily(cell); runCurrent() }
        assertNull(vm.ui.value.error)
        assertEquals(9, repo.data.value.daily.active!!.moves.size)
        assertEquals(1, repo.data.value.daily.stats.completed)
    }

    @Test fun midnightRefreshLeavesRunningHuntIntactAndNewDayNeedsConfirmation() = runTest {
        val repo = MemoryRepository(); val vm = model(repo); runCurrent()
        vm.startDaily(); runCurrent()
        today = today.plusDays(1); vm.foreground(false); vm.foreground(true); runCurrent()
        assertEquals("2026-11-03", vm.ui.value.daily.today)
        assertEquals("2026-11-02", vm.ui.value.daily.hunt!!.date)
        vm.startDaily(); runCurrent()
        assertEquals("2026-11-02", repo.data.value.daily.active!!.date)
        vm.dismissError(); vm.startDaily(replace = true); runCurrent()
        assertEquals("2026-11-03", repo.data.value.daily.active!!.date)
        assertNull(repo.data.value.daily.results.single().shots)
    }
}
