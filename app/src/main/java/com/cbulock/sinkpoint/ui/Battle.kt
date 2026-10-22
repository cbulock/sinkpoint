package com.cbulock.sinkpoint.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cbulock.sinkpoint.GameViewModel
import com.cbulock.sinkpoint.Mode
import com.cbulock.sinkpoint.R
import com.cbulock.sinkpoint.UiState
import com.cbulock.sinkpoint.game.*

@Composable fun GameScreen(ui: UiState, model: GameViewModel, onResign: () -> Unit, onReconnect: () -> Unit) {
    val match = ui.match ?: return
    if (match.phase == Phase.PLACEMENT) { PlacementScreen(ui, model, onReconnect); return }
    var chosen by rememberSaveable(match.id, match.moves.size, ui.connected) { mutableStateOf(emptyList<Int>()) }
    var ownBoard by rememberSaveable(match.id) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val available = !ui.storageBlocked && !ui.busy
    val linked = ui.mode == Mode.AI || ui.connected && ui.synchronized
    val canFire = available && linked && !ui.exchangePending && match.phase == Phase.PLAYING && match.current == match.localId
    // Track turn ownership separately from availability so saving/reconnecting cannot repeat the cue.
    var previousPlayer by rememberSaveable(match.id) { mutableStateOf(match.current) }
    var turnFeedbackPending by rememberSaveable(match.id) { mutableStateOf(false) }
    LaunchedEffect(match.current, match.phase, canFire, ui.mode, ui.preferences.haptics) {
        if (ui.mode != Mode.NEARBY || match.phase != Phase.PLAYING) {
            turnFeedbackPending = false
        } else {
            if (previousPlayer != match.current) {
                turnFeedbackPending = match.current == match.localId
            }
            if (turnFeedbackPending && canFire) {
                if (ui.preferences.haptics) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                turnFeedbackPending = false
            }
        }
        previousPlayer = match.current
    }
    val status = stringResource(when {
        match.phase == Phase.FINISHED -> outcomeTitle(match)
        !linked || ui.storageBlocked -> R.string.paused
        ui.exchangePending || ui.busy -> R.string.syncing
        match.current == match.localId -> R.string.your_turn
        ui.mode == Mode.AI -> R.string.thinking
        else -> R.string.their_turn
    })
    fun select(cell: Int) {
        if (!canFire || cell in match.outgoing.tried) return
        chosen = if (cell in chosen) chosen - cell else if (match.quota() == 1) listOf(cell) else if (chosen.size < match.quota()) chosen + cell else chosen
    }
    val fire = {
        if (ui.preferences.haptics) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        model.fire(chosen)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val largeText = LocalDensity.current.fontScale > 1.3f
        val wide = maxWidth >= 640.dp && !largeText
        val landscape = wide && maxHeight < 480.dp
        val tallTablet = wide && maxHeight >= maxWidth * 1.25f
        val wideTablet = wide && maxWidth >= 1000.dp
        val boardLimit = (maxHeight - 62.dp).coerceAtLeast(152.dp)
        val header: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val dot = if (canFire) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    Canvas(Modifier.size(8.dp)) { drawCircle(dot) }
                    Text(status, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (match.phase != Phase.FINISHED) TextButton(onClick = onResign,
                    enabled = available && !ui.exchangePending && (ui.mode == Mode.AI || match.current == match.localId)) {
                    Text(stringResource(R.string.resign), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val connection: @Composable () -> Unit = {
            if (!linked) {
                Text(stringResource(R.string.paused_detail), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onReconnect) { Text(stringResource(R.string.reconnect)) }
            }
        }
        val actions: @Composable (Boolean) -> Unit = { stacked ->
            if (match.phase == Phase.PLAYING) FirePanel(chosen, match, canFire, stacked, fire, if (!wide) status else null)
            else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { model.showResult(true) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.view_result)) }
                if (ui.mode == Mode.NEARBY) {
                    if (ui.rematchRequested) Text(stringResource(R.string.rematch_offer))
                    if (ui.rematchSent) Text(stringResource(R.string.rematch_wait))
                    OutlinedButton(onClick = model::rematch, enabled = linked && !ui.rematchSent) { Text(stringResource(R.string.play_again)) }
                } else OutlinedButton(onClick = { model.startSolo(ui.difficulty, match.config) }) { Text(stringResource(R.string.play_again)) }
            }
        }
        val boards: @Composable RowScope.() -> Unit = {
            BattleBoard(match, false, chosen, canFire, { select(it) }, Modifier.weight(1f), boardLimit, opponentFleet = ui.opponentFleet)
            BattleBoard(match, true, chosen, canFire, { select(it) }, Modifier.weight(1f), boardLimit, opponentFleet = ui.opponentFleet)
        }
        if (tallTablet) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                header()
                BoxWithConstraints(Modifier.weight(1.7f).fillMaxWidth()) {
                    BattleBoard(match, false, chosen, canFire, { select(it) },
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        (maxHeight - 80.dp).coerceAtLeast(152.dp), opponentFleet = ui.opponentFleet)
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                        BattleBoard(match, true, chosen, canFire, { select(it) },
                            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                            (maxHeight - 80.dp).coerceAtLeast(152.dp), opponentFleet = ui.opponentFleet)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        actions(true)
                        OpponentProgress(match)
                        connection()
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        } else if (landscape) {
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp), content = boards)
                Column(Modifier.width(224.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    header()
                    actions(true)
                    OpponentProgress(match)
                    connection()
                }
            }
        } else Column(Modifier.fillMaxSize().padding(horizontal = if (wide) 12.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (wide || match.phase == Phase.FINISHED) header()
            // Kept outside board scrolling so the opponent's progress is visible on the target tab.
            if (!wide) OpponentProgress(match, onShow = { ownBoard = true })
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val fittedBoard = (maxHeight - if (wide) 72.dp else 92.dp).coerceAtLeast(152.dp)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                connection()
                if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BattleBoard(match, false, chosen, canFire, { select(it) }, Modifier.weight(1f), fittedBoard, opponentFleet = ui.opponentFleet)
                    BattleBoard(match, true, chosen, canFire, { select(it) }, Modifier.weight(1f), fittedBoard, opponentFleet = ui.opponentFleet)
                }
                else {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(R.string.target_board, R.string.own_board).forEachIndexed { index, title ->
                            SegmentedButton(selected = ownBoard == (index == 1), onClick = { ownBoard = index == 1 },
                                shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(stringResource(title)) }
                        }
                    }
                    BattleBoard(match, ownBoard, chosen, canFire, { select(it) }, Modifier.fillMaxWidth(), fittedBoard, showHeading = false, opponentFleet = ui.opponentFleet)
                    if (match.phase != Phase.FINISHED) TextButton(onClick = onResign,
                        enabled = available && !ui.exchangePending && (ui.mode == Mode.AI || match.current == match.localId)) { Text(stringResource(R.string.resign)) }
                }
            }
            }
            if (wideTablet) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f)) { OpponentProgress(match) }
                Box(Modifier.weight(1f)) { actions(false) }
            }
            else if (ownBoard && !wide && match.phase == Phase.PLAYING) {
                Text(status, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.labelLarge)
                Button(onClick = { ownBoard = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.return_to_target)) }
            } else {
                if (wide) OpponentProgress(match, compact = true)
                actions(largeText)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable private fun BattleBoard(match: MatchState, own: Boolean, chosen: List<Int>, canFire: Boolean,
    select: (Int) -> Unit, modifier: Modifier, boardLimit: androidx.compose.ui.unit.Dp, showHeading: Boolean = true, opponentFleet: Fleet? = null) {
    val knowledge = if (own) match.incoming else match.outgoing
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showHeading) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(if (own) R.string.own_board else R.string.target_board), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.fleet_afloat, match.config.lengths.size - knowledge.sunk.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FleetBoard(match.id + if (own) "own" else "target", match.config, fleet = if (own) match.own else opponentFleet.takeIf { match.phase == Phase.FINISHED },
            knowledge = knowledge, selected = if (own || match.phase == Phase.FINISHED) emptySet() else chosen.toSet(), enabled = !own && canFire, onSelect = select,
            modifier = Modifier.widthIn(max = boardLimit).fillMaxWidth(), boardModifier = Modifier.testTag(if (own) "own-play-board" else "target-play-board"),
            latestMove = if (own) match.incoming.moves.lastOrNull()?.targets?.toSet().orEmpty() else emptySet(), showFleetStatus = true, showZoomControls = false, opponent = !own)
    }
}

