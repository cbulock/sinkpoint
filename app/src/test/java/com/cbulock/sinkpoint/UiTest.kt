package com.cbulock.sinkpoint

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.cbulock.sinkpoint.data.*
import com.cbulock.sinkpoint.game.*
import com.cbulock.sinkpoint.nearby.*
import com.cbulock.sinkpoint.ui.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MemoryRepository(initial: AppData = AppData()) : GameRepository {
    override val data = MutableStateFlow(initial)
    var failWrites = false
    override suspend fun update(transform: (AppData) -> AppData): AppData {
        if (failWrites) throw java.io.IOException("Disk full")
        return transform(data.value).withResults().also { it.validate(); data.value = it }
    }
}
class NoRadio : NearbyTransport {
    override val events = emptyFlow<TransportEvent>()
    override fun available() = true
    override suspend fun host(name: String) = Unit
    override suspend fun discover() = Unit
    override suspend fun connect(peer: Peer, name: String) = Unit
    override suspend fun accept(endpoint: String) = Unit
    override fun reject(endpoint: String) = Unit
    override suspend fun send(message: WireMessage) = Unit
    override fun close() = Unit
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class UiTest {
    @get:Rule val compose = createComposeRule()
    private fun show(repository: MemoryRepository = MemoryRepository()): GameViewModel {
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>(), repository, NoRadio())
        compose.setContent { val state by vm.ui.collectAsState(); SinkpointTheme(state.preferences.theme) { SinkpointApp(state, vm) } }
        compose.waitUntil(5000) { vm.ui.value.loaded }
        return vm
    }
    @Test fun homeOpensOptionsAndPreservesRuleSelection() {
        val repo = MemoryRepository(); show(repo)
        compose.onNodeWithText("Game options").performScrollTo().performClick()
        compose.onAllNodesWithText("6").onFirst().performScrollTo().performClick()
        compose.waitUntil { repo.data.value.preferences.config.size == 6 }
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("6 × 6 · 5 pieces · Classic · Fire again on hit").assertExists()
    }
    @Test fun normalHomeActionStartsTheMiddleDifficulty() {
        val repo = MemoryRepository(); val vm = show(repo)
        compose.onNodeWithTag("new-solo").performClick()
        compose.onNodeWithText("Medium").assertDoesNotExist()
        compose.onNodeWithText("Normal").performScrollTo().performClick()
        compose.waitUntil(5000) {
            compose.waitForIdle()
            repo.data.value.solo != null
        }
        assertEquals(Difficulty.NORMAL, repo.data.value.solo!!.difficulty)
        compose.waitUntil { vm.ui.value.screen == Screen.GAME }
        compose.onNodeWithText("Ready").assertIsDisplayed()
        compose.onNodeWithText("Main menu").performClick()
        compose.onNodeWithText("Statistics").performScrollTo().performClick()
        compose.onNodeWithText("Normal").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Medium").assertDoesNotExist()
    }
    @Test fun repeatHitOptionPersistsAndRestoresDefault() {
        val repo = MemoryRepository(); show(repo)
        compose.onNodeWithText("Game options").performScrollTo().performClick()
        compose.onNodeWithText("Fire again after a hit").performScrollTo().assertIsOn().performClick()
        compose.waitUntil { !repo.data.value.preferences.config.fireAgainOnHit }
        compose.onNodeWithText("Salvo", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Fire again after a hit").assertDoesNotExist()
        compose.onNodeWithText("Classic", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Fire again after a hit").performScrollTo().assertIsOff()
        compose.onNodeWithText("Restore standard rules").performScrollTo().performClick()
        compose.waitUntil { repo.data.value.preferences.config == GameConfig() }
        compose.onNodeWithText("Fire again after a hit").performScrollTo().assertIsOn()
    }
    @Test fun aiContinuesAfterHitsAndHandsBackTurnAfterMiss() {
        val config = GameConfig(6, listOf(6))
        val random = GameRandom(77)
        val firstTarget = random.nextInt(36)
        val own = Fleet(listOf(Piece(0, firstTarget / 6 * 6, true)))
        val m = MatchState("ai-repeat", config, "you", "ai", "ai", own, true, true)
        val repo = MemoryRepository(AppData(solo = SoloSave(m, Fleet(listOf(Piece(0, 0, true))), Difficulty.EASY, 77)))
        val vm = show(repo)
        compose.runOnIdle { vm.resumeSolo() }
        compose.waitUntil(15000) {
            compose.waitForIdle()
            val match = repo.data.value.solo!!.match
            match.moves.size >= 2 && (match.current == "you" || match.phase == Phase.FINISHED)
        }
        val played = repo.data.value.solo!!.match
        assertTrue(played.moves.first().outcome.hits.isNotEmpty())
        assertTrue(played.moves.all { it.attacker == "ai" })
        if (played.phase == Phase.PLAYING) {
            assertTrue(played.moves.last().outcome.hits.isEmpty())
            assertEquals("you", played.current)
        }
        assertNull(vm.ui.value.error)
        played.validate()
        compose.runOnIdle { vm.home() }
    }
    @Test fun accessibleBoardSelectionNeedsExplicitFire() {
        var selected: Int? = null
        compose.setContent { SinkpointTheme(ThemeMode.LIGHT) { FleetBoard("test", GameConfig(6, listOf(2)), enabled = true, onSelect = { selected = it }) } }
        compose.onNodeWithContentDescription("B3, untried").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        assertEquals(8, selected)
    }
    @Test fun unfinishedReplacementCanBeCanceled() {
        val c = GameConfig(6, listOf(2))
        val s = SoloSave(MatchState("existing", c, "player", "ai", "player", peerReady = true), Fleet(listOf(Piece(0, 0, true))), Difficulty.NORMAL, 1)
        val repo = MemoryRepository(AppData(playerId = "player", solo = s)); show(repo)
        compose.onNodeWithTag("new-solo").performClick()
        compose.onNodeWithText("Easy").performScrollTo().performClick()
        compose.onNodeWithText("Replace unfinished match?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals("existing", repo.data.value.solo?.match?.id)
    }
    @Test fun homeContinuesIndependentSavesAndMultiplayerOpensLobby() {
        val config = GameConfig(6, listOf(2))
        val solo = SoloSave(MatchState("solo-save", config, "player", "ai", "player", peerReady = true),
            Fleet(listOf(Piece(0, 0, true))), Difficulty.NORMAL, 1)
        val nearby = NearbySave(MatchState("nearby-save", config, "player", "friend", "player"), host = true)
        val repo = MemoryRepository(AppData(playerId = "player", solo = solo, nearby = nearby))
        val vm = show(repo)
        compose.onNodeWithContentDescription("Continue solo game").performScrollTo().performClick()
        compose.waitUntil { vm.ui.value.screen == Screen.GAME }
        assertEquals("solo-save", vm.ui.value.match?.id)
        compose.onNodeWithText("Main menu").performClick()
        compose.onNodeWithContentDescription("Continue multiplayer game").performScrollTo().performClick()
        compose.waitUntil { vm.ui.value.mode == Mode.NEARBY && vm.ui.value.screen == Screen.GAME }
        assertEquals("nearby-save", vm.ui.value.match?.id)
        compose.onNodeWithText("Main menu").performClick()
        compose.onNodeWithTag("new-multiplayer").performScrollTo().performClick()
        compose.waitUntil { vm.ui.value.screen == Screen.LOBBY }
        assertEquals(solo, repo.data.value.solo)
        assertEquals(nearby, repo.data.value.nearby)
    }
    @Test fun failedWriteKeepsProgressAndRetryCommitsOnce() {
        val repo = MemoryRepository(); val vm = show(repo)
        repo.failWrites = true
        compose.runOnIdle { vm.configure(GameConfig(size = 8)) }
        compose.waitUntil { vm.ui.value.storageBlocked }
        assertEquals(10, repo.data.value.preferences.config.size)
        repo.failWrites = false
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil { repo.data.value.preferences.config.size == 8 }
        assertFalse(vm.ui.value.storageBlocked)
    }
    @Test fun boardGeometryUsesSameTransformForZoomAndHitTesting() {
        val t = BoardTransform(360f, 20f, 2f, androidx.compose.ui.geometry.Offset(-100f, -140f), 10)
        val p = t.origin(55) + androidx.compose.ui.geometry.Offset(t.cell / 2, t.cell / 2)
        assertEquals(55, t.hit(p))
        assertNull(t.hit(androidx.compose.ui.geometry.Offset(1f, 1f)))
    }
    @Test fun winningShotRequiresFireAndRestoredResultDoesNotReplay() {
        val c = GameConfig(6, listOf(2))
        var match = MatchState("finish", c, "player", "ai", "player", Fleet(listOf(Piece(0, 0, true))), true, true)
        match = match.append(Move("one", 0, "player", listOf(6), Outcome(listOf(6), emptyList())))
        val repo = MemoryRepository(AppData(playerId = "player", solo = SoloSave(match, Fleet(listOf(Piece(0, 6, true))), Difficulty.NORMAL, 1)))
        val vm = show(repo)
        compose.runOnIdle { vm.resumeSolo() }
        compose.onNodeWithContentDescription("B2, untried").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        assertNull(vm.ui.value.opponentFleet)
        assertEquals(1, repo.data.value.solo!!.match.moves.size)
        compose.onNodeWithText("Fire").assertIsEnabled().performClick()
        compose.waitUntil { vm.ui.value.resultVisible }
        assertEquals("player", repo.data.value.solo!!.match.winner)
        assertEquals(repo.data.value.solo!!.aiFleet, vm.ui.value.opponentFleet)
        assertEquals(1, repo.data.value.records.size)
        compose.onNodeWithText("View board").performClick()
        compose.runOnIdle { vm.home(); vm.resumeSolo() }
        assertFalse(vm.ui.value.resultVisible)
        assertEquals(repo.data.value.solo!!.aiFleet, vm.ui.value.opponentFleet)
        assertEquals(1, repo.data.value.records.size)
        compose.onNodeWithText("Fire").assertDoesNotExist()
    }
    @Test fun longHoldAndCrossCellDragDoNotSelect() {
        var selected: Int? = null
        compose.setContent { SinkpointTheme(ThemeMode.LIGHT) { FleetBoard("gestures", GameConfig(6, listOf(2)), enabled = true, onSelect = { selected = it }) } }
        compose.onNodeWithContentDescription("B3, untried").performTouchInput { longClick() }
        assertNull(selected)
        compose.onNodeWithContentDescription("B3, untried").performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(80f, 0f), 100) }
        assertNull(selected)
    }

    @Test fun targetGridAcceptsTapsAfterEachAiTurn() = checkTargetGridAcrossAiTurns()

    @Test @Config(qualifiers = "w1000dp-h800dp")
    fun wideTargetGridAcceptsTapsAfterEachAiTurn() = checkTargetGridAcrossAiTurns()

    private fun checkTargetGridAcrossAiTurns() {
        val config = GameConfig(6, listOf(2))
        val match = MatchState("turns", config, "player", "ai", "player",
            Fleet(listOf(Piece(0, 0, true))), true, true)
        val repo = MemoryRepository(AppData(playerId = "player", solo = SoloSave(
            match, Fleet(listOf(Piece(0, 30, true))), Difficulty.EASY, 1)))
        val vm = show(repo)
        val store = androidx.lifecycle.ViewModelStore().apply { put("game", vm) }
        try {
            compose.runOnIdle { vm.resumeSolo() }
            for ((turn, label) in listOf("B1", "B2", "B3").withIndex()) {
                compose.onNode(hasContentDescription("$label, untried") and hasClickAction()).performTouchInput { click() }
                compose.onNodeWithContentDescription("$label, selected target").assertExists("Turn $turn")
                compose.onNodeWithText("Fire").assertIsDisplayed().assertIsEnabled().performClick()
                compose.waitUntil(5000) {
                    compose.waitForIdle()
                    check(vm.ui.value.error == null) { vm.ui.value.errorDetail }
                    vm.ui.value.match?.moves?.size == (turn + 1) * 2 &&
                        vm.ui.value.match?.current == "player"
                }
                compose.onNodeWithText("Your turn").assertExists()
                assertNull(vm.ui.value.error)
            }
        } finally { compose.runOnIdle { store.clear() } }
    }
    private fun placement(fleet: Fleet = Fleet()): MemoryRepository {
        val config = GameConfig(6, listOf(3, 2))
        val match = MatchState("placement", config, "player", "ai", "player", fleet, peerReady = true)
        val repo = MemoryRepository(AppData(playerId = "player", solo = SoloSave(match,
            Fleet(listOf(Piece(0, 0, true), Piece(1, 12, true))), Difficulty.NORMAL, 1)))
        val vm = show(repo)
        compose.runOnIdle { vm.resumeSolo() }
        return repo
    }
    private fun drag(from: SemanticsNodeInteraction, to: SemanticsNodeInteraction) {
        val start = from.fetchSemanticsNode().boundsInRoot.center
        val end = to.fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { swipe(start, end, 500) }
        compose.waitForIdle()
    }
    @Test fun visibleRandomPlacesWholeFleetAndKeepsItEditable() {
        val repo = placement(Fleet(listOf(Piece(0, 0, true))))
        compose.onNodeWithText("Ready").assertIsNotEnabled()
        compose.onNodeWithText("Random").assertIsDisplayed().performClick()
        compose.waitUntil(5000) {
            compose.waitForIdle()
            Rules.validFleet(repo.data.value.solo!!.match.config, repo.data.value.solo!!.match.own)
        }
        assertFalse(repo.data.value.solo!!.match.ready)
        compose.onNodeWithText("Ready").assertIsEnabled()
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.waitUntil { repo.data.value.solo!!.match.own.pieces.isEmpty() }
        compose.onNodeWithText("Random").assertIsEnabled().performClick()
        compose.waitUntil(5000) {
            compose.waitForIdle()
            Rules.validFleet(repo.data.value.solo!!.match.config, repo.data.value.solo!!.match.own)
        }
        assertFalse(repo.data.value.solo!!.match.ready)
    }
    @Test fun dragFromTrayPlacesAndRotateCommitsWithoutPlaceButton() {
        val repo = placement()
        drag(compose.onNodeWithTag("piece-0"), compose.onNodeWithContentDescription("C3, untried"))
        compose.waitUntil { repo.data.value.solo!!.match.own.pieces.isNotEmpty() }
        assertEquals(Piece(0, 13, true), repo.data.value.solo!!.match.own.pieces.single())
        compose.onNodeWithContentDescription("Rotate").performClick()
        compose.waitUntil { repo.data.value.solo!!.match.own.pieces.single().horizontal == false }
        assertEquals(Piece(0, 8, false), repo.data.value.solo!!.match.own.pieces.single())
        compose.onNodeWithText("Place", substring = false).assertDoesNotExist()
    }
    @Test fun dragFromTailPreservesGrabOffsetAndInvalidDropKeepsFleet() {
        val repo = placement(Fleet(listOf(Piece(0, 0, true), Piece(1, 24, true))))
        drag(compose.onNodeWithContentDescription("A3, selected piece"), compose.onNodeWithContentDescription("C4, untried"))
        compose.waitUntil { repo.data.value.solo!!.match.own.pieces.first { it.id == 0 }.start == 13 }
        val saved = repo.data.value.solo!!.match.own
        drag(compose.onNodeWithContentDescription("C2, selected piece"), compose.onNodeWithContentDescription("E1, your piece"))
        assertEquals(saved, repo.data.value.solo!!.match.own)
        compose.onNodeWithText("That piece will not fit here.").assertExists()
    }
    @Test fun canceledAndOutsideDragsDoNotPlace() {
        val repo = placement()
        val start = compose.onNodeWithTag("piece-0").fetchSemanticsNode().boundsInRoot.center
        val end = compose.onNodeWithContentDescription("C3, untried").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(start); moveTo(end); cancel() }
        compose.waitForIdle()
        assertTrue(repo.data.value.solo!!.match.own.pieces.isEmpty())
        compose.onRoot().performTouchInput { swipe(start, androidx.compose.ui.geometry.Offset(2f, 2f), 500) }
        assertTrue(repo.data.value.solo!!.match.own.pieces.isEmpty())
    }
}



