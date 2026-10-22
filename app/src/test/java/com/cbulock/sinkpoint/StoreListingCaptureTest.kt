package com.cbulock.sinkpoint

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.cbulock.sinkpoint.data.*
import com.cbulock.sinkpoint.game.*
import com.cbulock.sinkpoint.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Reproducible native Compose renders for store artwork; no radio/device validation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreListingCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun captureSet(group: String, full: Boolean = false) {
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>(), MemoryRepository(), NoRadio())
        val config = GameConfig(fireAgainOnHit = false)
        var match = MatchState("store-demo", config, "you", "ai", "you",
            Fleet(listOf(Piece(0, 0, true), Piece(1, 20, true), Piece(2, 40, true), Piece(3, 60, true), Piece(4, 80, true))), true, true)
        listOf(
            Move("1", 0, "you", listOf(14), Outcome(listOf(14), emptyList())),
            Move("2", 1, "ai", listOf(0), Outcome(listOf(0), emptyList())),
            Move("3", 2, "you", listOf(15), Outcome(listOf(15), emptyList())),
            Move("4", 3, "ai", listOf(1), Outcome(listOf(1), emptyList())),
            Move("5", 4, "you", listOf(16), Outcome(listOf(16), listOf(SunkPiece(2, listOf(14, 15, 16))))),
            Move("6", 5, "ai", listOf(55), Outcome(emptyList(), emptyList())),
        ).forEach { match = match.append(it) }
        val ui = mutableStateOf(UiState(loaded = true, preferences = Preferences(theme = ThemeMode.LIGHT)))
        compose.setContent { SinkpointTheme(ui.value.preferences.theme) { SinkpointApp(ui.value, vm) } }
        fun shot(name: String, state: UiState) {
            compose.runOnIdle { ui.value = state }
            compose.waitForIdle()
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val folder = File("../store-listing/$group").apply { mkdirs() }
                File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        val light = ui.value
        shot("01-classic-gameplay", light.copy(screen = Screen.GAME, match = match))
        shot("02-fleet-placement", light.copy(screen = Screen.GAME, match = match.copy(moves = emptyList(), ready = false)))
        shot("03-dark-theme", light.copy(screen = Screen.GAME, match = match, preferences = Preferences(theme = ThemeMode.DARK)))
        shot("04-game-options", light.copy(screen = Screen.OPTIONS))
        if (full) {
            shot("05-main-menu", light)
            shot("06-nearby-play", light.copy(screen = Screen.LOBBY))
            shot("07-learn-to-play", light.copy(screen = Screen.LEARN))
            shot("08-settings-and-backup", light.copy(screen = Screen.SETTINGS))
        }
    }
    @Test @Config(qualifiers = "w360dp-h640dp-xxhdpi") fun phonePortrait() = captureSet("phone-screenshots", true)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xxhdpi") fun phoneLandscape() = captureSet("phone-landscape-screenshots")
    @Test @Config(qualifiers = "w720dp-h1280dp-xhdpi") fun tabletPortrait() = captureSet("tablet-screenshots/portrait")
    @Test @Config(qualifiers = "w1280dp-h720dp-land-xhdpi") fun tabletLandscape() = captureSet("tablet-screenshots/landscape")
}
