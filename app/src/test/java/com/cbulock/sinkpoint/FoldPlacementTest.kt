package com.cbulock.sinkpoint

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.cbulock.sinkpoint.data.ThemeMode
import com.cbulock.sinkpoint.game.*
import com.cbulock.sinkpoint.ui.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FoldPlacementTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun checkLayout(name: String, large: Boolean = false) {
        if (large) RuntimeEnvironment.setFontScale(2f)
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>(), MemoryRepository(), NoRadio())
        val match = MatchState("fold-placement", GameConfig(), "you", "ai", "you", peerReady = true)
        compose.setContent { SinkpointTheme(ThemeMode.DARK) { SinkpointApp(UiState(loaded = true, screen = Screen.GAME, mode = Mode.AI, match = match), vm) } }
        val heading = compose.onNodeWithText("Place your fleet").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertTrue("Placement title must have room to read normally", heading.width >= 120f * density)
        assertTrue("Placement title must not wrap into a vertical column", heading.height <= 64f * density * if (large) 2f else 1f)
        for (id in 0..4) compose.onNodeWithTag("piece-$id").assertIsDisplayed()
        compose.onNodeWithContentDescription("Rotate").assertIsDisplayed()
        compose.onNodeWithText("Random").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Ready").assertIsDisplayed()
        val board = compose.onNodeWithTag("placement-board").fetchSemanticsNode().boundsInRoot
        val firstPiece = compose.onNodeWithTag("piece-0").fetchSemanticsNode().boundsInRoot
        assertTrue("Tray and board must not overlap", firstPiece.right <= board.left || firstPiece.bottom <= board.top)
        if (!large) compose.onNodeWithContentDescription("J10, untried").assertIsDisplayed()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val folder = File("build/outputs/screenshots").apply { mkdirs() }
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test @Config(qualifiers = "w673dp-h700dp-xhdpi") fun nearSquareInnerScreen() = checkLayout("fold-inner-placement")
    @Test @Config(qualifiers = "w600dp-h650dp-xhdpi") fun smallerInnerScreen() = checkLayout("fold-small-placement")
    @Test @Config(qualifiers = "w700dp-h600dp-land-xhdpi") fun rotatedInnerScreen() = checkLayout("fold-rotated-placement")
    @Test @Config(qualifiers = "w673dp-h840dp-xhdpi") fun tallerInnerScreen() = checkLayout("fold-tall-placement")
    @Test @Config(qualifiers = "w673dp-h700dp-xhdpi") fun innerScreenLargeText() = checkLayout("fold-large-text-placement", large = true)
}
