package com.cbulock.sinkpoint.game

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable enum class TurnRule { CLASSIC, SALVO }
@Serializable enum class Difficulty { EASY, NORMAL, HARD }
@Serializable enum class Phase { PLACEMENT, PLAYING, FINISHED }
@Serializable data class GameConfig(
    val size: Int = 10,
    val lengths: List<Int> = listOf(5, 4, 3, 3, 2),
    val touching: Boolean = true,
    val rule: TurnRule = TurnRule.CLASSIC,
    val fireAgainOnHit: Boolean = true,
) {
    fun validate() {
        require(size in 6..12 && lengths.size in 1..10 && lengths.all { it in 2..6 }) { "Invalid fleet configuration" }
    }
    val classic: Boolean get() = this == GameConfig()
}
@Serializable data class Piece(val id: Int, val start: Int, val horizontal: Boolean)
@Serializable data class Fleet(val pieces: List<Piece> = emptyList())
@Serializable data class SunkPiece(val id: Int, val cells: List<Int>)
@Serializable data class Outcome(val hits: List<Int>, val sunk: List<SunkPiece>)
@Serializable data class Move(val id: String, val turn: Int, val attacker: String, val targets: List<Int>, val outcome: Outcome)

/** Serializable PRNG: a resumed turn makes the same choices. Not used for peer authentication. */
class GameRandom(var state: Long) {
    fun nextInt(bound: Int): Int {
        require(bound > 0)
        state += -7046029254386353131L
        var z = state
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        return (((z xor (z ushr 31)) ushr 1) % bound).toInt()
    }
    fun <T> shuffled(values: List<T>): List<T> = values.toMutableList().apply {
        for (i in lastIndex downTo 1) { val j = nextInt(i + 1); val a = this[i]; this[i] = this[j]; this[j] = a }
    }
}

object Rules {
    fun cells(config: GameConfig, piece: Piece): List<Int> {
        if (piece.id !in config.lengths.indices || piece.start !in 0 until config.size * config.size) return emptyList()
        val length = config.lengths[piece.id]
        val row = piece.start / config.size
        val col = piece.start % config.size
        if (piece.horizontal && col + length > config.size || !piece.horizontal && row + length > config.size) return emptyList()
        return List(length) { piece.start + it * if (piece.horizontal) 1 else config.size }
    }
    fun validFleet(config: GameConfig, fleet: Fleet, complete: Boolean = true): Boolean {
        if (fleet.pieces.map { it.id }.distinct().size != fleet.pieces.size) return false
        if (complete && fleet.pieces.map { it.id }.toSet() != config.lengths.indices.toSet()) return false
        val occupied = mutableSetOf<Int>()
        for (p in fleet.pieces) {
            val cells = cells(config, p)
            if (cells.isEmpty() || cells.any { cell -> occupied.any { other -> conflicts(config, cell, other) } }) return false
            occupied.addAll(cells)
        }
        return true
    }
    private fun conflicts(c: GameConfig, a: Int, b: Int): Boolean = a == b || !c.touching &&
        abs(a / c.size - b / c.size) <= 1 && abs(a % c.size - b % c.size) <= 1

    /** Exact search, cancellable, with equal-length symmetry elimination. Null means proven impossible. */
    suspend fun place(config: GameConfig, random: GameRandom): Fleet? {
        currentCoroutineContext().ensureActive()
        config.validate()
        if (config.lengths.sum() > config.size * config.size) return null
        val ids = config.lengths.indices.sortedByDescending { config.lengths[it] }
        val candidates = ids.associateWith { id -> random.shuffled((0 until config.size * config.size).flatMap { start ->
            listOf(Piece(id, start, true), Piece(id, start, false)).filter { cells(config, it).isNotEmpty() }
        }) }
        val blocked = IntArray(config.size * config.size)
        val placed = mutableListOf<Piece>()
        var nodes = 0
        suspend fun search(depth: Int): Boolean {
            if (++nodes % 128 == 0) currentCoroutineContext().ensureActive()
            if (depth == ids.size) return true
            val id = ids[depth]
            val prior = placed.lastOrNull()?.takeIf { config.lengths[it.id] == config.lengths[id] }
            for (p in candidates.getValue(id)) {
                if (prior != null && p.start * 2 + (if (p.horizontal) 0 else 1) <= prior.start * 2 + (if (prior.horizontal) 0 else 1)) continue
                val cells = cells(config, p)
                if (cells.any { blocked[it] > 0 }) continue
                val affected = cells.flatMap { cell ->
                    if (config.touching) listOf(cell) else (-1..1).flatMap { dr -> (-1..1).mapNotNull { dc ->
                        val r = cell / config.size + dr; val col = cell % config.size + dc
                        if (r in 0 until config.size && col in 0 until config.size) r * config.size + col else null
                    } }
                }.distinct()
                affected.forEach { blocked[it]++ }; placed.add(p)
                if (search(depth + 1)) return true
                placed.removeAt(placed.lastIndex); affected.forEach { blocked[it]-- }
            }
            return false
        }
        return if (search(0)) Fleet(placed.sortedBy { it.id }) else null
    }
    fun resolve(config: GameConfig, fleet: Fleet, previous: Set<Int>, targets: List<Int>): Outcome {
        require(validFleet(config, fleet))
        require(targets.isNotEmpty() && targets.distinct().size == targets.size)
        require(targets.all { it in 0 until config.size * config.size && it !in previous })
        val all = fleet.pieces.flatMap { cells(config, it) }.toSet()
        val after = previous + targets
        return Outcome(targets.filter { it in all }, fleet.pieces.mapNotNull { p ->
            val cells = cells(config, p)
            if (cells.all { it in after } && !cells.all { it in previous }) SunkPiece(p.id, cells) else null
        })
    }
}

