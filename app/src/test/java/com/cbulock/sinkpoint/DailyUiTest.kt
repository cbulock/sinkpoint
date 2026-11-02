package com.cbulock.sinkpoint

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.cbulock.sinkpoint.game.*
import com.cbulock.sinkpoint.ui.*
import org.junit.Assert.*
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
class DailyUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var progress by mutableStateOf(DailyProgress().start("2026-11-02"))
    private fun show() {
        compose.setContent { SinkpointTheme(com.cbulock.sinkpoint.data.ThemeMode.LIGHT) {
            androidx.compose.material3.Surface { DailyScreen(UiState(loaded = true, daily = DailyUi.from(progress, "2026-11-02")),
                { progress = progress.fire(it) }, { progress = progress.abandon() }, { progress = progress.start("2026-11-02") }, {}) }
        } }
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
    @Test fun selectFireAndAbandonAreExplicitAndPracticeIsLabeled() {
        show()
        compose.onNodeWithTag("daily-fire").assertIsNotEnabled()
        compose.onNodeWithContentDescription("A2, untried", substring = true).performClick()
        compose.onNodeWithTag("daily-fire").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(setOf(1), progress.active!!.knowledge.tried) }
        capture("daily-phone")
        compose.onNodeWithText("End attempt").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(progress.active!!.active) }
        compose.onNodeWithText("End attempt").performClick()
        compose.onNodeWithText("End hunt").performClick()
        compose.onNodeWithText("Practice").performClick()
        compose.onNodeWithText("Practice · No records").assertExists()
        compose.runOnIdle { assertFalse(progress.active!!.scored) }
    }
    @Test fun completedHuntShowsRecordedScoreAndPracticeDoesNotChangeIt() {
        val hunt = progress.active!!
        hunt.fleet.pieces.flatMap { Rules.cells(hunt.config, it) }.forEach { progress = progress.fire(it) }
        show()
        compose.onNodeWithText("Fleet found!").assertExists()
        capture("daily-result")
        compose.onNodeWithText("Practice").performClick()
        compose.runOnIdle { assertEquals(9, progress.results.single().shots) }
        compose.onNodeWithText("Practice · No records").assertExists()
    }
    @Test fun largeTextKeepsFireReachable() {
        RuntimeEnvironment.setFontScale(2f)
        show()
        compose.onNodeWithTag("daily-fire").assertIsDisplayed()
        capture("daily-large-text")
    }
    @Test @Config(qualifiers = "w800dp-h360dp-xhdpi") fun landscapeKeepsFireReachable() {
        show(); compose.onNodeWithTag("daily-fire").assertIsDisplayed()
        compose.onNodeWithContentDescription("H8, untried").assertIsDisplayed()
        capture("daily-landscape")
    }
    @Test @Config(qualifiers = "w800dp-h1100dp-xhdpi") fun tabletShowsBoardAndFire() {
        show(); compose.onNodeWithTag("daily-fire").assertIsDisplayed(); capture("daily-tablet")
    }
}
