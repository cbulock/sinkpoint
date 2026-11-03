package com.cbulock.sinkpoint.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cbulock.sinkpoint.R
import com.cbulock.sinkpoint.UiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun dailyDate(date: String): String = if (date.isEmpty()) "" else LocalDate.parse(date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

@OptIn(ExperimentalLayoutApi::class)
@Composable fun DailyEntry(ui: UiState, onStart: () -> Unit, onResume: () -> Unit) {
    val daily = ui.daily
    val hunt = daily.hunt
    val continuing = hunt?.date == daily.today && hunt.active
    Section {
        Heading(stringResource(R.string.daily_title))
        Text(dailyDate(daily.today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.daily_detail), style = MaterialTheme.typography.bodyMedium)
        daily.todayResult?.let { result -> Text(if (result.shots == null) stringResource(R.string.daily_forfeited) else stringResource(R.string.daily_recorded_score, result.shots), style = MaterialTheme.typography.bodySmall) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = if (continuing) onResume else onStart, enabled = !ui.storageBlocked && daily.today.isNotEmpty(), modifier = Modifier.testTag("daily-start")) {
                Text(stringResource(if (continuing) R.string.continue_daily else if (daily.canScoreToday) R.string.start_daily else R.string.practice))
            }
            if (hunt != null && !continuing) TextButton(onClick = onResume) {
                Text(if (hunt.active) stringResource(R.string.resume_dated_hunt, dailyDate(hunt.date)) else stringResource(R.string.view_board))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun DailyScreen(ui: UiState, onFire: (Int) -> Unit, onAbandon: () -> Unit, onPractice: () -> Unit, onHome: () -> Unit) {
    val hunt = ui.daily.hunt ?: return
    var selected by rememberSaveable(hunt.id, hunt.knowledge.moves.size) { mutableStateOf<Int?>(null) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    var detailsHeightPx by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val details: @Composable () -> Unit = {
        Column(Modifier.onSizeChanged { detailsHeightPx = it.height }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dailyDate(hunt.date), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(if (hunt.scored) R.string.daily_first_attempt else R.string.daily_practice), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            if (!hunt.active) Section {
                Heading(stringResource(if (hunt.finished) R.string.daily_finished else R.string.daily_ended))
                if (hunt.finished) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Metric(stringResource(R.string.shots), hunt.knowledge.tried.size.toString())
                        Metric(stringResource(R.string.accuracy), stringResource(R.string.percent, hunt.knowledge.hits.size * 100 / hunt.knowledge.tried.size.coerceAtLeast(1)))
                    }
                    if (hunt.scored && ui.daily.stats.best?.date == hunt.date) Text(stringResource(R.string.new_personal_best), color = MaterialTheme.colorScheme.primary)
                } else Text(stringResource(if (hunt.scored) R.string.daily_forfeited else R.string.daily_practice))
            } else {
                Text(pluralStringResource(R.plurals.daily_shot_count, hunt.knowledge.tried.size, hunt.knowledge.tried.size), style = MaterialTheme.typography.titleSmall)
                hunt.knowledge.moves.lastOrNull()?.let { move ->
                    Text(stringResource(R.string.daily_shot_result, coordinate(move.targets.single(), hunt.config.size),
                        stringResource(if (move.outcome.sunk.isNotEmpty()) R.string.daily_sunk else if (move.outcome.hits.isNotEmpty()) R.string.hits_legend else R.string.misses_legend)),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        }
    }
    val board: @Composable (Modifier) -> Unit = { modifier ->
        FleetBoard(hunt.id, hunt.config, fleet = hunt.revealedFleet, knowledge = hunt.knowledge,
                selected = selected?.let(::setOf).orEmpty(), enabled = hunt.active && !ui.storageBlocked,
                onSelect = { cell -> if (cell !in hunt.knowledge.tried) selected = cell.takeUnless { it == selected } },
                latestMove = hunt.knowledge.moves.lastOrNull()?.targets?.toSet().orEmpty(),
                showFleetStatus = true, showZoomControls = false, opponent = true, highlightRemainingShips = !hunt.active,
                modifier = modifier)
    }
    val controls: @Composable () -> Unit = {
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.widthIn(max = 680.dp).padding(horizontal = 20.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (hunt.active) {
                    Button(onClick = {
                        selected?.let { cell ->
                            if (ui.preferences.haptics) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onFire(cell)
                        }
                    }, enabled = selected != null && !ui.storageBlocked, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("daily-fire")) {
                        Text(if (selected == null) stringResource(R.string.fire) else stringResource(R.string.fire_at, coordinate(selected!!, hunt.config.size)))
                    }
                    TextButton(onClick = { confirming = true }, enabled = !ui.storageBlocked) { Text(stringResource(R.string.end_attempt)) }
                } else FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onPractice, enabled = !ui.storageBlocked) { Text(stringResource(R.string.practice)) }
                    TextButton(onClick = onHome) { Text(stringResource(R.string.main_menu)) }
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 600.dp && maxHeight < 500.dp && LocalDensity.current.fontScale <= 1.3f) {
            val boardWidth = minOf(maxHeight - 72.dp, maxWidth * .55f).coerceAtLeast(160.dp)
            Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(boardWidth)) { board(Modifier) }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(8.dp)) { details() }
                    controls()
                }
            }
        } else Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            BoxWithConstraints(Modifier.weight(1f).widthIn(max = 680.dp).fillMaxWidth()) {
                val detailsHeight = with(LocalDensity.current) { detailsHeightPx.toDp() }
                val boardWidth = minOf(maxWidth - 40.dp, maxHeight - detailsHeight - 76.dp).coerceAtLeast(160.dp)
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) { details(); board(Modifier.width(boardWidth)) }
            }
            controls()
        }
    }
    if (confirming) ConfirmDialog(R.string.end_hunt_title, if (hunt.scored) R.string.end_hunt_detail else R.string.end_practice_detail,
        R.string.end_hunt, { confirming = false }, { confirming = false; onAbandon() })
}
