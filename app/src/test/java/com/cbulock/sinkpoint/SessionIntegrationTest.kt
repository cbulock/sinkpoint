package com.cbulock.sinkpoint

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.cbulock.sinkpoint.data.*
import com.cbulock.sinkpoint.game.*
import com.cbulock.sinkpoint.nearby.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class PairedRadio : NearbyTransport {
    private val channel = Channel<TransportEvent>(Channel.UNLIMITED)
    override val events = channel.receiveAsFlow()
    lateinit var peer: PairedRadio
    var connected = false
    val sent = mutableListOf<WireMessage>()
    fun open() { connected = true; peer.connected = true; channel.trySend(TransportEvent.Connected("temporary-endpoint")) }
    fun deliverLate(message: WireMessage) { channel.trySend(TransportEvent.Message(message)) }
    override fun available() = true
    override suspend fun host(name: String) = Unit
    override suspend fun discover() = Unit
    override suspend fun connect(peer: Peer, name: String) = Unit
    override suspend fun accept(endpoint: String) = Unit
    override fun reject(endpoint: String) = Unit
    override suspend fun send(message: WireMessage) {
        check(connected && peer.connected)
        sent.add(message)
        // Encode and decode to exercise exactly what the Google adapter sends.
        val decoder = WireCodec.Decoder()
        WireCodec.encode(message).forEach { frame -> decoder.accept(frame)?.let { peer.channel.trySend(TransportEvent.Message(it)) } }
    }
    override fun close() {
        val wasConnected = connected
        connected = false
        if (wasConnected) { channel.trySend(TransportEvent.Disconnected); peer.connected = false; peer.channel.trySend(TransportEvent.Disconnected) }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionIntegrationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun controllersNegotiatePlayPauseReconnectAndRematch() {
        compose.setContent { }
        val config = GameConfig(6, listOf(2))
        val ra = PairedRadio(); val rb = PairedRadio(); ra.peer = rb; rb.peer = ra
        val repoA = MemoryRepository(AppData(playerId = "player-a", preferences = Preferences(config = config)))
        val repoB = MemoryRepository(AppData(playerId = "player-b", preferences = Preferences(config = config)))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val a = GameViewModel(app, repoA, ra); val b = GameViewModel(app, repoB, rb)
        val store = ViewModelStore().apply { put("a", a); put("b", b) }
        fun until(condition: () -> Boolean) = compose.waitUntil(5000) {
            compose.waitForIdle()
            check(a.ui.value.error == null) { "Host: ${a.ui.value.errorDetail}" }
            check(b.ui.value.error == null) { "Guest: ${b.ui.value.errorDetail}" }
            condition()
        }
        try {
            until { a.ui.value.loaded && b.ui.value.loaded }
            compose.runOnIdle { a.startNearby(true, false); b.startNearby(false, false) }
            until { a.ui.value.searching && b.ui.value.searching }
            compose.runOnIdle { ra.open(); rb.open() }
            until { b.ui.value.offer != null }
            compose.runOnIdle { b.acceptOffer() }
            until { a.ui.value.synchronized && b.ui.value.synchronized }
            assertEquals(config, a.ui.value.match?.config); assertEquals(config, b.ui.value.match?.config)
            compose.runOnIdle { a.place(Piece(0, 0, true)); b.place(Piece(0, 6, true)) }
            until { a.ui.value.match?.own?.pieces?.size == 1 && b.ui.value.match?.own?.pieces?.size == 1 }
            compose.runOnIdle { a.ready(); b.ready() }
            until { a.ui.value.match?.phase == Phase.PLAYING && b.ui.value.match?.phase == Phase.PLAYING }
            val first = if (a.ui.value.match!!.current == "player-a") a else b
            compose.runOnIdle { first.fire(listOf(35)) }
            until { a.ui.value.match?.moves?.size == 1 && b.ui.value.match?.moves?.size == 1 && !a.ui.value.exchangePending && !b.ui.value.exchangePending }
            val id = a.ui.value.match!!.id
            compose.runOnIdle { a.home() }
            until { !a.ui.value.connected && !b.ui.value.connected }
            compose.runOnIdle { ra.deliverLate(SessionProtocol.sync(repoB.data.value.nearby!!)) }
            compose.waitForIdle()
            assertEquals(Screen.HOME, a.ui.value.screen)
            compose.runOnIdle { a.startNearby(true, true); b.startNearby(false, true) }
            until { a.ui.value.searching && b.ui.value.searching }
            compose.runOnIdle { ra.open(); rb.open() }
            until { a.ui.value.synchronized && b.ui.value.synchronized }
            assertEquals(id, a.ui.value.match!!.id)
            assertEquals(a.ui.value.match!!.moves, b.ui.value.match!!.moves)
            val current = if (a.ui.value.match!!.current == "player-a") a else b
            compose.runOnIdle { current.resign() }
            until { a.ui.value.match?.phase == Phase.FINISHED && b.ui.value.match?.phase == Phase.FINISHED }
            until { a.ui.value.opponentFleet == b.ui.value.match?.own && b.ui.value.opponentFleet == a.ui.value.match?.own }
            assertEquals(1, repoA.data.value.records.size); assertEquals(1, repoB.data.value.records.size)
            compose.runOnIdle { a.rematch(); b.rematch() }
            until { b.ui.value.offer != null }
            compose.runOnIdle { b.acceptOffer() }
            until { a.ui.value.synchronized && b.ui.value.synchronized && a.ui.value.match?.id != id }
            assertEquals(Phase.PLACEMENT, a.ui.value.match!!.phase)
            assertNull(a.ui.value.opponentFleet); assertNull(b.ui.value.opponentFleet)
            assertEquals(a.ui.value.match!!.id, b.ui.value.match!!.id)
        } finally { compose.runOnIdle { store.clear() } }
    }
}
