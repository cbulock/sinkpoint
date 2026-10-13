package com.cbulock.sinkpoint

import com.cbulock.sinkpoint.game.*
import com.cbulock.sinkpoint.nearby.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class ProtocolTest {
    private val config = GameConfig(6, listOf(2))
    private fun pair(): Pair<NearbySave, NearbySave> {
        val a = MatchState("game", config, "a", "b", "a", Fleet(listOf(Piece(0, 0, true))), true, true)
        val b = MatchState("game", config, "b", "a", "a", Fleet(listOf(Piece(0, 6, true))), true, true)
        return NearbySave(a, true, accepted = true) to NearbySave(b, false, accepted = true)
    }
    private fun restart(save: NearbySave) = WireCodec.json.decodeFromString<NearbySave>(WireCodec.json.encodeToString(save)).also { it.validate() }
    @Test fun consecutiveHitsWaitForConfirmationThenAllowSamePlayerToFire() {
        var (a, b) = pair()
        fun fire(target: Int, id: String) {
            val shot = SessionProtocol.fire(a, id, listOf(target)).also { a = restart(it.save) }.send.single()
            val result = SessionProtocol.receive(b, shot).also { b = restart(it.save) }.send.single()
            assertTrue(runCatching { SessionProtocol.fire(a, "early", listOf(7)) }.isFailure)
            val attackResult = SessionProtocol.receive(a, result).also { a = restart(it.save) }
            val ack = attackResult.send.single { it.kind == MessageKind.ACK }
            assertTrue(a.blocked)
            val defenseResult = SessionProtocol.receive(b, ack).also { b = restart(it.save) }
            val confirm = defenseResult.send.single { it.kind == MessageKind.CONFIRM }
            a = restart(SessionProtocol.receive(a, confirm).save)
            attackResult.send.filter { it.kind == MessageKind.REVEAL }.forEach { b = restart(SessionProtocol.receive(b, it).save) }
            defenseResult.send.filter { it.kind == MessageKind.REVEAL }.forEach { a = restart(SessionProtocol.receive(a, it).save) }
            assertEquals(a.match.moves, b.match.moves)
            assertFalse(a.blocked); assertFalse(b.blocked)
        }
        fire(6, "hit")
        assertEquals("a", a.match.current); assertEquals("a", b.match.current)
        fire(7, "sink")
        assertEquals(Phase.FINISHED, a.match.phase); assertEquals(Phase.FINISHED, b.match.phase)
        assertEquals(b.match.own, a.peerFleet); assertEquals(a.match.own, b.peerFleet)
    }
    @Test fun revealsRequireFinishedMatchAndValidConsistentFleet() {
        val (a, b) = pair()
        assertTrue(runCatching { SessionProtocol.reveal(a) }.isFailure)
        val reveal = WireMessage(kind = MessageKind.REVEAL, player = "b", matchId = "game", fleet = b.match.own)
        assertTrue(runCatching { SessionProtocol.receive(a, reveal) }.isFailure)
        val finished = a.copy(match = a.match.copy(resigned = "a"))
        val restored = restart(SessionProtocol.receive(finished, reveal).save)
        assertEquals(b.match.own, restored.peerFleet)
        assertEquals(b.match.own, restart(SessionProtocol.receive(finished, SessionProtocol.sync(b.copy(match = b.match.copy(resigned = "a")))).save).peerFleet)
        assertTrue(runCatching { SessionProtocol.receive(finished, reveal.copy(fleet = Fleet())) }.isFailure)
        val hit = Move("hit", 0, "a", listOf(6), Outcome(listOf(6), emptyList()))
        val withHit = a.copy(match = a.match.append(hit).copy(resigned = "b"))
        assertTrue(runCatching { SessionProtocol.receive(withHit, reveal.copy(fleet = a.match.own)) }.isFailure)
    }
    @Test fun repeatedPacketsResolveExactlyOnce() {
        var (a, b) = pair()
        val shot = SessionProtocol.fire(a, "shot", listOf(6)).also { a = it.save }.send.single()
        val result = SessionProtocol.receive(b, shot).also { b = it.save }.send.single()
        assertEquals(result, SessionProtocol.receive(b, shot).send.single())
        val ack = SessionProtocol.receive(a, result).also { a = it.save }.send.single()
        assertEquals(ack, SessionProtocol.receive(a, result).send.single())
        val confirm = SessionProtocol.receive(b, ack).also { b = it.save }.send.single()
        assertEquals(confirm, SessionProtocol.receive(b, ack).send.single())
        a = SessionProtocol.receive(a, confirm).save
        assertEquals(a, SessionProtocol.receive(a, confirm).save)
        assertEquals(a.match.moves, b.match.moves); assertEquals(1, a.match.moves.size)
        assertFalse(a.blocked); assertFalse(b.blocked)
    }
    @Test fun interruptionsAtEveryExchangeRecoverAfterEitherDeviceRestarts() {
        for (cut in 0..4) {
            var (a, b) = pair()
            var packet = SessionProtocol.fire(a, "shot", listOf(6)).also { a = it.save }.send.single()
            if (cut >= 1) packet = SessionProtocol.receive(b, packet).also { b = it.save }.send.single()
            if (cut >= 2) packet = SessionProtocol.receive(a, packet).also { a = it.save }.send.single()
            if (cut >= 3) packet = SessionProtocol.receive(b, packet).also { b = it.save }.send.single()
            if (cut >= 4) a = SessionProtocol.receive(a, packet).save
            a = restart(a); b = restart(b)
            val queue = ArrayDeque<Pair<Boolean, WireMessage>>()
            queue.add(true to SessionProtocol.sync(b)); queue.add(false to SessionProtocol.sync(a))
            var steps = 0
            while (queue.isNotEmpty()) {
                assertTrue("Sync must converge", steps++ < 30)
                val (toA, message) = queue.removeFirst()
                val transition = SessionProtocol.receive(if (toA) a else b, message)
                if (toA) a = restart(transition.save) else b = restart(transition.save)
                transition.send.forEach { queue.add(!toA to it) }
            }
            assertEquals("cut=$cut", a.match.moves, b.match.moves)
            assertEquals(1, a.match.moves.size); assertFalse(a.blocked); assertFalse(b.blocked)
        }
    }
    @Test fun wrongPeerVersionMatchAndHistoryAreRejected() {
        val (a, b) = pair()
        val sync = SessionProtocol.sync(b)
        listOf(sync.copy(version = 1), sync.copy(player = "stranger"), sync.copy(matchId = "other"), sync.copy(moves = listOf(Move("fake", 0, "a", listOf(5), Outcome(emptyList(), emptyList()))))).forEach { message ->
            assertTrue(runCatching { SessionProtocol.receive(a, message) }.isFailure)
        }
    }
    @Test fun noHiddenFleetCrossesWireAndFramesAreBounded() {
        val (a, _) = pair()
        val message = SessionProtocol.sync(a)
        val text = WireCodec.json.encodeToString(message)
        assertFalse(text.contains("own")); assertFalse(text.contains("pieces"))
        val large = message.copy(moves = List(288) { Move("move-$it", it, "a", listOf(0), Outcome(emptyList(), emptyList())) })
        val frames = WireCodec.encode(large)
        assertTrue(frames.size > 1); assertTrue(frames.all { it.size < 32 * 1024 })
        val decoder = WireCodec.Decoder()
        var received: WireMessage? = null
        frames.reversed().forEach { decoder.accept(it)?.let { m -> received = m } }
        assertEquals(large, received)
        assertTrue(runCatching { decoder.accept(ByteArray(33 * 1024)) }.isFailure)
    }
    @Test fun cannotFireBeforeAcceptanceOrDuringPendingMove() {
        val (a, _) = pair()
        assertTrue(runCatching { SessionProtocol.fire(a.copy(accepted = false), "1", listOf(6)) }.isFailure)
        val pending = SessionProtocol.fire(a, "1", listOf(6)).save
        assertTrue(runCatching { SessionProtocol.fire(pending, "2", listOf(7)) }.isFailure)
    }
}
