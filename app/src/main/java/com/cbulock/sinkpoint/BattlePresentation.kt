package com.cbulock.sinkpoint

import com.cbulock.sinkpoint.game.MatchState
import com.cbulock.sinkpoint.game.Phase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object BattleTiming {
    const val RESULT_MILLIS = 1_000L
    const val CPU_THINK_MILLIS = 900L
}

enum class BattleStage { TURN, RESULT }

data class BattlePresentation(
    val match: MatchState,
    val stage: BattleStage = BattleStage.TURN,
    val ownBoard: Boolean = match.current != match.localId,
    val focusRevision: Long = 0,
) {
    val displayedMoveCount get() = match.moves.size
    val canFire get() = stage == BattleStage.TURN && match.phase == Phase.PLAYING && match.current == match.localId
}

/** Main-thread presentation only. The authoritative match and protocol never wait for these timers. */
class BattlePresentationController(
    private val scope: CoroutineScope,
    private val onChanged: (resultCompleted: Boolean) -> Unit = {},
) {
    var state: BattlePresentation? = null
        private set
    private var latest: MatchState? = null
    private var active = false
    private var revision = 0L
    private var hold: Job? = null

    fun update(match: MatchState?, enabled: Boolean) {
        val previous = state
        val reset = match == null || !enabled || !active || previous == null || previous.match.id != match.id ||
            match.resigned != null || match.moves.take(previous.displayedMoveCount) != previous.match.moves
        latest = match
        if (reset) {
            hold?.cancel(); hold = null
            if (match != previous?.match || active != enabled || previous?.stage == BattleStage.RESULT) {
                state = match?.let { BattlePresentation(it, focusRevision = ++revision) }
                onChanged(false)
            }
            active = enabled
            return
        }
        active = enabled
        if (previous!!.stage == BattleStage.RESULT) return
        if (match!!.moves.size > previous.displayedMoveCount) presentNext()
        else if (previous.match != match) {
            val own = match.current != match.localId
            state = previous.copy(match = match, ownBoard = own,
                focusRevision = if (own != previous.ownBoard) ++revision else previous.focusRevision)
            onChanged(false)
        }
    }

    private fun presentNext() {
        val match = latest ?: return
        val count = (state?.displayedMoveCount ?: 0) + 1
        val displayed = match.copy(moves = match.moves.take(count))
        state = BattlePresentation(displayed, BattleStage.RESULT,
            displayed.moves.last().attacker == match.peerId, ++revision)
        onChanged(false)
        hold = scope.launch {
            delay(BattleTiming.RESULT_MILLIS)
            hold = null
            val current = latest ?: return@launch
            val shown = state ?: return@launch
            if (shown.displayedMoveCount < current.moves.size) presentNext()
            else {
                val own = if (current.phase == Phase.FINISHED) shown.ownBoard else current.current != current.localId
                state = shown.copy(match = current, stage = BattleStage.TURN, ownBoard = own,
                    focusRevision = if (own != shown.ownBoard) ++revision else shown.focusRevision)
                onChanged(true)
            }
        }
    }
}
