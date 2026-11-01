package com.cbulock.sinkpoint

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.cbulock.sinkpoint.data.*
import com.cbulock.sinkpoint.game.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BattleViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    private fun repository(first: String = "you", length: Int = 3): MemoryRepository {
        val match = MatchState("vm-battle", GameConfig(6, listOf(length)), "you", "ai", first,
            Fleet(listOf(Piece(0, 0, true))), true, true)
        return MemoryRepository(AppData(playerId = "you", solo = SoloSave(match,
            Fleet(listOf(Piece(0, 30, true))), Difficulty.EASY, 1)))
    }
    private fun model(repo: MemoryRepository): GameViewModel =
        GameViewModel(ApplicationProvider.getApplicationContext<Application>(), repo, NoRadio()).also { store.put("model", it) }

    @Test fun directFireCannotBypassResultHoldEvenWhenAHitRetainsTurn() = runTest {
        val repo = repository()
        val vm = model(repo)
        runCurrent(); vm.resumeSolo(); runCurrent()
        vm.fire(listOf(30)); vm.fire(listOf(31)); runCurrent()
        assertEquals(1, repo.data.value.solo!!.match.moves.size)
        assertEquals("you", repo.data.value.solo!!.match.current)
        assertFalse(vm.ui.value.battle!!.canFire)
        advanceTimeBy(999); vm.fire(listOf(31)); runCurrent()
        assertEquals(1, repo.data.value.solo!!.match.moves.size)
        advanceTimeBy(1); runCurrent()
        assertTrue(vm.ui.value.battle!!.canFire)
        vm.fire(listOf(31)); runCurrent()
        assertEquals(2, repo.data.value.solo!!.match.moves.size)
        assertNull(vm.ui.value.error)
    }

    @Test fun winningShotIsSavedBeforeItsResultDialogAndCannotBeOpenedEarly() = runTest {
        val repo = repository(length = 2)
        val vm = model(repo)
        runCurrent(); vm.resumeSolo(); runCurrent()
        vm.fire(listOf(30)); runCurrent()
        advanceTimeBy(1000); runCurrent()
        vm.fire(listOf(31)); runCurrent()
        assertEquals(Phase.FINISHED, repo.data.value.solo!!.match.phase)
        assertEquals(1, repo.data.value.records.size)
        assertFalse(vm.ui.value.resultVisible)
        vm.showResult(true)
        assertFalse(vm.ui.value.resultVisible)
        advanceTimeBy(999); runCurrent()
        assertFalse(vm.ui.value.resultVisible)
        advanceTimeBy(1); runCurrent()
        assertTrue(vm.ui.value.resultVisible)
        vm.showResult(false); vm.home(); vm.resumeSolo(); runCurrent()
        assertFalse(vm.ui.value.resultVisible)
        assertEquals(2, repo.data.value.solo!!.match.moves.size)
    }

    @Test fun cpuWaitsUntilOutgoingResultFinishesThenThinksForAtLeast900Millis() = runTest {
        val repo = repository()
        val vm = model(repo)
        runCurrent(); vm.resumeSolo(); runCurrent()
        vm.fire(listOf(8)); runCurrent()
        advanceTimeBy(1000); runCurrent()
        assertTrue(vm.ui.value.battle!!.ownBoard)
        assertEquals(BattleStage.TURN, vm.ui.value.battle!!.stage)
        advanceTimeBy(899); runCurrent()
        assertEquals(1, repo.data.value.solo!!.match.moves.size)
        advanceTimeBy(1); runCurrent()
        vm.ui.first { it.match!!.moves.size == 2 }
        assertEquals(BattleStage.RESULT, vm.ui.value.battle!!.stage)
        assertTrue(vm.ui.value.battle!!.ownBoard)
        assertFalse(vm.ui.value.battle!!.canFire)
        vm.home(); runCurrent()
    }

    @Test fun backgroundAndNavigationCancelHoldsWithoutReapplyingMoves() = runTest {
        val repo = repository()
        val vm = model(repo)
        runCurrent(); vm.resumeSolo(); runCurrent()
        vm.fire(listOf(30)); runCurrent()
        advanceTimeBy(300); runCurrent()
        vm.foreground(false); runCurrent()
        assertEquals(BattleStage.TURN, vm.ui.value.battle!!.stage)
        vm.foreground(true); runCurrent()
        assertTrue(vm.ui.value.battle!!.canFire)
        advanceTimeBy(2000); runCurrent()
        assertEquals(1, repo.data.value.solo!!.match.moves.size)
        vm.fire(listOf(31)); runCurrent()
        vm.home(); runCurrent()
        advanceTimeBy(2000); runCurrent()
        vm.resumeSolo(); runCurrent()
        assertTrue(vm.ui.value.battle!!.canFire)
        assertEquals(2, repo.data.value.solo!!.match.moves.size)
    }

    @Test fun failedSavePausesAndRetryDisplaysNoHistoricalReplay() = runTest {
        val repo = repository()
        val vm = model(repo)
        runCurrent(); vm.resumeSolo(); runCurrent()
        vm.fire(listOf(30)); runCurrent()
        advanceTimeBy(1000); runCurrent()
        repo.failWrites = true
        vm.fire(listOf(31)); runCurrent()
        assertTrue(vm.ui.value.storageBlocked)
        assertEquals(1, repo.data.value.solo!!.match.moves.size)
        repo.failWrites = false
        vm.retryStorage(); runCurrent()
        assertFalse(vm.ui.value.storageBlocked)
        assertEquals(2, repo.data.value.solo!!.match.moves.size)
        assertEquals(BattleStage.TURN, vm.ui.value.battle!!.stage)
    }

    @Test fun resignationInterruptsTheResultHoldImmediately() = runTest {
        val vm = model(repository())
        runCurrent(); vm.resumeSolo(); runCurrent()
        vm.fire(listOf(30)); runCurrent()
        vm.resign(); runCurrent()
        assertTrue(vm.ui.value.resultVisible)
        assertEquals(BattleStage.TURN, vm.ui.value.battle!!.stage)
        assertEquals("ai", vm.ui.value.match!!.winner)
    }

    @Test fun backgroundCancelsCpuThinkingAndResumeStartsAFreshMinimumDelay() = runTest {
        val repo = repository(first = "ai")
        val vm = model(repo)
        runCurrent(); vm.resumeSolo(); runCurrent()
        advanceTimeBy(500); runCurrent()
        vm.foreground(false); runCurrent()
        advanceTimeBy(3000); runCurrent()
        assertTrue(repo.data.value.solo!!.match.moves.isEmpty())
        assertEquals(1L, repo.data.value.solo!!.randomState)
        vm.foreground(true); runCurrent()
        advanceTimeBy(899); runCurrent()
        assertTrue(repo.data.value.solo!!.match.moves.isEmpty())
        advanceTimeBy(1); runCurrent()
        vm.ui.first { it.match!!.moves.size == 1 }
        assertEquals(BattleStage.RESULT, vm.ui.value.battle!!.stage)
        vm.home(); runCurrent()
    }

    @Test fun backgroundDuringCpuHitHoldSkipsReplayAndDelaysTheNextShotAfterResume() = runTest {
        val firstCell = GameRandom(1).nextInt(36)
        val repo = repository(first = "ai", length = 6)
        repo.data.value = repo.data.value.copy(solo = repo.data.value.solo!!.let {
            it.copy(match = it.match.copy(own = Fleet(listOf(Piece(0, firstCell / 6 * 6, true)))))
        })
        val vm = model(repo)
        runCurrent(); vm.resumeSolo(); runCurrent()
        advanceTimeBy(900); runCurrent()
        vm.ui.first { it.match!!.moves.size == 1 }
        assertTrue(repo.data.value.solo!!.match.moves.single().outcome.hits.isNotEmpty())
        assertEquals(BattleStage.RESULT, vm.ui.value.battle!!.stage)
        vm.foreground(false); runCurrent()
        advanceTimeBy(3000); runCurrent()
        assertEquals(1, repo.data.value.solo!!.match.moves.size)
        vm.foreground(true); runCurrent()
        assertEquals(BattleStage.TURN, vm.ui.value.battle!!.stage)
        assertTrue(vm.ui.value.battle!!.ownBoard)
        advanceTimeBy(899); runCurrent()
        assertEquals(1, repo.data.value.solo!!.match.moves.size)
        advanceTimeBy(1); runCurrent()
        vm.ui.first { it.match!!.moves.size == 2 }
        assertEquals(BattleStage.RESULT, vm.ui.value.battle!!.stage)
        vm.home(); runCurrent()
    }
}
