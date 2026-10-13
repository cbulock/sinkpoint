package com.cbulock.sinkpoint

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.cbulock.sinkpoint.data.ThemeMode
import com.cbulock.sinkpoint.game.Difficulty
import com.cbulock.sinkpoint.ui.HomeScreen
import com.cbulock.sinkpoint.ui.SinkpointTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeMenuTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var soloResumes = 0
    private var nearbyResumes = 0
    private var multiplayerStarts = 0
    private var chosen: Difficulty? = null

    private fun show(state: UiState, dark: Boolean = false) {
        compose.setContent {
            SinkpointTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                androidx.compose.material3.Surface {
                    HomeScreen(state, { chosen = it }, { multiplayerStarts++ }, { soloResumes++ }, { nearbyResumes++ }, {})
                }
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val folder = File("build/outputs/screenshots").apply { mkdirs() }
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun bothModesAreVisibleAndSoloRequiresExplicitDifficulty() {
        show(UiState(loaded = true))
        compose.onNodeWithTag("new-multiplayer").assertIsDisplayed().performClick()
        assertEquals(1, multiplayerStarts)
        compose.onNodeWithText("Continue playing").assertDoesNotExist()
        compose.onNodeWithTag("new-solo").assertIsDisplayed().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(null, chosen)
        compose.onNodeWithTag("new-solo").performClick()
        compose.onNodeWithText("Hard").performScrollTo().performClick()
        assertEquals(Difficulty.HARD, chosen)
        compose.onNodeWithText("Choose your challenge").assertDoesNotExist()
    }

    @Test fun eachSavedGameResumesItsOwnModeAndPlacementHasUsefulStatus() {
        show(UiState(loaded = true, difficulty = Difficulty.HARD,
            solo = ResumeSummary(false, 0, 8, true), nearby = ResumeSummary(false, 12, 10, false)))
        compose.onNodeWithTag("new-multiplayer").assertIsDisplayed()
        capture("home-two-saves")
        compose.onNodeWithText("8 × 8 · Fleet setup").assertExists()
        compose.onNodeWithText("10 × 10 · 12 shots fired").assertExists()
        compose.onNodeWithContentDescription("Continue solo game").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Continue multiplayer game").performScrollTo().performClick()
        assertEquals(1, soloResumes)
        assertEquals(1, nearbyResumes)
    }

    @Test fun completedGamesAreSeparateFromContinue() {
        show(UiState(loaded = true, solo = ResumeSummary(true, 25, 10, false), nearby = ResumeSummary(true, 18, 8, false)), dark = true)
        compose.onNodeWithText("Continue playing").assertDoesNotExist()
        compose.onNodeWithText("Recent games").performScrollTo().assertIsDisplayed()
        capture("home-completed-dark")
        compose.onNodeWithContentDescription("View last solo board").performScrollTo().performClick()
        compose.onNodeWithContentDescription("View last multiplayer board").performScrollTo().performClick()
        assertEquals(1, soloResumes)
        assertEquals(1, nearbyResumes)
    }

    @Test fun largeTextKeepsSavedGamesAndDifficultyReachable() {
        RuntimeEnvironment.setFontScale(2f)
        show(UiState(loaded = true, solo = ResumeSummary(false, 4, 10, false)), dark = true)
        capture("home-large-text-dark")
        compose.onNodeWithContentDescription("Continue solo game").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Statistics").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("new-solo").performScrollTo().performClick()
        compose.onNodeWithText("Hard").performScrollTo().performClick()
        assertEquals(Difficulty.HARD, chosen)
    }

    @Test fun storageFailureDisablesNewSoloButKeepsSavedGameAccessible() {
        show(UiState(loaded = true, storageBlocked = true, solo = ResumeSummary(false, 4, 10, false)))
        compose.onNodeWithTag("new-solo").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Continue solo game").performScrollTo().performClick()
        assertEquals(1, soloResumes)
    }
}
