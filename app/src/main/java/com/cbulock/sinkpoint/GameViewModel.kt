package com.cbulock.sinkpoint

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cbulock.sinkpoint.data.*
import com.cbulock.sinkpoint.game.*
import com.cbulock.sinkpoint.nearby.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

enum class Screen { HOME, OPTIONS, GAME, SETTINGS, STATISTICS, LEARN, LOBBY }
enum class Mode { AI, NEARBY }
data class ResumeSummary(val finished: Boolean, val shots: Int, val size: Int, val placement: Boolean)
data class ImportSummary(val recentResults: Int, val hasAiProgress: Boolean)
data class UiState(
    val loaded: Boolean = false,
    val screen: Screen = Screen.HOME,
    val mode: Mode = Mode.AI,
    val preferences: Preferences = Preferences(),
    val match: MatchState? = null,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val solo: ResumeSummary? = null,
    val nearby: ResumeSummary? = null,
    val records: List<Record> = emptyList(),
    val totals: Map<String, Aggregate> = emptyMap(),
    val busy: Boolean = false,
    val error: Int? = null,
    val errorDetail: String = "",
    val peers: List<Peer> = emptyList(),
    val pairing: TransportEvent.Pairing? = null,
    val connected: Boolean = false,
    val searching: Boolean = false,
    val hosting: Boolean = false,
    val synchronized: Boolean = false,
    val exchangePending: Boolean = false,
    val offer: WireMessage? = null,
    val rematchRequested: Boolean = false,
    val rematchSent: Boolean = false,
    val importPreview: ImportSummary? = null,
    val resultVisible: Boolean = false,
    val storageBlocked: Boolean = false,
    val opponentFleet: Fleet? = null,
)

class GameViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: GameRepository = DiskGameRepository(application),
    private val transport: NearbyTransport = GoogleNearbyTransport(application),
) : AndroidViewModel(application) {
    private val mutex = Mutex()
    private var saved: AppData? = null
    private val mutable = MutableStateFlow(UiState())
    val ui = mutable.asStateFlow()
    private var aiJob: Job? = null
    private var setupJob: Job? = null
    private var setupGeneration = 0
    private var retryWrite: (suspend () -> Unit)? = null
    private var newJoin = false
    private var pendingOffer: WireMessage? = null
    private var pendingImport: Backup? = null
    private var lastFinishedShown: String? = null
    private var foreground = true
    private var previousScreen = Screen.HOME
    private suspend fun findLayout(config: GameConfig, random: GameRandom): Fleet? =
        withContext(Dispatchers.Default) { withTimeout(5000) { Rules.place(config, random) } }

    init {
        viewModelScope.launch {
            while (isActive) {
                try {
                    repository.data.collect { data ->
                        saved = data
                        if (retryWrite == null) mutable.update { it.copy(storageBlocked = false) }
                        refresh()
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { fail(R.string.save_unreadable, e, true); delay(3000) }
            }
        }
        viewModelScope.launch {
            transport.events.collect { event ->
                mutex.withLock {
                    try { event(event) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        transport.close(); mutable.update { it.copy(connected = false, synchronized = false, searching = false, pairing = null) }
                        if (!mutable.value.storageBlocked) fail(connectionError(e), e)
                    }
                }
            }
        }
    }
    private fun refresh() {
        val data = saved ?: return
        val match = if (mutable.value.mode == Mode.AI) data.solo?.match else data.nearby?.match
        fun summary(m: MatchState) = ResumeSummary(m.phase == Phase.FINISHED, m.outgoing.tried.size, m.config.size, m.phase == Phase.PLACEMENT)
        mutable.update { it.copy(loaded = true, preferences = data.preferences, match = match,
            opponentFleet = if (match?.phase == Phase.FINISHED) {
                if (it.mode == Mode.AI) data.solo?.aiFleet else data.nearby?.peerFleet
            } else null,
            difficulty = data.solo?.difficulty ?: it.difficulty,
            solo = data.solo?.match?.let(::summary), nearby = data.nearby?.match?.let(::summary),
            records = data.records, totals = data.totals, exchangePending = it.mode == Mode.NEARBY && data.nearby?.blocked == true) }
    }
    private fun fail(resource: Int, error: Exception? = null, storage: Boolean = false) {
        mutable.update { it.copy(error = resource, errorDetail = error?.message.orEmpty(), busy = false, storageBlocked = it.storageBlocked || storage) }
    }
    private suspend fun write(transform: (AppData) -> AppData) {
        try {
            saved = repository.update(transform); retryWrite = null
            mutable.update { it.copy(storageBlocked = false) }; refresh()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            retryWrite = { write(transform) }
            pauseNearby()
            fail(R.string.save_failed, e, true)
            throw e
        }
    }
    private fun command(block: suspend () -> Unit) {
        viewModelScope.launch {
            mutex.withLock {
                if (mutable.value.storageBlocked) return@withLock
                try { block() }
                catch (e: TimeoutCancellationException) { fail(R.string.layout_timeout) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    if (mutable.value.mode == Mode.NEARBY) pauseNearby()
                    if (!mutable.value.storageBlocked) fail(if (mutable.value.mode == Mode.NEARBY) connectionError(e) else R.string.action_failed, e)
                }
            }
        }
    }
    fun dismissError() { mutable.update { it.copy(error = null, errorDetail = "") } }
    private fun connectionError(e: Exception): Int = when {
        e.message.orEmpty().contains("histories") || e.message.orEmpty().contains("durable move") || e.message.orEmpty().contains("match your fleet") -> R.string.history_conflict
        e.message.orEmpty().contains("original") || e.message.orEmpty().contains("different") || e.message.orEmpty().contains("Incompatible") -> R.string.wrong_session
        else -> R.string.connection_error
    }
    fun retryStorage() {
        viewModelScope.launch { mutex.withLock {
            try {
                retryWrite?.invoke()
                // A failed wire effect is recovered by a fresh handshake, never by guessing delivery.
                if (!mutable.value.storageBlocked) { dismissError(); scheduleAi() }
            } catch (_: Exception) { }
        } }
    }
    fun navigate(screen: Screen) {
        previousScreen = mutable.value.screen
        aiJob?.cancel()
        if (screen != Screen.GAME && screen != Screen.LOBBY) pauseNearby()
        mutable.update { it.copy(screen = screen, resultVisible = false) }; refresh()
    }
    fun home() { cancelSetup(); navigate(Screen.HOME) }
    fun back() {
        if (mutable.value.screen == Screen.OPTIONS && previousScreen == Screen.LOBBY) navigate(Screen.LOBBY) else home()
    }
    fun settings(preferences: Preferences) = command {
        write { it.copy(preferences = preferences.copy(name = preferences.name.trim().take(32).ifBlank { "Player" })) }
    }
    fun configure(config: GameConfig) = settings(mutable.value.preferences.copy(config = config))
    fun startSolo(difficulty: Difficulty, config: GameConfig = mutable.value.preferences.config) {
        if (mutable.value.storageBlocked || saved == null) return
        val generation = ++setupGeneration
        setupJob?.cancel(); aiJob?.cancel()
        mutable.update { it.copy(busy = true, difficulty = difficulty) }
        setupJob = viewModelScope.launch {
            try {
                val random = GameRandom(java.security.SecureRandom().nextLong())
                val fleet = if (difficulty == Difficulty.HARD) withContext(Dispatchers.Default) {
                    withTimeout(5000) { DefensivePlacement.place(config, random) }
                } else findLayout(config, random)
                if (fleet == null) { fail(R.string.impossible_fleet); return@launch }
                mutex.withLock {
                    val data = requireNotNull(saved)
                    val match = MatchState(UUID.randomUUID().toString(), config, data.playerId, "ai", if (random.nextInt(2) == 0) data.playerId else "ai", peerReady = true)
                    write { it.copy(solo = SoloSave(match, fleet, difficulty, random.state), preferences = it.preferences.copy(config = config)) }
                    mutable.update { it.copy(mode = Mode.AI, screen = Screen.GAME, busy = false, resultVisible = false) }; refresh()
                }
            } catch (e: TimeoutCancellationException) { fail(R.string.layout_timeout) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (!mutable.value.storageBlocked) fail(R.string.action_failed, e) }
            finally { if (generation == setupGeneration) mutable.update { it.copy(busy = false) } }
        }
    }
    fun cancelSetup() { setupGeneration++; setupJob?.cancel(); setupJob = null; mutable.update { it.copy(busy = false) } }
    fun resumeSolo() {
        mutable.update { it.copy(mode = Mode.AI, screen = Screen.GAME, resultVisible = false) }; refresh(); scheduleAi()
    }
    private suspend fun saveMatch(match: MatchState) {
        if (mutable.value.mode == Mode.AI) write { it.copy(solo = requireNotNull(it.solo).copy(match = match)) }
        else write { it.copy(nearby = requireNotNull(it.nearby).copy(match = match)) }
    }
    fun place(piece: Piece) = command {
        val match = requireNotNull(mutable.value.match)
        require(!match.ready)
        val fleet = Fleet(match.own.pieces.filterNot { it.id == piece.id } + piece)
        require(Rules.validFleet(match.config, fleet, false))
        saveMatch(match.copy(own = fleet))
    }
    fun remove(id: Int?) = command {
        val m = requireNotNull(mutable.value.match); require(!m.ready)
        saveMatch(m.copy(own = if (id == null) Fleet() else Fleet(m.own.pieces.filterNot { it.id == id })))
    }
    fun randomize() {
        val match = mutable.value.match ?: return
        if (match.ready || mutable.value.busy || mutable.value.storageBlocked) return
        val generation = ++setupGeneration
        mutable.update { it.copy(busy = true) }
        setupJob = viewModelScope.launch {
            try {
                val fleet = findLayout(match.config, GameRandom(java.security.SecureRandom().nextLong()))
                mutex.withLock {
                    if (mutable.value.match?.id == match.id && mutable.value.match?.ready == false && fleet != null) saveMatch(requireNotNull(mutable.value.match).copy(own = fleet))
                }
            } catch (e: TimeoutCancellationException) { fail(R.string.layout_timeout) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (!mutable.value.storageBlocked) fail(R.string.action_failed, e) }
            finally { if (generation == setupGeneration) mutable.update { it.copy(busy = false) } }
        }
    }
    fun ready() = command {
        val match = requireNotNull(mutable.value.match)
        require(Rules.validFleet(match.config, match.own) && !match.ready)
        saveMatch(match.copy(ready = true))
        if (mutable.value.mode == Mode.NEARBY && mutable.value.connected) transport.send(SessionProtocol.message(requireNotNull(saved?.nearby), MessageKind.READY))
        scheduleAi()
    }
    fun fire(targets: List<Int>) = command {
        val match = requireNotNull(mutable.value.match)
        if (mutable.value.mode == Mode.AI) {
            val solo = requireNotNull(saved?.solo)
            match.checkTargets(match.localId, targets)
            val move = Move(UUID.randomUUID().toString(), match.moves.size, match.localId, targets, Rules.resolve(match.config, solo.aiFleet, match.outgoing.tried, targets))
            saveMatch(match.append(move)); showFreshResult(); scheduleAi()
        } else {
            require(mutable.value.connected && mutable.value.synchronized)
            apply(SessionProtocol.fire(requireNotNull(saved?.nearby), UUID.randomUUID().toString(), targets))
        }
    }
    private fun scheduleAi() {
        aiJob?.cancel()
        val solo = saved?.solo ?: return
        if (!foreground || mutable.value.screen != Screen.GAME || mutable.value.mode != Mode.AI || solo.match.phase != Phase.PLAYING || solo.match.current == solo.match.localId || mutable.value.storageBlocked) return
        aiJob = viewModelScope.launch {
            while (isActive) {
                val turn = saved?.solo ?: break
                if (!foreground || mutable.value.screen != Screen.GAME || mutable.value.mode != Mode.AI ||
                    mutable.value.storageBlocked || turn.match.phase != Phase.PLAYING || turn.match.current == turn.match.localId) break
                delay(650)
                val random = GameRandom(turn.randomState)
                val targets = withContext(Dispatchers.Default) { FleetAi.strategy(turn.difficulty).targets(turn.match.incoming, turn.match.quota(), random) }
                val applied = mutex.withLock {
                    if (saved?.solo != turn || !foreground || mutable.value.screen != Screen.GAME) return@withLock false
                    try {
                        val move = Move("ai-${turn.match.id}-${turn.match.moves.size}", turn.match.moves.size, turn.match.peerId, targets, Rules.resolve(turn.match.config, turn.match.own, turn.match.incoming.tried, targets))
                        write { it.copy(solo = turn.copy(match = turn.match.append(move), randomState = random.state)) }
                        showFreshResult()
                        true
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { if (!mutable.value.storageBlocked) fail(R.string.action_failed, e); false }
                }
                if (!applied) break
            }
        }
    }
    private fun showFreshResult() {
        val m = mutable.value.match ?: return
        if (m.phase == Phase.FINISHED && !mutable.value.exchangePending && lastFinishedShown != m.id && mutable.value.screen == Screen.GAME) {
            lastFinishedShown = m.id; mutable.update { it.copy(resultVisible = true) }
        }
    }
    fun showResult(show: Boolean) { mutable.update { it.copy(resultVisible = show) } }
    fun resign() = command {
        val m = requireNotNull(mutable.value.match)
        require(m.phase == Phase.PLAYING)
        if (mutable.value.mode == Mode.NEARBY) {
            val s = requireNotNull(saved?.nearby)
            require(!s.blocked && m.current == m.localId) { "Wait for your turn before resigning" }
            apply(Transition(s.copy(match = m.copy(resigned = m.localId), outgoing = null, incomingResult = null, awaitingConfirm = null),
                if (mutable.value.connected) listOf(SessionProtocol.message(s, MessageKind.RESIGN),
                    SessionProtocol.reveal(s.copy(match = m.copy(resigned = m.localId)))) else emptyList()))
        } else saveMatch(m.copy(resigned = m.localId))
        aiJob?.cancel(); showFreshResult()
    }
    fun enterNearby() { mutable.update { it.copy(mode = Mode.NEARBY, screen = Screen.LOBBY) }; refresh() }
    fun resumeNearby() { mutable.update { it.copy(mode = Mode.NEARBY, screen = Screen.GAME, resultVisible = false) }; refresh() }
    fun nearbyUnavailable() { fail(R.string.permissions_needed) }
    fun startNearby(host: Boolean, resume: Boolean) {
        if (!transport.available()) { fail(R.string.play_services_needed); return }
        if (mutable.value.storageBlocked || saved == null) return
        val generation = ++setupGeneration
        setupJob?.cancel(); mutable.update { it.copy(busy = true) }
        setupJob = viewModelScope.launch {
            try {
                if (host && !resume) {
                    val config = mutable.value.preferences.config
                    val possible = findLayout(config, GameRandom(1))
                    if (possible == null) { fail(R.string.impossible_fleet); return@launch }
                }
                mutex.withLock {
                    transport.close()
                    newJoin = !host && !resume
                    if (host && !resume) {
                        val d = requireNotNull(saved)
                        val match = MatchState(UUID.randomUUID().toString(), d.preferences.config, d.playerId, "unpaired", d.playerId)
                        write { it.copy(nearby = NearbySave(match, host = true)) }
                    }
                    val hosting = if (resume) requireNotNull(saved?.nearby).host else host
                    mutable.update { it.copy(mode = Mode.NEARBY, screen = Screen.LOBBY, busy = false, peers = emptyList(), connected = false, synchronized = false,
                        searching = true, hosting = hosting, pairing = null, offer = null, rematchRequested = false, rematchSent = false) }
                    refresh()
                    if (hosting) transport.host(requireNotNull(saved).preferences.name) else transport.discover()
                }
            } catch (e: TimeoutCancellationException) { fail(R.string.layout_timeout) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (!mutable.value.storageBlocked) fail(R.string.connection_error, e); mutable.update { it.copy(searching = false) } }
            finally { if (generation == setupGeneration) mutable.update { it.copy(busy = false) } }
        }
    }
    fun connect(peer: Peer) = command { transport.connect(peer, requireNotNull(saved).preferences.name) }
    fun pairing(accept: Boolean) = command {
        val p = mutable.value.pairing ?: return@command
        if (accept) transport.accept(p.peer.endpoint) else transport.reject(p.peer.endpoint)
        mutable.update { it.copy(pairing = null) }
    }
    private suspend fun event(event: TransportEvent) {
        if (!foreground || mutable.value.mode != Mode.NEARBY || mutable.value.screen !in listOf(Screen.LOBBY, Screen.GAME)) return
        when (event) {
            is TransportEvent.Found -> mutable.update { it.copy(peers = it.peers.filterNot { p -> p.endpoint == event.peer.endpoint } + event.peer) }
            is TransportEvent.Lost -> mutable.update { it.copy(peers = it.peers.filterNot { p -> p.endpoint == event.endpoint }) }
            is TransportEvent.Pairing -> mutable.update { it.copy(pairing = event) }
            is TransportEvent.Connected -> {
                mutable.update { it.copy(connected = true, searching = false, synchronized = false) }
                val d = requireNotNull(saved)
                val matchId = if (newJoin) "" else d.nearby?.match?.id.orEmpty()
                transport.send(WireMessage(kind = MessageKind.INTRO, player = d.playerId, name = d.preferences.name, matchId = matchId))
            }
            TransportEvent.Disconnected -> mutable.update { it.copy(connected = false, synchronized = false, searching = false, pairing = null) }
            is TransportEvent.Failure -> {
                transport.close(); mutable.update { it.copy(connected = false, synchronized = false, searching = false, pairing = null) }
                fail(R.string.connection_error, IllegalStateException(event.cause))
            }
            is TransportEvent.Message -> if (mutable.value.connected) receive(event.value)
        }
    }
    private suspend fun receive(msg: WireMessage) {
        require(msg.version == 2) { "Incompatible session version" }
        val data = requireNotNull(saved)
        val s = data.nearby
        when (msg.kind) {
            MessageKind.INTRO -> {
                require(msg.player != data.playerId)
                if (mutable.value.hosting) {
                    requireNotNull(s)
                    if (s.match.peerId == "unpaired") {
                        require(msg.matchId.isEmpty()) { "Opponent is trying to resume a different match" }
                        val first = if (java.security.SecureRandom().nextBoolean()) data.playerId else msg.player
                        write { it.copy(nearby = s.copy(match = s.match.copy(peerId = msg.player, first = first), peerName = msg.name)) }
                        sendOffer()
                    } else {
                        require(msg.player == s.match.peerId && (msg.matchId == s.match.id || !s.accepted && msg.matchId.isEmpty())) { "Use the original opponent and saved match" }
                        if (!s.accepted) sendOffer() else transport.send(SessionProtocol.sync(s))
                    }
                } else if (!newJoin) {
                    requireNotNull(s)
                    require(msg.player == s.match.peerId && msg.matchId == s.match.id) { "Use the original host and saved match" }
                    transport.send(SessionProtocol.sync(s))
                }
            }
            MessageKind.OFFER -> {
                require(!mutable.value.hosting && msg.first in listOf(data.playerId, msg.player))
                if (!newJoin && s != null && s.match.phase != Phase.FINISHED) require(msg.matchId == s.match.id && msg.config == s.match.config && msg.player == s.match.peerId)
                requireNotNull(msg.config).validate()
                pendingOffer = msg
                mutable.update { it.copy(offer = msg) }
            }
            MessageKind.ACCEPT -> {
                requireNotNull(s); require(s.host && msg.matchId == s.match.id && msg.player == s.match.peerId)
                write { it.copy(nearby = s.copy(accepted = true)) }
                transport.send(SessionProtocol.sync(requireNotNull(saved?.nearby)))
            }
            MessageKind.SYNC -> {
                requireNotNull(s)
                require(s.accepted || s.host)
                val transition = SessionProtocol.receive(s.copy(accepted = true), msg)
                apply(transition)
                if (!mutable.value.connected || !foreground) return
                val wasSynced = mutable.value.synchronized
                mutable.update { it.copy(synchronized = true, screen = Screen.GAME) }
                if (!wasSynced) transport.send(SessionProtocol.sync(requireNotNull(saved?.nearby)))
                refresh()
            }
            MessageKind.PAUSE -> {
                requireNotNull(s); require(msg.matchId == s.match.id && msg.player == s.match.peerId)
                transport.close(); mutable.update { it.copy(connected = false, synchronized = false) }
            }
            MessageKind.REMATCH -> {
                requireNotNull(s); require(msg.matchId == s.match.id && msg.player == s.match.peerId && s.match.phase == Phase.FINISHED)
                mutable.update { it.copy(rematchRequested = true) }
                if (mutable.value.rematchSent && s.host) newRematch()
            }
            MessageKind.ERROR -> error("Peer rejected session")
            else -> { requireNotNull(s); apply(SessionProtocol.receive(s, msg)); showFreshResult() }
        }
    }
    private suspend fun sendOffer() {
        val s = requireNotNull(saved?.nearby)
        transport.send(SessionProtocol.message(s, MessageKind.OFFER).copy(config = s.match.config, first = s.match.first, name = requireNotNull(saved).preferences.name))
    }
    fun acceptOffer() {
        if (mutable.value.busy || mutable.value.storageBlocked) return
        val generation = ++setupGeneration
        mutable.update { it.copy(busy = true) }
        setupJob = viewModelScope.launch { try { mutex.withLock {
        val msg = requireNotNull(pendingOffer)
        val config = requireNotNull(msg.config)
        val possible = findLayout(config, GameRandom(1))
        require(possible != null) { "Impossible fleet configuration" }
        val data = requireNotNull(saved)
        val old = data.nearby
        val match = if (old?.match?.id == msg.matchId) old.match else MatchState(msg.matchId, config, data.playerId, msg.player, msg.first)
        write { it.copy(nearby = NearbySave(match, host = false, peerName = msg.name, accepted = true)) }
        newJoin = false; pendingOffer = null
        mutable.update { it.copy(offer = null, synchronized = false, rematchRequested = false, rematchSent = false, resultVisible = false) }
        transport.send(SessionProtocol.message(requireNotNull(saved?.nearby), MessageKind.ACCEPT))
        } } catch (e: TimeoutCancellationException) { fail(R.string.layout_timeout) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { pauseNearby(); if (!mutable.value.storageBlocked) fail(connectionError(e), e) }
        finally { if (generation == setupGeneration) mutable.update { it.copy(busy = false) } } }
    }
    private suspend fun apply(transition: Transition) {
        write { it.copy(nearby = transition.save) }
        if (!mutable.value.connected) return
        try { transition.send.forEach { transport.send(it) } }
        catch (e: Exception) {
            transport.close(); mutable.update { it.copy(connected = false, synchronized = false) }; throw e
        }
    }
    fun rematch() = command {
        val s = requireNotNull(saved?.nearby)
        require(s.match.phase == Phase.FINISHED && mutable.value.connected)
        mutable.update { it.copy(rematchSent = true) }
        transport.send(SessionProtocol.message(s, MessageKind.REMATCH))
        if (s.host && mutable.value.rematchRequested) newRematch()
    }
    private suspend fun newRematch() {
        val old = requireNotNull(saved?.nearby)
        val first = if (old.match.first == old.match.localId) old.match.peerId else old.match.localId
        val match = old.match.copy(id = UUID.randomUUID().toString(), first = first, own = Fleet(), ready = false, peerReady = false, moves = emptyList(), resigned = null)
        write { it.copy(nearby = NearbySave(match, host = true, peerName = old.peerName)) }
        mutable.update { it.copy(synchronized = false, screen = Screen.LOBBY, resultVisible = false, rematchRequested = false, rematchSent = false) }
        sendOffer()
    }
    private fun pauseNearby() {
        if (mutable.value.connected || mutable.value.searching || mutable.value.pairing != null) {
            // Connection closure is itself the durable pause signal; no forfeit is inferred.
            transport.close()
            mutable.update { it.copy(connected = false, synchronized = false, searching = false, pairing = null, offer = null) }
        }
    }
    fun foreground(active: Boolean) {
        foreground = active
        if (!active) { aiJob?.cancel(); pauseNearby() } else scheduleAi()
    }
    fun export(uri: Uri) = command {
        val text = SaveFormat.encode(requireNotNull(saved))
        withContext(Dispatchers.IO) {
            val output = getApplication<Application>().contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open backup destination")
            output.use { it.write(text.toByteArray()) }
        }
    }
    fun previewImport(uri: Uri) = command {
        aiJob?.cancel(); pauseNearby()
        val backup = withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openInputStream(uri).use { stream ->
                val bytes = requireNotNull(stream).readBytesLimited(SaveFormat.MAX_BACKUP_BYTES)
                SaveFormat.decode(bytes.toString(Charsets.UTF_8))
            }
        }
        pendingImport = backup
        mutable.update { it.copy(importPreview = ImportSummary(backup.records.size, backup.solo != null)) }
    }
    fun importBackup(confirm: Boolean) = command {
        if (confirm) { val backup = requireNotNull(pendingImport); write { SaveFormat.restore(it, backup) } }
        pendingImport = null
        mutable.update { it.copy(importPreview = null) }
    }
    override fun onCleared() { transport.close(); super.onCleared() }
}
private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) { val n = read(buffer); if (n < 0) break; require(output.size() + n <= limit) { "Backup is too large" }; output.write(buffer, 0, n) }
    return output.toByteArray()
}
