package com.cbulock.sinkpoint

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.cbulock.sinkpoint.data.ThemeMode
import com.cbulock.sinkpoint.game.*
import com.cbulock.sinkpoint.ui.GameScreen
import com.cbulock.sinkpoint.ui.SinkpointTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class TurnHapticsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val feedback = mutableListOf<HapticFeedbackType>()
    private val state = mutableStateOf(UiState(
        loaded = true, mode = Mode.NEARBY, connected = true, synchronized = true,
        match = MatchState("haptics", GameConfig(6, listOf(2, 2), rule = TurnRule.SALVO),
            "you", "peer", "peer", Fleet(listOf(Piece(0, 0, true), Piece(1, 12, true))), true, true)
    ))

    private fun show() {
        val model = GameViewModel(ApplicationProvider.getApplicationContext<Application>(), MemoryRepository(), NoRadio())
        val haptics = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { feedback += hapticFeedbackType }
        }
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                SinkpointTheme(ThemeMode.LIGHT) { GameScreen(state.value, model, {}, {}) }
            }
        }
        expectPulses(0)
    }

    private fun update(change: (UiState) -> UiState) {
        compose.runOnIdle { state.value = change(state.value) }
        compose.waitForIdle()
    }

    private fun move(attacker: String, hit: Boolean = false) = update { ui ->
        val match = ui.match!!
        val cell = match.moves.size
        ui.copy(match = match.copy(moves = match.moves + Move("move-$cell", cell, attacker,
            listOf(cell), Outcome(if (hit) listOf(cell) else emptyList(), emptyList()))))
    }

    private fun expectPulses(count: Int) = compose.runOnIdle {
        assertEquals(List(count) { HapticFeedbackType.Confirm }, feedback)
    }

    @Test fun returningTurnWaitsForConfirmationAndDoesNotRepeatOnReconnectOrBusyChanges() {
        show()
        update { it.copy(exchangePending = true) }
        move("peer")
        expectPulses(0)
        update { it.copy(exchangePending = false, storageBlocked = true) }
        expectPulses(0)
        update { it.copy(storageBlocked = false) }
        expectPulses(1)
        update { it.copy(busy = true, connected = false, synchronized = false) }
        update { it.copy(busy = false, connected = true, synchronized = true) }
        expectPulses(1)
        move("you")
        expectPulses(1)
        move("peer")
        expectPulses(2)
    }

    @Test fun disabledHapticsAndSoloTurnsStaySilent() {
        state.value = state.value.copy(preferences = state.value.preferences.copy(haptics = false))
        show()
        move("peer")
        expectPulses(0)
        update { it.copy(preferences = it.preferences.copy(haptics = true)) }
        expectPulses(0)
        update { it.copy(mode = Mode.AI) }
        move("you")
        move("peer")
        expectPulses(0)
    }

    @Test fun hitRepeatOnlyPulsesWhenOpponentActuallyYieldsAndNeverForFinishedMatch() {
        state.value = state.value.copy(match = state.value.match!!.let {
            it.copy(config = it.config.copy(rule = TurnRule.CLASSIC, fireAgainOnHit = true))
        })
        show()
        move("peer", hit = true)
        expectPulses(0)
        move("peer")
        expectPulses(1)
        move("you", hit = true)
        expectPulses(1)
        move("you")
        update { it.copy(match = it.match!!.copy(resigned = "peer")) }
        move("peer")
        expectPulses(1)
    }

    @Test fun openingAnExistingLocalTurnDoesNotPulse() {
        state.value = state.value.copy(match = state.value.match!!.copy(first = "you"))
        show()
        update { it.copy(busy = true) }
        update { it.copy(busy = false) }
        expectPulses(0)
    }
}
