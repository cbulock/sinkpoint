package com.cbulock.sinkpoint

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.cbulock.sinkpoint.data.ThemeMode
import com.cbulock.sinkpoint.game.*
import com.cbulock.sinkpoint.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GameplayFeedbackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun model() = GameViewModel(ApplicationProvider.getApplicationContext<Application>(), MemoryRepository(), NoRadio())
    private val config = GameConfig(6, listOf(2, 2), rule = TurnRule.SALVO)
    private val match = MatchState("feedback", config, "you", "ai", "ai",
        Fleet(listOf(Piece(0, 0, true), Piece(1, 12, true))), true, true,
        listOf(Move("incoming", 0, "ai", listOf(0, 8), Outcome(listOf(0), emptyList()))))

    private fun checkSelectionDoesNotMoveBoard() {
        val vm = model()
        compose.setContent { SinkpointTheme(ThemeMode.LIGHT) { GameScreen(UiState(loaded = true, mode = Mode.AI, match = match), vm, {}, {}) } }
        val boardCell = compose.onNodeWithContentDescription("A1, untried").fetchSemanticsNode().boundsInRoot
        val fire = compose.onNodeWithText("Fire").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("A1, untried").performClick()
        assertEquals(boardCell, compose.onNodeWithContentDescription("A1, selected target").fetchSemanticsNode().boundsInRoot)
        assertEquals(fire, compose.onNodeWithText("Fire").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("A2, untried").performClick()
        compose.onNodeWithText("Fire").assertIsEnabled()
        assertEquals(boardCell, compose.onNodeWithContentDescription("A1, selected target").fetchSemanticsNode().boundsInRoot)
        assertEquals(fire, compose.onNodeWithText("Fire").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("A1, selected target").performClick()
        compose.onNodeWithContentDescription("A2, selected target").performClick()
        assertEquals(boardCell, compose.onNodeWithContentDescription("A1, untried").fetchSemanticsNode().boundsInRoot)
        assertEquals(fire, compose.onNodeWithText("Fire").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Fire").assertIsNotEnabled()
    }

    @Test fun phoneSelectionDoesNotMoveBoardOrFireButton() = checkSelectionDoesNotMoveBoard()

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun targetViewKeepsOpponentProgressVisibleAndUpdatesDamage() {
        val vm = model()
        val state = mutableStateOf(UiState(loaded = true, mode = Mode.AI, match = match))
        compose.setContent { SinkpointTheme(ThemeMode.DARK) { SinkpointApp(state.value.copy(screen = Screen.GAME), vm) } }
        compose.onNodeWithTag("target-play-board").assertIsDisplayed()
        compose.onNodeWithText("Opponent · Hits 1 · Sunk 0/2").assertIsDisplayed()
        val summary = compose.onNodeWithTag("opponent-progress").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Resign").performScrollTo()
        assertEquals(summary, compose.onNodeWithTag("opponent-progress").assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Fire").assertIsDisplayed()
        compose.runOnIdle {
            state.value = state.value.copy(match = match.copy(moves = match.moves +
                Move("reply", 1, "you", listOf(4, 5), Outcome(emptyList(), emptyList())) +
                Move("sink", 2, "ai", listOf(1, 20), Outcome(listOf(1), listOf(SunkPiece(0, listOf(0, 1)))))))
        }
        compose.onNodeWithText("Opponent · Hits 2 · Sunk 1/2").assertIsDisplayed()
        compose.onNodeWithText("A2 hit · D3 miss").assertIsDisplayed()
        capture("compact-opponent-progress")
        compose.onNodeWithContentDescription("Show on own fleet").performClick()
        compose.onNodeWithTag("own-play-board").assertIsDisplayed()
        compose.onNodeWithText("Back to targeting").performClick()
        compose.onNodeWithTag("target-play-board").assertIsDisplayed()
    }

    @Test fun opponentSummaryIsPresentBeforeTheirFirstShot() {
        val vm = model()
        compose.setContent { SinkpointTheme(ThemeMode.LIGHT) {
            GameScreen(UiState(loaded = true, match = match.copy(moves = emptyList(), first = "you")), vm, {}, {})
        } }
        compose.onNodeWithText("Opponent · Hits 0 · Sunk 0/2").assertIsDisplayed()
        compose.onNodeWithText("No shots at your fleet yet").assertIsDisplayed()
        compose.onNodeWithText("Fire").assertIsNotEnabled()
    }
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun landscapeSelectionDoesNotMoveBoardOrFireButton() = checkSelectionDoesNotMoveBoard()

    @Test fun turnBannerTracksPlayableWaitingAndPausedStates() {
        val vm = model()
        val state = mutableStateOf(UiState(loaded = true, mode = Mode.AI, match = match))
        compose.setContent { SinkpointTheme(ThemeMode.LIGHT) { GameScreen(state.value, vm, {}, {}) } }
        compose.onNodeWithText("Your turn").assertIsDisplayed()
        compose.onNode(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.LiveRegion, androidx.compose.ui.semantics.LiveRegionMode.Polite)).assertExists()
        capture("your-turn-banner")
        compose.runOnIdle { state.value = state.value.copy(exchangePending = true) }
        compose.onNodeWithText("Your turn").assertDoesNotExist()
        compose.onNodeWithText("Saving and confirming the move…").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(exchangePending = false, match = match.copy(moves = match.moves + Move("outgoing", 1, "you", listOf(4, 5), Outcome(emptyList(), emptyList())))) }
        compose.onNodeWithText("Thinking…").assertIsDisplayed()
        compose.onNodeWithText("Fire").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(storageBlocked = true) }
        compose.onNodeWithText("Match paused").assertIsDisplayed()
        compose.onNodeWithText("Your turn").assertDoesNotExist()
    }

    @Test fun latestVolleyIsAccessibleAndPersistsUntilTheNextOpponentMove() {
        val vm = model()
        val state = mutableStateOf(UiState(loaded = true, mode = Mode.AI, match = match))
        compose.setContent { SinkpointTheme(ThemeMode.LIGHT) { GameScreen(state.value, vm, {}, {}) } }
        compose.onNodeWithText("A1 hit · B3 miss").assertExists()
        compose.onNodeWithContentDescription("Show on own fleet").performClick()
        compose.onNodeWithContentDescription("A1, hit").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Latest opponent move"))
        compose.onNodeWithContentDescription("B3, miss").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Latest opponent move"))
        capture("own-latest-volley")
        compose.runOnIdle {
            state.value = state.value.copy(match = match.copy(moves = match.moves + Move("outgoing", 1, "you", listOf(4, 5), Outcome(emptyList(), emptyList()))))
        }
        compose.onNodeWithText("A1 hit · B3 miss").assertExists()
        compose.runOnIdle {
            val current = state.value.match!!
            state.value = state.value.copy(match = current.copy(moves = current.moves + Move("next", 2, "ai", listOf(12, 20), Outcome(listOf(12), emptyList()))))
        }
        compose.onNodeWithText("C1 hit · D3 miss").assertExists()
        compose.onNodeWithContentDescription("A1, hit").assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.StateDescription))
        compose.onNodeWithContentDescription("C1, hit").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Latest opponent move"))
    }

    @Test fun victoryCelebratesAndDefeatDoesNot() {
        val vm = model()
        val state = mutableStateOf(UiState(loaded = true, mode = Mode.AI, match = match.copy(resigned = "ai")))
        compose.mainClock.autoAdvance = false
        compose.setContent { SinkpointTheme(ThemeMode.LIGHT) { ResultDialog(state.value, vm) } }
        compose.onNodeWithContentDescription("Victory trophy and confetti").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(600)
        capture("victory-confetti")
        compose.mainClock.advanceTimeBy(3000)
        compose.onNodeWithContentDescription("Victory trophy and confetti").assertIsDisplayed()
        compose.onNodeWithText("View board").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { state.value = state.value.copy(match = match.copy(resigned = "you")) }
        compose.onNodeWithContentDescription("Victory trophy and confetti").assertDoesNotExist()
    }

    @Test fun finishedTargetBoardRevealsShipsWithoutAllowingShots() {
        val vm = model()
        val fleet = Fleet(listOf(Piece(0, 6, true), Piece(1, 18, true)))
        val shot = Move("shot", 1, "you", listOf(6, 8), Outcome(listOf(6), emptyList()))
        val played = match.copy(moves = match.moves + shot)
        val state = mutableStateOf(UiState(loaded = true, mode = Mode.AI, match = played, opponentFleet = fleet))
        compose.setContent { SinkpointTheme(ThemeMode.LIGHT) { GameScreen(state.value, vm, {}, {}) } }
        compose.onNodeWithContentDescription("B2, untried").assertExists()
        compose.onNodeWithContentDescription("B2, opponent ship").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(match = played.copy(resigned = "you")) }
        compose.onNodeWithContentDescription("B1, hit").assertExists()
        compose.onNodeWithContentDescription("B3, miss").assertExists()
        compose.onNodeWithContentDescription("B2, opponent ship").assertExists().assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsActions.OnClick))
        compose.onNodeWithContentDescription("D1, opponent ship").assertExists()
        compose.onNodeWithContentDescription("Opponent fleet, ship 1, length 2").assertExists()
        compose.onNodeWithText("Fire").assertDoesNotExist()
        capture("finished-opponent-reveal")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull { it.isShown && it.width > 0 } ?: compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val folder = File("build/outputs/screenshots").apply { mkdirs() }
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
