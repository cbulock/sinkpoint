package com.cbulock.sinkpoint

import com.cbulock.sinkpoint.game.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BattlePresentationTest {
    private val initial = MatchState("presentation", GameConfig(6, listOf(2)), "you", "peer", "you",
        Fleet(listOf(Piece(0, 0, true))), true, true)
    private fun shot(match: MatchState, attacker: String, cell: Int, hit: Boolean = false) =
        match.copy(moves = match.moves + Move("shot-$cell", match.moves.size, attacker, listOf(cell),
            Outcome(if (hit) listOf(cell) else emptyList(), emptyList())))

    @Test fun outgoingMissStaysVisibleForOneSecondBeforeFollowingOpponent() = runTest {
        val controller = BattlePresentationController(backgroundScope)
        controller.update(initial, true)
        assertTrue(controller.state!!.canFire)
        controller.update(shot(initial, "you", 8), true)
        assertEquals(BattleStage.RESULT, controller.state!!.stage)
        assertFalse(controller.state!!.ownBoard)
        assertFalse(controller.state!!.canFire)
        advanceTimeBy(999); runCurrent()
        assertFalse(controller.state!!.ownBoard)
        advanceTimeBy(1); runCurrent()
        assertEquals(BattleStage.TURN, controller.state!!.stage)
        assertTrue(controller.state!!.ownBoard)
    }

    @Test fun incomingResultHoldsBeforeLocalTurnAndDuplicatesDoNotRestartIt() = runTest {
        val controller = BattlePresentationController(backgroundScope)
        val waiting = initial.copy(first = "peer")
        controller.update(waiting, true)
        assertTrue(controller.state!!.ownBoard)
        val moved = shot(waiting, "peer", 8)
        controller.update(moved, true)
        val revision = controller.state!!.focusRevision
        advanceTimeBy(600); runCurrent()
        controller.update(moved, true)
        assertEquals(revision, controller.state!!.focusRevision)
        advanceTimeBy(400); runCurrent()
        assertTrue(controller.state!!.canFire)
        assertFalse(controller.state!!.ownBoard)
    }

    @Test fun rapidOpponentHitStreakDisplaysEveryCommittedMoveInOrder() = runTest {
        val controller = BattlePresentationController(backgroundScope)
        val waiting = initial.copy(first = "peer")
        controller.update(waiting, true)
        val one = shot(waiting, "peer", 0, true)
        val two = shot(one, "peer", 1, true)
        val three = shot(two, "peer", 8)
        controller.update(one, true)
        controller.update(three, true)
        assertEquals(1, controller.state!!.displayedMoveCount)
        advanceTimeBy(1000); runCurrent()
        assertEquals(2, controller.state!!.displayedMoveCount)
        assertTrue(controller.state!!.ownBoard)
        advanceTimeBy(1000); runCurrent()
        assertEquals(3, controller.state!!.displayedMoveCount)
        assertFalse(controller.state!!.canFire)
        advanceTimeBy(1000); runCurrent()
        assertTrue(controller.state!!.canFire)
    }

    @Test fun localHitRetainsTargetBoardAndSalvoAppearsAsOneResult() = runTest {
        val controller = BattlePresentationController(backgroundScope)
        controller.update(initial, true)
        controller.update(shot(initial, "you", 8, true), true)
        advanceTimeBy(1000); runCurrent()
        assertFalse(controller.state!!.ownBoard)
        assertTrue(controller.state!!.canFire)
        val salvo = initial.copy(id = "salvo", config = GameConfig(6, listOf(2, 2), rule = TurnRule.SALVO))
        controller.update(salvo, true)
        controller.update(salvo.copy(moves = listOf(Move("volley", 0, "you", listOf(8, 9), Outcome(listOf(8), emptyList())))), true)
        assertEquals(listOf(8, 9), controller.state!!.match.moves.single().targets)
        advanceTimeBy(1000); runCurrent()
        assertTrue(controller.state!!.ownBoard)
    }

    @Test fun pauseAndResumeSkipHistoricalPlaybackAndCancelOldTimers() = runTest {
        val controller = BattlePresentationController(backgroundScope)
        controller.update(initial, true)
        val one = shot(initial, "you", 8)
        controller.update(one, true)
        advanceTimeBy(300); runCurrent()
        controller.update(one, false)
        val two = shot(one, "peer", 9)
        controller.update(two, false)
        controller.update(two, true)
        assertEquals(BattleStage.TURN, controller.state!!.stage)
        assertEquals(2, controller.state!!.displayedMoveCount)
        assertTrue(controller.state!!.canFire)
        val revision = controller.state!!.focusRevision
        advanceTimeBy(3000); runCurrent()
        assertEquals(revision, controller.state!!.focusRevision)
    }

    @Test fun finalShotCompletesOnlyAfterHoldAndResignationCancelsImmediately() = runTest {
        var completed = 0
        val controller = BattlePresentationController(backgroundScope) { if (it) completed++ }
        val before = initial.copy(moves = listOf(Move("hit", 0, "you", listOf(6), Outcome(listOf(6), emptyList()))))
        controller.update(before, true)
        val finished = before.copy(moves = before.moves + Move("win", 1, "you", listOf(7),
            Outcome(listOf(7), listOf(SunkPiece(0, listOf(6, 7))))))
        controller.update(finished, true)
        assertEquals(Phase.FINISHED, controller.state!!.match.phase)
        assertEquals(0, completed)
        advanceTimeBy(999); runCurrent()
        assertEquals(0, completed)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, completed)
        controller.update(initial.copy(id = "resign"), true)
        controller.update(shot(initial.copy(id = "resign"), "you", 8), true)
        controller.update(controller.state!!.match.copy(resigned = "peer"), true)
        assertEquals(BattleStage.TURN, controller.state!!.stage)
        advanceTimeBy(2000); runCurrent()
        assertEquals(1, completed)
    }

    @Test fun differentMatchNeverReceivesPreviousMatchTimer() = runTest {
        val controller = BattlePresentationController(backgroundScope)
        controller.update(initial, true)
        controller.update(shot(initial, "you", 8), true)
        controller.update(initial.copy(id = "new", first = "peer"), true)
        advanceTimeBy(2000); runCurrent()
        assertEquals("new", controller.state!!.match.id)
        assertEquals(0, controller.state!!.displayedMoveCount)
        assertTrue(controller.state!!.ownBoard)
    }
}
