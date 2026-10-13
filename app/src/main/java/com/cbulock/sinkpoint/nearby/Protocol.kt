package com.cbulock.sinkpoint.nearby

import com.cbulock.sinkpoint.game.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Base64
import java.util.UUID

@Serializable data class Shot(val id: String, val turn: Int, val attacker: String, val targets: List<Int>)
@Serializable data class NearbySave(
    val match: MatchState,
    val host: Boolean,
    val peerName: String = "",
    val accepted: Boolean = false,
    val outgoing: Shot? = null,
    val incomingResult: Move? = null,
    val awaitingConfirm: String? = null,
    val peerFleet: Fleet? = null,
) {
    val blocked get() = outgoing != null || incomingResult != null || awaitingConfirm != null
    fun validate() {
        match.validate()
        peerFleet?.let { validateRevealedFleet(match, it) }
        require(peerName.length <= 32)
        outgoing?.let { require(it.attacker == match.localId && it.id.isNotBlank() && it.turn == match.moves.size); match.checkTargets(it.attacker, it.targets) }
        incomingResult?.let { require(it.attacker == match.peerId); match.append(it) }
        awaitingConfirm?.let { require(match.moves.lastOrNull()?.id == it && match.moves.last().attacker == match.localId) }
        require(listOf(outgoing, incomingResult, awaitingConfirm).count { it != null } <= 1)
    }
}
@Serializable enum class MessageKind { INTRO, OFFER, ACCEPT, SYNC, READY, SHOT, RESULT, ACK, CONFIRM, PAUSE, RESIGN, REMATCH, ERROR, REVEAL }
@Serializable data class WireMessage(
    val version: Int = 2,
    val kind: MessageKind,
    val player: String,
    val matchId: String = "",
    val name: String = "",
    val config: GameConfig? = null,
    val first: String = "",
    val ready: Boolean = false,
    val moves: List<Move> = emptyList(),
    val shot: Shot? = null,
    val move: Move? = null,
    val actionId: String = "",
    val resigned: String? = null,
    val fleet: Fleet? = null,
)
data class Transition(val save: NearbySave, val send: List<WireMessage>)

private fun validateRevealedFleet(match: MatchState, fleet: Fleet) {
    require(match.phase == Phase.FINISHED && Rules.validFleet(match.config, fleet))
    var tried = emptySet<Int>()
    match.outgoing.moves.forEach { move ->
        require(move.outcome == Rules.resolve(match.config, fleet, tried, move.targets)) { "Revealed fleet disagrees with shot history" }
        tried = tried + move.targets
    }
}

