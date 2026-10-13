package com.cbulock.sinkpoint

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.cbulock.sinkpoint.data.ThemeMode
import com.cbulock.sinkpoint.game.*
import com.cbulock.sinkpoint.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "w673dp-h700dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GameplayLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val config = GameConfig(fireAgainOnHit = false)
    private val fleet = Fleet(config.lengths.indices.map { Piece(it, it * 20, true) })
    private val match = MatchState("fleet-status", config, "you", "ai", "you", fleet, true, true,
        listOf(Move("target", 0, "you", listOf(14, 15, 16), Outcome(listOf(14, 15, 16), listOf(SunkPiece(2, listOf(14, 15, 16))))),
            Move("own", 1, "ai", listOf(0, 8), Outcome(listOf(0), emptyList()))))

    @Test fun squareLayoutUsesTwoLargeBoardsWithControlsBelow() {
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>(), MemoryRepository(), NoRadio())
        compose.setContent { SinkpointTheme(ThemeMode.DARK) { SinkpointApp(UiState(loaded = true, screen = Screen.GAME, match = match), vm) } }
        val target = compose.onNodeWithTag("target-play-board").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val own = compose.onNodeWithTag("own-play-board").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertTrue("Boards should use nearly half of the inner screen", target.width >= 300f * density)
        assertEquals(target.width, own.width, 1f)
        assertTrue(target.right < own.left)
        val fire = compose.onNodeWithText("Fire").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Actions belong below the boards", fire.top > target.bottom)
        compose.onNodeWithText("You:", substring = true).assertDoesNotExist()
        for (id in config.lengths.indices) {
            compose.onNodeWithContentDescription("Your fleet, ship ${id + 1}, length ${config.lengths[id]}").assertIsDisplayed()
            compose.onNodeWithContentDescription("Opponent fleet, ship ${id + 1}, length ${config.lengths[id]}").assertIsDisplayed()
        }
        status("Your fleet, ship 1, length 5", "Damaged")
        status("Your fleet, ship 2, length 4", "Intact")
        status("Opponent fleet, ship 3, length 3", "Sunk")
        status("Opponent fleet, ship 4, length 3", "Afloat")
        compose.onNodeWithContentDescription("A1, untried").performClick()
        compose.onNodeWithText("Fire").assertIsEnabled()
        assertEquals(target, compose.onNodeWithTag("target-play-board").fetchSemanticsNode().boundsInRoot)
        assertEquals(fire, compose.onNodeWithText("Fire").fetchSemanticsNode().boundsInRoot)
        capture("fold-inner-game-status")
    }

    @Test fun shipStatusUpdatesOnSinkWithoutAssigningUnknownOpponentHits() {
        val state = mutableStateOf(match)
        compose.setContent { SinkpointTheme(ThemeMode.LIGHT) { FleetStatus(config, state.value.incoming, fleet); } }
        status("Your fleet, ship 1, length 5", "Damaged")
        compose.runOnIdle {
            state.value = match.copy(moves = match.moves + Move("sink", 2, "ai", listOf(1, 2, 3, 4),
                Outcome(listOf(1, 2, 3, 4), listOf(SunkPiece(0, listOf(0, 1, 2, 3, 4))))))
        }
        status("Your fleet, ship 1, length 5", "Sunk")
        status("Your fleet, ship 2, length 4", "Intact")
    }

    @Test fun opponentUnresolvedHitsDoNotLabelSpecificShipsDamaged() {
        val knowledge = Knowledge(config, listOf(Move("hit", 0, "you", listOf(55), Outcome(listOf(55), emptyList()))))
        compose.setContent { SinkpointTheme(ThemeMode.LIGHT) { FleetStatus(config, knowledge) } }
        config.lengths.forEachIndexed { id, length -> status("Opponent fleet, ship ${id + 1}, length $length", "Afloat") }
    }

    private fun status(label: String, status: String) {
        compose.onNodeWithContentDescription(label).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, status))
    }

    private fun capture(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val folder = File("build/outputs/screenshots").apply { mkdirs() }
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