/** A projection containing only information an opponent has disclosed. */
data class Knowledge(val config: GameConfig, val moves: List<Move>) {
    val tried = moves.flatMap { it.targets }.toSet()
    val hits = moves.flatMap { it.outcome.hits }.toSet()
    val sunk = moves.flatMap { it.outcome.sunk }
    val unresolved = hits - sunk.flatMap { it.cells }.toSet()
    val remaining = config.lengths.indices.filter { id -> sunk.none { it.id == id } }
}

@Serializable data class MatchState(
    val id: String,
    val config: GameConfig,
    val localId: String,
    val peerId: String,
    val first: String,
    val own: Fleet = Fleet(),
    val ready: Boolean = false,
    val peerReady: Boolean = false,
    val moves: List<Move> = emptyList(),
    val resigned: String? = null,
) {
    val outgoing get() = Knowledge(config, moves.filter { it.attacker == localId })
    val incoming get() = Knowledge(config, moves.filter { it.attacker == peerId })
    val winner: String? get() = when {
        resigned == localId -> peerId
        resigned == peerId -> localId
        outgoing.sunk.size == config.lengths.size -> localId
        incoming.sunk.size == config.lengths.size -> peerId
        else -> null
    }
    val phase get() = when { winner != null -> Phase.FINISHED; ready && peerReady -> Phase.PLAYING; else -> Phase.PLACEMENT }
    val current get(): String {
        val last = moves.lastOrNull() ?: return first
        if (config.rule == TurnRule.CLASSIC && config.fireAgainOnHit && last.outcome.hits.isNotEmpty()) return last.attacker
        return if (last.attacker == localId) peerId else localId
    }
    fun quota(attacker: String = current): Int {
        val attack = if (attacker == localId) outgoing else incoming
        val defense = if (attacker == localId) incoming else outgoing
        return minOf(if (config.rule == TurnRule.CLASSIC) 1 else config.lengths.size - defense.sunk.size,
            config.size * config.size - attack.tried.size)
    }
    fun checkTargets(attacker: String, targets: List<Int>) {
        require(phase == Phase.PLAYING && attacker == current) { "Not this player's turn" }
        val tried = if (attacker == localId) outgoing.tried else incoming.tried
        require(targets.size == quota(attacker) && targets.distinct().size == targets.size) { "Invalid volley size" }
        require(targets.all { it in 0 until config.size * config.size && it !in tried }) { "Target already used or out of bounds" }
    }
    fun append(move: Move): MatchState {
        require(move.id.isNotBlank() && moves.none { it.id == move.id } && move.turn == moves.size)
        checkTargets(move.attacker, move.targets)
        val known = if (move.attacker == localId) outgoing else incoming
        val outcome = move.outcome
        require(outcome.hits.distinct().size == outcome.hits.size && move.targets.containsAll(outcome.hits))
        require(outcome.sunk.map { it.id }.distinct().size == outcome.sunk.size)
        val allHits = known.hits + outcome.hits
        val sunkCells = known.sunk.flatMap { it.cells }.toMutableSet()
        outcome.sunk.forEach { p ->
            require(p.id in config.lengths.indices && known.sunk.none { it.id == p.id })
            require(p.cells.size == config.lengths[p.id] && p.cells.distinct().size == p.cells.size && allHits.containsAll(p.cells))
            require(p.cells.any { it in move.targets } && p.cells.none { it in sunkCells })
            val start = p.cells.min()
            require(listOf(true, false).any { Rules.cells(config, Piece(p.id, start, it)).toSet() == p.cells.toSet() })
            sunkCells.addAll(p.cells)
        }
        if (move.attacker == peerId) require(outcome == Rules.resolve(config, own, incoming.tried, move.targets)) { "Peer history does not match your fleet" }
        return copy(moves = moves + move)
    }
    fun validate() {
        config.validate()
        require(id.isNotBlank() && localId.isNotBlank() && peerId.isNotBlank() && localId != peerId && first in listOf(localId, peerId))
        require(Rules.validFleet(config, own, ready))
        require(resigned == null || resigned in listOf(localId, peerId))
        var replay = copy(moves = emptyList(), resigned = null)
        moves.forEach { replay = replay.append(it) }
    }
}
