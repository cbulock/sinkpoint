package com.cbulock.sinkpoint.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cbulock.sinkpoint.R
import com.cbulock.sinkpoint.UiState
import com.cbulock.sinkpoint.game.GameConfig

@OptIn(ExperimentalLayoutApi::class)
@Composable fun PersonalBestsSection(ui: UiState, mode: String, custom: Boolean?) {
    var count by rememberSaveable(mode, custom) { mutableIntStateOf(5) }
    val bests = ui.bests.values.filter { best ->
        (mode == "ALL" || best.category.mode == mode) &&
            (custom == null || (best.category.config != GameConfig()) == custom)
    }
    Heading(stringResource(R.string.personal_bests))
    Text(stringResource(R.string.personal_bests_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (bests.isEmpty()) Text(stringResource(R.string.no_bests))
    bests.take(count).forEach { best ->
        Section {
            Heading(if (best.category.mode == "NEARBY") stringResource(R.string.nearby) else stringResource(R.string.solo_difficulty, difficultyName(requireNotNull(best.category.difficulty))))
            Text(configSummary(best.category.config), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.fleet_lengths, best.category.config.lengths.joinToString(" · ")), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(if (best.category.config.touching) R.string.touch_yes else R.string.touch_no), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(stringResource(R.string.fewest_shots), best.fewestShots?.shots?.toString() ?: "—")
                Metric(stringResource(R.string.best_accuracy), best.accuracy?.let { stringResource(R.string.percent, it.hits * 100 / it.shots) } ?: "—")
                Metric(stringResource(R.string.best_streak), best.longestStreak.toString())
                Metric(stringResource(R.string.current_streak), best.currentStreak.toString())
            }
        }
    }
    if (bests.size > count) TextButton(onClick = { count += 5 }) { Text(stringResource(R.string.more_records)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun DailyStatistics(ui: UiState) {
    var count by rememberSaveable { mutableIntStateOf(5) }
    Heading(stringResource(R.string.daily_title))
    Section {
        val stats = ui.daily.stats
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric(stringResource(R.string.daily_completed), stats.completed.toString())
            Metric(stringResource(R.string.fewest_shots), stats.best?.shots?.toString() ?: "—")
            Metric(stringResource(R.string.best_accuracy), stats.best?.let { stringResource(R.string.percent, it.hits * 100 / requireNotNull(it.shots)) } ?: "—")
        }
        Text(stringResource(R.string.daily_stats_note), style = MaterialTheme.typography.bodySmall)
        ui.daily.results.take(count).forEach { result ->
            Text(dailyDate(result.date) + " · " + if (result.shots == null) stringResource(R.string.daily_forfeited) else stringResource(R.string.daily_shots, result.shots),
                style = MaterialTheme.typography.bodyMedium)
        }
        if (ui.daily.results.size > count) TextButton(onClick = { count += 10 }) { Text(stringResource(R.string.more_records)) }
    }
}
