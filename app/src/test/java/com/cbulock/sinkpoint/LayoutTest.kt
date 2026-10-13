package com.cbulock.sinkpoint

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.cbulock.sinkpoint.data.*
import com.cbulock.sinkpoint.game.*
import com.cbulock.sinkpoint.ui.*
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
class LayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun render(name: String, dark: Boolean = false, large: Boolean = false) {
        if (large) RuntimeEnvironment.setFontScale(2f)
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>(), MemoryRepository(), NoRadio())
        val c = GameConfig(fireAgainOnHit = false)
        var m = MatchState("screenshot", c, "you", "ai", "you", Fleet(listOf(Piece(0, 0, true), Piece(1, 20, true), Piece(2, 40, true), Piece(3, 60, true), Piece(4, 80, true))), true, true)
        listOf(
            Move("1", 0, "you", listOf(14), Outcome(listOf(14), emptyList())),
            Move("2", 1, "ai", listOf(0), Outcome(listOf(0), emptyList())),
            Move("3", 2, "you", listOf(15), Outcome(listOf(15), emptyList())),
            Move("4", 3, "ai", listOf(1), Outcome(listOf(1), emptyList())),
            Move("5", 4, "you", listOf(16), Outcome(listOf(16), listOf(SunkPiece(2, listOf(14, 15, 16))))),
            Move("6", 5, "ai", listOf(55), Outcome(emptyList(), emptyList())),
        ).forEach { m = m.append(it) }
        val state = mutableStateOf(UiState(loaded = true, preferences = Preferences(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)))
        compose.setContent { SinkpointTheme(state.value.preferences.theme) { SinkpointApp(state.value, vm) } }
        fun capture(suffix: String) {
            compose.waitForIdle()
            compose.runOnIdle {
                val view = android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull { it.isShown && it.width > 0 } ?: compose.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val folder = File("build/outputs/screenshots").apply { mkdirs() }
                File(folder, "$name-$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        capture("home")
        listOf("Solo", "Multiplayer", "Learn to play", "Statistics").forEach { label -> compose.onNodeWithText(label).performScrollTo().assertIsDisplayed() }
        compose.runOnIdle { state.value = state.value.copy(screen = Screen.GAME, match = m) }
        capture("game")
        compose.onNodeWithText("Fire").assertIsDisplayed()
        compose.onNodeWithText("Fit").assertDoesNotExist()
        compose.onNodeWithContentDescription("Zoom in").assertDoesNotExist()
        compose.onNodeWithContentDescription("Zoom out").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(match = m.copy(moves = emptyList(), ready = false)) }
        capture("placement")
        if (!large && RuntimeEnvironment.getApplication().resources.configuration.screenWidthDp < 640) compose.onNodeWithContentDescription("J10, untried").assertIsDisplayed()
        compose.onNodeWithText("Ready").assertIsDisplayed()
        compose.onNodeWithText("Random").assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { state.value = state.value.copy(screen = Screen.SETTINGS) }
        capture("settings")
        compose.onNodeWithText("Import backup").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(screen = Screen.GAME, match = m.copy(resigned = "ai"), resultVisible = true) }
        capture("result")
        compose.onNodeWithText("View board").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Main menu").onLast().assertIsDisplayed()
    }
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun phoneLight() = render("phone-light")
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun phoneDark() = render("phone-dark", dark = true)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi") fun landscapeLight() = render("landscape-light")
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi") fun landscapeDark() = render("landscape-dark", dark = true)
    @Test @Config(qualifiers = "w800dp-h1280dp-xhdpi") fun tabletLight() = render("tablet-light")
    @Test @Config(qualifiers = "w800dp-h1280dp-xhdpi") fun tabletDark() = render("tablet-dark", dark = true)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun largeTextLight() = render("large-text-light", large = true)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun largeTextDark() = render("large-text-dark", dark = true, large = true)
}