/** All effects are returned. The caller must persist save BEFORE sending any message. */
object SessionProtocol {
    fun message(s: NearbySave, kind: MessageKind) = WireMessage(kind = kind, player = s.match.localId, matchId = s.match.id)
    fun sync(s: NearbySave) = message(s, MessageKind.SYNC).copy(ready = s.match.ready, moves = s.match.moves, resigned = s.match.resigned,
        fleet = s.match.own.takeIf { s.match.phase == Phase.FINISHED })
    fun reveal(s: NearbySave): WireMessage {
        require(s.match.phase == Phase.FINISHED)
        return message(s, MessageKind.REVEAL).copy(fleet = s.match.own)
    }
    fun fire(s: NearbySave, id: String, targets: List<Int>): Transition {
        require(s.accepted && !s.blocked)
        s.match.checkTargets(s.match.localId, targets)
        val shot = Shot(id, s.match.moves.size, s.match.localId, targets)
        return Transition(s.copy(outgoing = shot), listOf(message(s, MessageKind.SHOT).copy(shot = shot)))
    }
    fun receive(s: NearbySave, msg: WireMessage): Transition {
        require(msg.version == 2 && msg.matchId == s.match.id && msg.player == s.match.peerId) { "Incompatible session or different player" }
        fun answer(kind: MessageKind) = message(s, kind)
        val transition = when (msg.kind) {
            MessageKind.REVEAL -> {
                val fleet = requireNotNull(msg.fleet)
                validateRevealedFleet(s.match, fleet)
                require(s.peerFleet == null || s.peerFleet == fleet)
                Transition(s.copy(peerFleet = fleet), emptyList())
            }
            MessageKind.READY -> Transition(s.copy(match = s.match.copy(peerReady = true)), emptyList())
            MessageKind.SHOT -> {
                val shot = requireNotNull(msg.shot)
                require(shot.attacker == msg.player)
                val existing = s.match.moves.find { it.id == shot.id } ?: s.incomingResult?.takeIf { it.id == shot.id }
                if (existing != null) {
                    require(existing.targets == shot.targets && existing.turn == shot.turn && existing.attacker == shot.attacker)
                    Transition(s, listOf(answer(MessageKind.RESULT).copy(move = existing)))
                } else {
                    require(!s.blocked && shot.turn == s.match.moves.size)
                    s.match.checkTargets(shot.attacker, shot.targets)
                    val result = Move(shot.id, shot.turn, shot.attacker, shot.targets, Rules.resolve(s.match.config, s.match.own, s.match.incoming.tried, shot.targets))
                    Transition(s.copy(incomingResult = result), listOf(answer(MessageKind.RESULT).copy(move = result)))
                }
            }
            MessageKind.RESULT -> {
                val move = requireNotNull(msg.move)
                require(move.attacker == s.match.localId)
                val existing = s.match.moves.find { it.id == move.id }
                if (existing != null) {
                    require(existing == move)
                    Transition(s, listOf(answer(MessageKind.ACK).copy(actionId = move.id)))
                } else {
                    val shot = requireNotNull(s.outgoing)
                    require(move.id == shot.id && move.turn == shot.turn && move.targets == shot.targets)
                    Transition(s.copy(match = s.match.append(move), outgoing = null, awaitingConfirm = move.id), listOf(answer(MessageKind.ACK).copy(actionId = move.id)))
                }
            }
            MessageKind.ACK -> {
                val existing = s.match.moves.find { it.id == msg.actionId }
                if (existing != null) {
                    require(existing.attacker == s.match.peerId)
                    Transition(s, listOf(answer(MessageKind.CONFIRM).copy(actionId = msg.actionId)))
                } else {
                    val result = requireNotNull(s.incomingResult)
                    require(result.id == msg.actionId)
                    Transition(s.copy(match = s.match.append(result), incomingResult = null), listOf(answer(MessageKind.CONFIRM).copy(actionId = result.id)))
                }
            }
            MessageKind.CONFIRM -> {
                require(s.match.moves.any { it.id == msg.actionId && it.attacker == s.match.localId })
                Transition(if (s.awaitingConfirm == msg.actionId) s.copy(awaitingConfirm = null) else s, emptyList())
            }
            MessageKind.SYNC -> reconcile(s, msg)
            MessageKind.RESIGN -> {
                require(!s.blocked && s.match.current == msg.player && s.match.phase == Phase.PLAYING)
                Transition(s.copy(match = s.match.copy(resigned = msg.player)), emptyList())
            }
            else -> throw IllegalArgumentException("Unexpected session message")
        }
        return if (transition.save.match.phase == Phase.FINISHED && msg.kind != MessageKind.REVEAL && msg.kind != MessageKind.SYNC)
            transition.copy(send = transition.send + reveal(transition.save)) else transition
    }
    private fun reconcile(original: NearbySave, msg: WireMessage): Transition {
        var s = original.copy(match = original.match.copy(peerReady = original.match.peerReady || msg.ready))
        val local = s.match.moves
        require(msg.moves.size <= s.match.config.size * s.match.config.size * 2)
        require(local.take(minOf(local.size, msg.moves.size)) == msg.moves.take(minOf(local.size, msg.moves.size))) { "Saved histories disagree" }
        for (move in msg.moves.drop(local.size)) {
            // The only ahead event can be a result this device durably resolved before its ACK was lost.
            require(s.incomingResult == move) { "Missing durable move result" }
            s = s.copy(match = s.match.append(move), incomingResult = null)
        }
        if (msg.resigned != null) {
            require(msg.resigned in listOf(s.match.localId, s.match.peerId))
            require(s.match.resigned == null || s.match.resigned == msg.resigned)
            s = s.copy(match = s.match.copy(resigned = msg.resigned), outgoing = null, incomingResult = null, awaitingConfirm = null)
        }
        if (s.awaitingConfirm != null && msg.moves.any { it.id == s.awaitingConfirm }) s = s.copy(awaitingConfirm = null)
        msg.fleet?.let { fleet ->
            validateRevealedFleet(s.match, fleet)
            require(s.peerFleet == null || s.peerFleet == fleet)
            s = s.copy(peerFleet = fleet)
        }
        val send = mutableListOf<WireMessage>()
        if (local.size > msg.moves.size || s.match.resigned != null && msg.resigned == null) send.add(sync(s))
        s.outgoing?.let { send.add(message(s, MessageKind.SHOT).copy(shot = it)) }
        s.incomingResult?.let { send.add(message(s, MessageKind.RESULT).copy(move = it)) }
        s.awaitingConfirm?.let { send.add(message(s, MessageKind.ACK).copy(actionId = it)) }
        return Transition(s, send)
    }
}

/** Byte payloads stay below Nearby's 32 KiB limit, including a long saved match. */
object WireCodec {
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    const val MAX_MESSAGE = 256 * 1024
    @Serializable private data class Frame(val id: String, val index: Int, val count: Int, val body: String)
    fun encode(message: WireMessage): List<ByteArray> {
        val bytes = json.encodeToString(message).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_MESSAGE)
        val parts = bytes.toList().chunked(8192)
        val id = UUID.randomUUID().toString()
        return parts.mapIndexed { i, part -> json.encodeToString(Frame(id, i, parts.size, Base64.getEncoder().encodeToString(part.toByteArray()))).toByteArray() }
    }
    class Decoder {
        private data class Pending(val started: Long, val count: Int, val parts: MutableMap<Int, ByteArray>)
        private val pending = mutableMapOf<String, Pending>()
        fun accept(bytes: ByteArray, now: Long = System.nanoTime() / 1_000_000): WireMessage? {
            require(bytes.size <= 16_384)
            pending.entries.removeAll { now - it.value.started > 30_000 }
            val f = json.decodeFromString<Frame>(bytes.toString(Charsets.UTF_8))
            require(f.id.length <= 36 && f.count in 1..32 && f.index in 0 until f.count && f.body.length <= 11_000)
            require(f.id in pending || pending.size < 4)
            val p = pending.getOrPut(f.id) { Pending(now, f.count, mutableMapOf()) }
            require(p.count == f.count)
            val body = Base64.getDecoder().decode(f.body)
            require(body.size <= 8192)
            p.parts[f.index]?.let { require(it.contentEquals(body)) }
            p.parts[f.index] = body
            if (p.parts.size != p.count) return null
            pending.remove(f.id)
            val joined = (0 until p.count).flatMap { p.parts.getValue(it).asIterable() }.toByteArray()
            require(joined.size <= MAX_MESSAGE)
            return json.decodeFromString<WireMessage>(joined.toString(Charsets.UTF_8)).also {
                require(it.version == 2 && it.player.length in 1..64 && it.matchId.length <= 64 && it.name.length <= 32)
            }
        }
    }
}
