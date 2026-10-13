package com.cbulock.sinkpoint.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cbulock.sinkpoint.R
import com.cbulock.sinkpoint.ResumeSummary
import com.cbulock.sinkpoint.Screen
import com.cbulock.sinkpoint.UiState
import com.cbulock.sinkpoint.game.Difficulty

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    ui: UiState,
    onSolo: (Difficulty) -> Unit,
    onMultiplayer: () -> Unit,
    onResumeSolo: () -> Unit,
    onResumeNearby: () -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    var choosingDifficulty by rememberSaveable { mutableStateOf(false) }
    Page(760) {
        Heading(stringResource(R.string.new_game))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val sideBySide = maxWidth >= 580.dp && LocalDensity.current.fontScale <= 1.3f
            val solo: @Composable (Modifier) -> Unit = { modifier ->
                PlayModeCard(false, !ui.storageBlocked, modifier) { choosingDifficulty = true }
            }
            val multiplayer: @Composable (Modifier) -> Unit = { modifier ->
                PlayModeCard(true, true, modifier, onMultiplayer)
            }
            if (sideBySide) Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                solo(Modifier.weight(1f).fillMaxHeight()); multiplayer(Modifier.weight(1f).fillMaxHeight())
            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                solo(Modifier.fillMaxWidth()); multiplayer(Modifier.fillMaxWidth())
            }
        }
        if (ui.solo?.finished == false || ui.nearby?.finished == false) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Heading(stringResource(R.string.continue_playing))
                ui.solo?.takeUnless { it.finished }?.let {
                    SavedGameRow(it, false, ui.difficulty, onResumeSolo)
                }
                ui.nearby?.takeUnless { it.finished }?.let {
                    SavedGameRow(it, true, ui.difficulty, onResumeNearby)
                }
            }
        }
        Surface(
            onClick = { onNavigate(Screen.OPTIONS) },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.game_options), style = MaterialTheme.typography.titleSmall)
                    Text(configSummary(ui.preferences.config), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MenuArrow(MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { onNavigate(Screen.LEARN) }) { Text(stringResource(R.string.learn)) }
            TextButton(onClick = { onNavigate(Screen.STATISTICS) }) { Text(stringResource(R.string.statistics)) }
        }
        if (ui.solo?.finished == true || ui.nearby?.finished == true) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Heading(stringResource(R.string.recent_games))
                ui.solo?.takeIf { it.finished }?.let { SavedGameRow(it, false, ui.difficulty, onResumeSolo) }
                ui.nearby?.takeIf { it.finished }?.let { SavedGameRow(it, true, ui.difficulty, onResumeNearby) }
            }
        }
    }
    if (choosingDifficulty) AlertDialog(
        onDismissRequest = { choosingDifficulty = false },
        title = { Text(stringResource(R.string.choose_difficulty)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Difficulty.entries.forEachIndexed { index, difficulty ->
                    DifficultyRow(difficulty, !ui.storageBlocked) {
                        choosingDifficulty = false
                        onSolo(difficulty)
                    }
                    if (index < Difficulty.entries.lastIndex) HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = { choosingDifficulty = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PlayModeCard(multiplayer: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val foreground = if (multiplayer) colors.onSecondaryContainer else colors.onPrimaryContainer
    Card(
        onClick = onClick, enabled = enabled,
        modifier = modifier.testTag(if (multiplayer) "new-multiplayer" else "new-solo"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (multiplayer) colors.secondaryContainer else colors.primaryContainer,
            contentColor = foreground,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (LocalDensity.current.fontScale <= 1.3f) ModeGlyph(multiplayer, foreground)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(if (multiplayer) R.string.multiplayer else R.string.solo_play), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(if (multiplayer) R.string.multiplayer_detail else R.string.solo_detail), style = MaterialTheme.typography.bodyMedium)
            }
            MenuArrow(foreground)
        }
    }
}

@Composable
private fun SavedGameRow(summary: ResumeSummary, multiplayer: Boolean, difficulty: Difficulty, action: () -> Unit) {
    val label = stringResource(when {
        summary.finished && multiplayer -> R.string.finished_multiplayer
        summary.finished -> R.string.finished_solo
        multiplayer -> R.string.continue_multiplayer
        else -> R.string.continue_solo
    })
    Surface(
        onClick = action,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (multiplayer) stringResource(R.string.multiplayer) else stringResource(R.string.solo_difficulty, difficultyName(difficulty)),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when {
                            summary.finished -> stringResource(R.string.completed_game, summary.size)
                            summary.placement -> stringResource(R.string.saved_placement, summary.size)
                            else -> pluralStringResource(R.plurals.saved_battle, summary.shots, summary.size, summary.shots)
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MenuArrow(MaterialTheme.colorScheme.primary)
            }
            Text(stringResource(if (summary.finished) R.string.view_board_action else R.string.continue_action), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun MenuArrow(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        drawLine(color, Offset(size.width * .35f, size.height * .2f), Offset(size.width * .65f, size.height * .5f), 2.dp.toPx(), StrokeCap.Round)
        drawLine(color, Offset(size.width * .65f, size.height * .5f), Offset(size.width * .35f, size.height * .8f), 2.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun ModeGlyph(multiplayer: Boolean, color: Color) {
    Canvas(Modifier.size(40.dp)) {
        val unit = size.width / 40f
        if (multiplayer) {
            listOf(12f, 29f).forEach { x ->
                drawCircle(color, 5f * unit, Offset(x * unit, 11f * unit))
                drawRoundRect(color, Offset((x - 7f) * unit, 20f * unit), Size(14f * unit, 13f * unit), CornerRadius(5f * unit))
            }
        } else {
            for (row in 0..2) for (col in 0..2) {
                val occupied = row == 1 || row == 2 && col == 2
                drawRoundRect(color.copy(alpha = if (occupied) 1f else .18f), Offset(col * 14f * unit, row * 14f * unit), Size(10f * unit, 10f * unit), CornerRadius(3f * unit))
            }
        }
    }
}
