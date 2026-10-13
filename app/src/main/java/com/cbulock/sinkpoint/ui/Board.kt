package com.cbulock.sinkpoint.ui

import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cbulock.sinkpoint.R
import com.cbulock.sinkpoint.game.*
import kotlin.math.roundToInt

fun coordinate(cell: Int, size: Int) = "${'A' + cell / size}${cell % size + 1}"
data class BoardTransform(val side: Float, val margin: Float, val zoom: Float, val pan: Offset, val size: Int) {
    val grid = side - margin
    val cell = grid / size * zoom
    fun origin(index: Int) = Offset(margin + pan.x + index % size * cell, margin + pan.y + index / size * cell)
    fun hit(point: Offset): Int? {
        if (point.x < margin || point.y < margin || point.x >= side || point.y >= side) return null
        val x = ((point.x - margin - pan.x) / cell).toInt()
        val y = ((point.y - margin - pan.y) / cell).toInt()
        return if (x in 0 until size && y in 0 until size) y * size + x else null
    }
    fun clamp(p: Offset, scale: Float) = Offset(p.x.coerceIn(-grid * (scale - 1), 0f), p.y.coerceIn(-grid * (scale - 1), 0f))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun FleetBoard(
    identity: String,
    config: GameConfig,
    fleet: Fleet? = null,
    knowledge: Knowledge = Knowledge(config, emptyList()),
    selected: Set<Int> = emptySet(),
    preview: Piece? = null,
    enabled: Boolean = false,
    onSelect: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    boardModifier: Modifier = Modifier,
    placement: Boolean = false,
    previewValid: Boolean = true,
    latestMove: Set<Int> = emptySet(),
    showFleetStatus: Boolean = false,
    showZoomControls: Boolean = true,
    opponent: Boolean = fleet == null,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var side by remember { mutableFloatStateOf(0f) }
    var zoom by rememberSaveable(identity) { mutableFloatStateOf(1f) }
    var panX by rememberSaveable(identity) { mutableFloatStateOf(0f) }
    var panY by rememberSaveable(identity) { mutableFloatStateOf(0f) }
    val margin = with(density) { 20.dp.toPx() }
    val transform = BoardTransform(side.coerceAtLeast(margin + 1), margin, zoom, Offset(panX, panY), config.size)
    val latestTransform by rememberUpdatedState(transform)
    val latestSelect by rememberUpdatedState(onSelect)
    val ownCells = remember(config, fleet) { fleet?.pieces?.flatMap { Rules.cells(config, it) }?.toSet().orEmpty() }
    val previewCells = remember(config, preview) { preview?.let { Rules.cells(config, it).toSet() }.orEmpty() }
    val sunkCells = knowledge.sunk.flatMap { it.cells }.toSet()
    val labelUnknown = stringResource(R.string.unknown_cell)
    val labelPiece = stringResource(if (opponent) R.string.opponent_piece_cell else R.string.piece_cell)
    val labelMiss = stringResource(R.string.miss_cell)
    val labelHit = stringResource(R.string.hit_cell)
    val labelSunk = stringResource(R.string.sunk_cell)
    val labelSelected = stringResource(if (placement) R.string.selected_piece_cell else R.string.selected_cell)
    val labelPreview = stringResource(R.string.preview_cell)
    val selectLabel = stringResource(R.string.select_cell)
    val latestLabel = stringResource(R.string.latest_move_cell)
    val flicker = if (knowledge.hits.isNotEmpty()) {
        val transition = rememberInfiniteTransition(label = "fire")
        val value by transition.animateFloat(.92f, 1.08f,
            infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "flame height")
        value
    } else 1f
    fun setZoom(value: Float) {
        zoom = value.coerceIn(1f, 3f)
        val pan = transform.clamp(Offset(panX, panY), zoom)
        panX = pan.x; panY = pan.y
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
            .onSizeChanged { side = it.width.toFloat(); panX = 0f; panY = 0f }
            .then(boardModifier)
            .pointerInput(identity, config.size, enabled, side, placement) {
                if (placement) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val initial = down.position
                    val initialCell = latestTransform.hit(initial)
                    val initialTime = down.uptimeMillis
                    var canceled = false
                    var last = initial
                    do {
                        val event = awaitPointerEvent()
                        val t = latestTransform
                        val change = event.changes.firstOrNull { it.id == down.id }
                        val multi = event.changes.size > 1
                        if (multi || change == null || change.isConsumed || change.position.minus(initial).getDistance() > viewConfiguration.touchSlop || change.uptimeMillis - initialTime > viewConfiguration.longPressTimeoutMillis) canceled = true
                        if (change != null && t.hit(change.position) != initialCell) canceled = true
                        if (multi || canceled && t.zoom > 1f && event.changes.any { it.pressed }) {
                            val newZoom = (t.zoom * event.calculateZoom()).coerceIn(1f, 3f)
                            val center = event.changes.filter { it.pressed }.map { it.position }.let { points -> if (points.isEmpty()) initial else points.reduce(Offset::plus) / points.size.toFloat() }
                            val ratio = newZoom / t.zoom
                            val base = center - Offset(t.margin, t.margin)
                            val newPan = t.clamp(base - (base - t.pan) * ratio + event.calculatePan(), newZoom)
                            zoom = newZoom; panX = newPan.x; panY = newPan.y
                            event.changes.forEach { it.consume() }
                        }
                        change?.let { last = it.position }
                    } while (event.changes.any { it.pressed })
                    if (!canceled && enabled && initialCell != null && latestTransform.hit(last) == initialCell) latestSelect(initialCell)
                }
            }) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(colors.surfaceContainerHigh)
                val t = transform
                val gap = minOf(3.dp.toPx(), t.cell * .055f)
                for (index in 0 until config.size * config.size) {
                    val origin = t.origin(index) + Offset(gap, gap)
                    val tile = Size(t.cell - gap * 2, t.cell - gap * 2)
                    val tried = index in knowledge.tried
                    val own = index in ownCells
                    val fill = when {
                        placement && index in previewCells -> if (previewValid) colors.secondaryContainer else colors.errorContainer
                        placement && index in selected -> colors.secondaryContainer
                        own -> colors.primaryContainer
                        index in selected -> colors.primaryContainer
                        tried -> colors.surface.copy(alpha = .65f)
                        else -> colors.surfaceContainer
                    }
                    drawRoundRect(fill, origin, tile, CornerRadius(t.cell * .15f))
                    val center = origin + Offset(tile.width / 2, tile.height / 2)
                    if (index in knowledge.hits) {
                        drawFlame(center, t.cell * .66f, flicker)
                    } else if (tried) {
                        val half = t.cell * .13f
                        drawLine(colors.onSurfaceVariant, center - Offset(half, half), center + Offset(half, half), t.cell * .065f, StrokeCap.Round)
                        drawLine(colors.onSurfaceVariant, center + Offset(-half, half), center + Offset(half, -half), t.cell * .065f, StrokeCap.Round)
                    }
                    if (index in selected || index in previewCells || index in sunkCells) {
                        drawRoundRect(if (index in previewCells && !previewValid) colors.error else if (index in sunkCells) colors.tertiary else colors.primary,
                            origin, tile, CornerRadius(t.cell * .15f), style = Stroke(2.dp.toPx()))
                    }
                }
                // Draw links across the gaps so intact fleets read as connected pieces.
                fleet?.pieces?.forEach { p ->
                    val cells = Rules.cells(config, p)
                    if (cells.isEmpty()) return@forEach
                    cells.zipWithNext().forEach { (a, b) ->
                        val ac = t.origin(a) + Offset(t.cell / 2, t.cell / 2)
                        val bc = t.origin(b) + Offset(t.cell / 2, t.cell / 2)
                        val mid = (ac + bc) / 2f
                        if (p.horizontal) drawLine(colors.primaryContainer, mid - Offset(gap * 2, 0f), mid + Offset(gap * 2, 0f), t.cell * .45f)
                        else drawLine(colors.primaryContainer, mid - Offset(0f, gap * 2), mid + Offset(0f, gap * 2), t.cell * .45f)
                    }
                    val origin = t.origin(cells.first()) + Offset(gap, gap)
                    val last = t.origin(cells.last()) + Offset(t.cell - gap, t.cell - gap)
                    drawRoundRect(colors.primary, origin, Size(last.x - origin.x, last.y - origin.y), CornerRadius(t.cell * .15f), style = Stroke(1.5.dp.toPx()))
                }
                // Draw this after fleet outlines so the latest volley remains prominent.
                latestMove.forEach { index ->
                    val inset = t.cell * .07f
                    val origin = t.origin(index) + Offset(inset, inset)
                    val tile = Size(t.cell - inset * 2, t.cell - inset * 2)
                    drawRoundRect(colors.onSurface, origin, tile, CornerRadius(t.cell * .12f), style = Stroke(t.cell * .085f))
                    drawRoundRect(colors.surface, origin, tile, CornerRadius(t.cell * .12f), style = Stroke(t.cell * .035f))
                }
                drawRect(colors.surfaceContainerHigh, Offset.Zero, Size(t.margin, size.height))
                drawRect(colors.surfaceContainerHigh, Offset.Zero, Size(size.width, t.margin))
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.onSurfaceVariant.toArgb(); textSize = minOf(11.sp.toPx(), t.cell * .45f); textAlign = Paint.Align.CENTER }
                for (i in 0 until config.size) {
                    val x = t.origin(i).x + t.cell / 2
                    val y = t.origin(i * config.size).y + t.cell / 2
                    if (x >= t.margin && x < size.width) drawContext.canvas.nativeCanvas.drawText((i + 1).toString(), x, t.margin * .72f, paint)
                    if (y >= t.margin && y < size.height) drawContext.canvas.nativeCanvas.drawText(('A' + i).toString(), t.margin / 2, y + paint.textSize / 3, paint)
                }
            }
            for (index in 0 until config.size * config.size) {
                val origin = transform.origin(index)
                val left = origin.x.coerceAtLeast(margin)
                val top = origin.y.coerceAtLeast(margin)
                val right = (origin.x + transform.cell).coerceAtMost(side)
                val bottom = (origin.y + transform.cell).coerceAtMost(side)
                if (right <= left || bottom <= top) continue
                val state = when {
                    index in selected -> labelSelected
                    index in previewCells -> labelPreview
                    index in sunkCells -> labelSunk
                    index in knowledge.hits -> labelHit
                    index in knowledge.tried -> labelMiss
                    index in ownCells -> labelPiece
                    else -> labelUnknown
                }
                val label = stringResource(R.string.cell_label, coordinate(index, config.size), state)
                Box(Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .size(with(density) { (right - left).toDp() }, with(density) { (bottom - top).toDp() })
                    .semantics {
                        contentDescription = label
                        if (index in latestMove) stateDescription = latestLabel
                        if (index in selected) this.selected = true
                        if (enabled) onClick(selectLabel) { onSelect(index); true }
                    })
            }
        }
        if (showFleetStatus) FleetStatus(config, knowledge, fleet, opponent)
        if (!placement) Column {
            if (showZoomControls) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { zoom = 1f; panX = 0f; panY = 0f }, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.fit)) }
            val zoomIn = stringResource(R.string.zoom_in)
            val zoomOut = stringResource(R.string.zoom_out)
            IconButton(onClick = { setZoom(zoom + .5f) }, enabled = zoom < 3f, modifier = Modifier.size(48.dp).semantics { contentDescription = zoomIn }) { Text("+", style = MaterialTheme.typography.titleLarge) }
            IconButton(onClick = { setZoom(zoom - .5f) }, enabled = zoom > 1f, modifier = Modifier.size(48.dp).semantics { contentDescription = zoomOut }) { Text("−", style = MaterialTheme.typography.titleLarge) }
            }
            if (!showFleetStatus) Row(Modifier.heightIn(min = 24.dp).padding(horizontal = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.size(20.dp)) { drawFlame(center, size.height, 1f) }
                Text(stringResource(R.string.hits_legend), style = MaterialTheme.typography.labelMedium)
                Text("×", style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.misses_legend), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Opponent damage cannot be assigned to a particular ship until it is sunk. */
@Composable fun FleetStatus(config: GameConfig, knowledge: Knowledge, fleet: Fleet? = null, opponent: Boolean = fleet == null) {
    val colors = MaterialTheme.colorScheme
    val owner = stringResource(if (opponent) R.string.opponent_fleet_status else R.string.own_fleet_status)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        config.lengths.forEachIndexed { id, length ->
            val sunk = knowledge.sunk.any { it.id == id }
            val cells = fleet?.pieces?.find { it.id == id }?.let { Rules.cells(config, it) }.orEmpty()
            val damage = cells.map { it in knowledge.hits }
            val label = stringResource(R.string.ship_status_label, owner, id + 1, length)
            val status = stringResource(when {
                sunk -> R.string.ship_sunk
                damage.any { it } -> R.string.ship_damaged
                fleet != null -> R.string.ship_intact
                else -> R.string.ship_afloat
            })
            Surface(shape = RoundedCornerShape(8.dp), color = if (sunk) colors.surface else colors.surfaceContainerHigh,
                modifier = Modifier.semantics { contentDescription = label; stateDescription = status }) {
                Canvas(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).size((length * 9).dp, 20.dp)) {
                    val segment = size.width / length
                    repeat(length) { index ->
                        val color = if (sunk) colors.outline else if (damage.getOrElse(index) { false }) colors.error else colors.primary
                        drawRoundRect(color, Offset(index * segment + 1.dp.toPx(), size.height * .2f),
                            Size(segment - 2.dp.toPx(), size.height * .6f), CornerRadius(2.dp.toPx()))
                        if (damage.getOrElse(index) { false } && !sunk) {
                            drawLine(colors.onError, Offset((index + .25f) * segment, size.height * .35f), Offset((index + .75f) * segment, size.height * .65f), 1.5.dp.toPx())
                            drawLine(colors.onError, Offset((index + .25f) * segment, size.height * .65f), Offset((index + .75f) * segment, size.height * .35f), 1.5.dp.toPx())
                        }
                    }
                    if (sunk) {
                        drawLine(colors.onSurface, Offset(0f, size.height * .9f), Offset(size.width, size.height * .1f), 2.dp.toPx(), StrokeCap.Round)
                    }
                }
            }
        }
    }
}

/** A two-tone silhouette keeps hits recognizable even in small or monochrome cells. */
private fun DrawScope.drawFlame(center: Offset, side: Float, flicker: Float) {
    fun flame(scale: Float, color: Color) {
        val w = side * scale
        val h = side * scale * flicker
        val x = center.x
        val bottom = center.y + side * .43f
        val path = Path().apply {
            moveTo(x, bottom - h)
            cubicTo(x + w * .05f, bottom - h * .58f, x + w * .40f, bottom - h * .60f, x + w * .30f, bottom - h * .82f)
            cubicTo(x + w * .78f, bottom - h * .24f, x + w * .35f, bottom, x, bottom)
            cubicTo(x - w * .50f, bottom, x - w * .56f, bottom - h * .43f, x - w * .23f, bottom - h * .66f)
            cubicTo(x - w * .28f, bottom - h * .38f, x - w * .08f, bottom - h * .50f, x, bottom - h)
            close()
        }
        drawPath(path, color)
    }
    flame(1f, Color(0xFFB93612))
    flame(.76f, Color(0xFFFF8A24))
    flame(.40f, Color(0xFFFFE39A))
}
