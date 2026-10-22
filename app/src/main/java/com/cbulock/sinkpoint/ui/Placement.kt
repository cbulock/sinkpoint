package com.cbulock.sinkpoint.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.cbulock.sinkpoint.GameViewModel
import com.cbulock.sinkpoint.Mode
import com.cbulock.sinkpoint.R
import com.cbulock.sinkpoint.UiState
import com.cbulock.sinkpoint.game.*

/** Keep the grabbed segment under the finger; snap the whole piece inside the grid. */
fun placementAt(config: GameConfig, id: Int, cell: Int, horizontal: Boolean, grabbed: Int): Piece {
    val length = config.lengths[id]
    val row = (cell / config.size - if (horizontal) 0 else grabbed).coerceIn(0, config.size - if (horizontal) 1 else length)
    val col = (cell % config.size - if (horizontal) grabbed else 0).coerceIn(0, config.size - if (horizontal) length else 1)
    return Piece(id, row * config.size + col, horizontal)
}

private fun Modifier.placementGesture(
    enabled: Boolean, start: (Offset) -> Unit, move: (Offset) -> Unit,
    finish: () -> Unit, cancel: () -> Unit, tap: (Offset) -> Unit,
) = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var dragging = false
        var canceled = false
        try {
            do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (event.changes.size > 1 || change == null || change.isConsumed) canceled = true
                if (!canceled && change != null) {
                    if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        dragging = true
                        start(down.position)
                    }
                    if (dragging) { move(change.position); change.consume() }
                    if (!change.pressed) {
                        if (dragging) finish()
                        else if (change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) tap(change.position)
                    }
                }
            } while (event.changes.any { it.pressed })
        } finally { cancel() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun PlacementScreen(ui: UiState, model: GameViewModel, onReconnect: () -> Unit) {
    val match = ui.match ?: return
    val config = match.config
    val enabled = !match.ready && !ui.busy && !ui.storageBlocked
    var selected by rememberSaveable(match.id) { mutableIntStateOf(0) }
    var horizontal by rememberSaveable(match.id) { mutableStateOf(true) }
    var preview by remember(match.id) { mutableStateOf<Piece?>(null) }
    var invalid by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var boardOrigin by remember { mutableStateOf(Offset.Zero) }
    var side by remember { mutableFloatStateOf(1f) }
    var dragged by remember { mutableStateOf<Piece?>(null) }
    var grabbed by remember { mutableIntStateOf(0) }
    val rotateLabel = stringResource(R.string.rotate)
    val moreLabel = stringResource(R.string.more_actions)
    val orientationLabel = stringResource(if (horizontal) R.string.horizontal else R.string.vertical)
    val placedLabel = stringResource(R.string.piece_placed)
    val unplacedLabel = stringResource(R.string.piece_unplaced)
    val margin = with(LocalDensity.current) { 20.dp.toPx() }
    val transform = BoardTransform(side.coerceAtLeast(margin + 1), margin, 1f, Offset.Zero, config.size)
    fun valid(piece: Piece) = Rules.validFleet(config, Fleet(match.own.pieces.filterNot { it.id == piece.id } + piece), false)
    fun choose(id: Int) { selected = id; horizontal = match.own.pieces.find { it.id == id }?.horizontal ?: horizontal; invalid = false }
    fun commit(piece: Piece) {
        if (!enabled) return
        if (valid(piece)) { model.place(piece); invalid = false } else invalid = true
    }
    fun tap(cell: Int) {
        val existing = match.own.pieces.find { cell in Rules.cells(config, it) }
        if (existing != null) choose(existing.id)
        else commit(placementAt(config, selected, cell, horizontal, (config.lengths[selected] - 1) / 2))
    }
    fun move(point: Offset) {
        val piece = dragged ?: return
        preview = transform.hit(point - boardOrigin)?.let { placementAt(config, piece.id, it, piece.horizontal, grabbed) }
    }
    val currentMove by rememberUpdatedState<(Offset) -> Unit>({ move(it) })
    val currentDrop by rememberUpdatedState<() -> Unit>({ preview?.let { commit(it) } })
    val currentBoardStart by rememberUpdatedState<(Offset) -> Unit>({ point ->
        transform.hit(point)?.let { cell ->
            val piece = match.own.pieces.find { cell in Rules.cells(config, it) }
            if (piece != null) {
                choose(piece.id); dragged = piece; grabbed = Rules.cells(config, piece).indexOf(cell)
            }
        }
    })
    val currentTap by rememberUpdatedState<(Offset) -> Unit>({ transform.hit(it)?.let { cell -> tap(cell) } })
    fun cancel() { dragged = null; preview = null }
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // A near-square fold can be wider than the usable height after app bars.
            // Reserve a readable tray before sizing the board, rather than letting
            // an unweighted square consume almost all of the row's width.
            val landscape = maxWidth >= 600.dp && maxWidth > maxHeight && LocalDensity.current.fontScale <= 1.3f
            val boardLimit = if (landscape) minOf(maxHeight - 16.dp, maxWidth - 272.dp).coerceAtLeast(120.dp) else maxWidth - 32.dp
            val headingAndTray: @Composable () -> Unit = {
                if (ui.mode == Mode.NEARBY && (!ui.connected || !ui.synchronized)) {
                    Text(stringResource(R.string.paused_detail))
                    TextButton(onClick = onReconnect) { Text(stringResource(R.string.reconnect)) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.place_fleet), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                    Text(stringResource(R.string.placed_count, match.own.pieces.size, config.lengths.size), style = MaterialTheme.typography.labelMedium)
                }
                Text(stringResource(if (match.ready) R.string.waiting_ready else R.string.placement_short_hint), style = MaterialTheme.typography.bodySmall)
                if (!match.ready) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    config.lengths.forEachIndexed { id, length ->
                        var origin by remember { mutableStateOf(Offset.Zero) }
                        val placed = match.own.pieces.any { it.id == id }
                        val label = stringResource(R.string.piece_number, id + 1, length)
                        val currentStart by rememberUpdatedState<() -> Unit>({
                            choose(id); dragged = Piece(id, 0, horizontal); grabbed = (length - 1) / 2; invalid = false
                        })
                        val currentChoose by rememberUpdatedState<() -> Unit>({ choose(id) })
                        Surface(shape = RoundedCornerShape(12.dp), color = if (id == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("piece-$id")
                                .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
                                .placementGesture(enabled, { currentStart() }, { currentMove(origin + it) }, { currentDrop() }, { cancel() }, { currentChoose() })
                                .semantics { contentDescription = label; this.selected = id == selected; stateDescription = if (placed) placedLabel else unplacedLabel; if (enabled) onClick { currentChoose(); true } }) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (placed) "✓ $length" else "$length", style = MaterialTheme.typography.labelSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    repeat(length) { Surface(Modifier.size(6.dp), shape = RoundedCornerShape(2.dp), color = MaterialTheme.colorScheme.primary) {} }
                                }
                            }
                        }
                    }
                }
            }
            val board: @Composable () -> Unit = {
                FleetBoard(match.id + "placement", config, match.own,
                    selected = match.own.pieces.find { it.id == selected }?.let { Rules.cells(config, it).toSet() }.orEmpty(),
                    preview = preview, previewValid = preview?.let(::valid) != false,
                    enabled = enabled, onSelect = { tap(it) }, placement = true,
                    modifier = if (landscape) Modifier.width(boardLimit) else Modifier.widthIn(max = boardLimit).fillMaxWidth(),
                    boardModifier = Modifier.testTag("placement-board")
                        .onGloballyPositioned { boardOrigin = it.boundsInRoot().topLeft; side = it.size.width.toFloat() }
                        .placementGesture(enabled, { currentBoardStart(it) }, { currentMove(boardOrigin + it) }, { currentDrop() }, { cancel() }, { currentTap(it) }))
            }
            if (landscape) Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { headingAndTray() }
                board()
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                headingAndTray()
                board()
            }
        }
        if (!match.ready) Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (invalid || preview?.let { !valid(it) } == true) Text(stringResource(R.string.invalid_placement), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(onClick = {
                        val piece = match.own.pieces.find { it.id == selected }
                        if (piece == null) horizontal = !horizontal else {
                            val center = Rules.cells(config, piece)[(config.lengths[selected] - 1) / 2]
                            val rotated = placementAt(config, selected, center, !piece.horizontal, (config.lengths[selected] - 1) / 2)
                            if (valid(rotated)) { horizontal = rotated.horizontal; commit(rotated) } else invalid = true
                        }
                    }, enabled = enabled, modifier = Modifier.size(48.dp).semantics { contentDescription = rotateLabel; stateDescription = orientationLabel }) {
                        val ink = MaterialTheme.colorScheme.onSecondaryContainer
                        Canvas(Modifier.size(24.dp)) {
                            drawArc(ink, 40f, 290f, false, Offset(size.width * .18f, size.height * .18f), androidx.compose.ui.geometry.Size(size.width * .64f, size.height * .64f), style = Stroke(2.dp.toPx()))
                            drawPath(Path().apply { moveTo(size.width * .88f, size.height * .12f); lineTo(size.width * .88f, size.height * .43f); lineTo(size.width * .57f, size.height * .43f) }, ink, style = Stroke(2.dp.toPx()))
                        }
                    }
                    OutlinedButton(onClick = {
                        cancel()
                        invalid = false
                        model.randomize()
                    }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        val ink = LocalContentColor.current
                        Canvas(Modifier.size(18.dp)) {
                            val stroke = Stroke(2.dp.toPx())
                            drawPath(Path().apply {
                                moveTo(size.width * .1f, size.height * .2f)
                                cubicTo(size.width * .5f, size.height * .2f, size.width * .5f, size.height * .8f, size.width * .9f, size.height * .8f)
                                moveTo(size.width * .1f, size.height * .8f)
                                cubicTo(size.width * .5f, size.height * .8f, size.width * .5f, size.height * .2f, size.width * .9f, size.height * .2f)
                                moveTo(size.width * .7f, 0f)
                                lineTo(size.width * .9f, size.height * .2f)
                                lineTo(size.width * .7f, size.height * .4f)
                                moveTo(size.width * .7f, size.height * .6f)
                                lineTo(size.width * .9f, size.height * .8f)
                                lineTo(size.width * .7f, size.height)
                            }, ink, style = stroke)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.randomize))
                    }
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = moreLabel }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.remove)) }, onClick = { menu = false; model.remove(selected) }, enabled = enabled && match.own.pieces.any { it.id == selected })
                            DropdownMenuItem(text = { Text(stringResource(R.string.clear)) }, onClick = { menu = false; model.remove(null) }, enabled = enabled)
                        }
                    }
                    Button(onClick = model::ready, enabled = enabled && Rules.validFleet(config, match.own), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.ready)) }
                }
            }
        }
    }
}