@Composable private fun FirePanel(chosen: List<Int>, match: MatchState, canFire: Boolean, stacked: Boolean, onFire: () -> Unit, status: String? = null) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        val summary: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (status != null) Text(status, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold, color = if (canFire) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.selected_count, chosen.size, match.quota(match.localId)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (chosen.isEmpty()) stringResource(R.string.choose_targets) else chosen.joinToString(" · ") { coordinate(it, match.config.size) },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, minLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val button: @Composable (Modifier) -> Unit = { modifier ->
            Button(onClick = onFire, enabled = canFire && chosen.size == match.quota(), modifier = modifier.heightIn(min = 60.dp).testTag("fire-action"),
                shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .16f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                val ink = LocalContentColor.current
                Canvas(Modifier.size(24.dp)) {
                    drawCircle(ink, size.minDimension * .30f, style = Stroke(2.dp.toPx()))
                    drawCircle(ink, size.minDimension * .07f)
                    val c = center
                    listOf(Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f)).forEach { direction ->
                        drawLine(ink, c + direction * (size.minDimension * .23f), c + direction * (size.minDimension * .48f), 2.dp.toPx())
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.fire), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        if (stacked) Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            summary(Modifier.fillMaxWidth().padding(horizontal = 4.dp))
            button(Modifier.fillMaxWidth())
        } else Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            summary(Modifier.weight(1f))
            button(if (status != null) Modifier.width(140.dp) else Modifier.weight(1f))
        }
    }
}

@Composable private fun OpponentProgress(match: MatchState, onShow: (() -> Unit)? = null, compact: Boolean = false) {
    val hit = stringResource(R.string.hit_cell)
    val miss = stringResource(R.string.miss_cell)
    val showLabel = stringResource(R.string.show_latest_move)
    val latest = match.incoming.moves.lastOrNull()?.let { move ->
        move.targets.joinToString(" · ") { "${coordinate(it, match.config.size)} ${if (it in move.outcome.hits) hit else miss}" }
    } ?: stringResource(R.string.no_opponent_moves)
    Surface(Modifier.fillMaxWidth().testTag("opponent-progress").then(if (onShow == null) Modifier else
        Modifier.clickable(onClickLabel = showLabel, onClick = onShow).semantics { contentDescription = showLabel }),
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        if (compact) Row(Modifier.heightIn(min = 40.dp).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.opponent_progress, match.incoming.hits.size, match.incoming.sunk.size, match.config.lengths.size),
                Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(latest, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.opponent_progress, match.incoming.hits.size, match.incoming.sunk.size, match.config.lengths.size),
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(latest, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (onShow != null) Text("→", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
