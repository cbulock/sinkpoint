package com.cbulock.sinkpoint.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cbulock.sinkpoint.*
import com.cbulock.sinkpoint.R
import com.cbulock.sinkpoint.data.*
import com.cbulock.sinkpoint.game.*
import com.cbulock.sinkpoint.nearby.nearbyPermissions
import java.text.DateFormat
import java.util.Date

@Composable fun difficultyName(d: Difficulty) = stringResource(when (d) { Difficulty.EASY -> R.string.easy; Difficulty.NORMAL -> R.string.normal; Difficulty.HARD -> R.string.hard })
@Composable fun configSummary(config: GameConfig): String {
    val summary = stringResource(R.string.config_summary, config.size, config.lengths.size, stringResource(if (config.rule == TurnRule.CLASSIC) R.string.classic else R.string.salvo))
    return if (config.rule == TurnRule.CLASSIC) summary + " · " + stringResource(if (config.fireAgainOnHit) R.string.hit_repeat_summary else R.string.alternate_summary) else summary
}
fun outcomeTitle(match: MatchState): Int = if (match.winner == match.localId) {
    if (match.resigned != null) R.string.victory_resigned else R.string.victory
} else if (match.resigned != null) R.string.defeat_resigned else R.string.defeat

@OptIn(ExperimentalLayoutApi::class)
@Composable fun SinkpointApp(ui: UiState, model: GameViewModel) {
    val context = LocalContext.current
    var replaceAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var resign by remember { mutableStateOf(false) }
    var permissionAction by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val action = permissionAction; permissionAction = null
        if (permissions.values.all { it } && action != null) model.startNearby(action.first, action.second) else model.nearbyUnavailable()
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(model::export) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::previewImport) }
    fun nearby(host: Boolean, resume: Boolean) {
        permissionAction = host to resume
        permissionLauncher.launch(nearbyPermissions())
    }
    fun replaceIfNeeded(mode: Mode, action: () -> Unit) {
        val summary = if (mode == Mode.AI) ui.solo else ui.nearby
        if (summary != null && !summary.finished) replaceAction = action else action()
    }
    BackHandler(ui.screen != Screen.HOME && !ui.resultVisible && ui.pairing == null && ui.offer == null && ui.error == null && ui.importPreview == null && replaceAction == null && !resign) { model.back() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                if (ui.screen == Screen.HOME) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (LocalDensity.current.fontScale <= 1.3f) Image(painterResource(R.drawable.sinkpoint_icon_art), null, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                    }
                    TextButton(onClick = { model.navigate(Screen.SETTINGS) }) { Text(stringResource(R.string.settings)) }
                } else {
                    TextButton(onClick = model::back) { Text(stringResource(R.string.back)) }
                    if (ui.screen == Screen.GAME && LocalDensity.current.fontScale > 1.3f) Spacer(Modifier.weight(1f)) else Text(stringResource(when (ui.screen) { Screen.OPTIONS -> R.string.game_options; Screen.SETTINGS -> R.string.settings; Screen.STATISTICS -> R.string.statistics; Screen.LEARN -> R.string.learn; Screen.LOBBY -> R.string.nearby_play; else -> R.string.app_name }),
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { heading() })
                    if (ui.screen == Screen.GAME) TextButton(onClick = model::home) { Text(stringResource(R.string.main_menu)) }
                }
            }
            if (!ui.loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.loading)) }
            } else when (ui.screen) {
                Screen.HOME -> HomeScreen(
                    ui = ui,
                    onSolo = { difficulty -> replaceIfNeeded(Mode.AI) { model.startSolo(difficulty) } },
                    onMultiplayer = model::enterNearby,
                    onResumeSolo = model::resumeSolo,
                    onResumeNearby = model::resumeNearby,
                    onNavigate = model::navigate,
                )
                Screen.OPTIONS -> OptionsScreen(ui.preferences.config, model::configure)
                Screen.GAME -> GameScreen(ui, model, onResign = { resign = true }, onReconnect = { nearby(false, true) })
                Screen.SETTINGS -> Page {
                    Section {
                        Heading(stringResource(R.string.appearance))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { mode -> FilterChip(ui.preferences.theme == mode, { model.settings(ui.preferences.copy(theme = mode)) }, label = { Text(stringResource(when (mode) { ThemeMode.SYSTEM -> R.string.device_theme; ThemeMode.LIGHT -> R.string.light_theme; ThemeMode.DARK -> R.string.dark_theme })) }) }
                        }
                        Text(stringResource(R.string.dynamic_note), style = MaterialTheme.typography.bodySmall)
                    }
                    Section {
                        Heading(stringResource(R.string.controls))
                        ToggleRow(stringResource(R.string.haptics), ui.preferences.haptics) { model.settings(ui.preferences.copy(haptics = it)) }
                    }
                    Section {
                        Heading(stringResource(R.string.backup))
                        Text(stringResource(R.string.backup_note))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { exportLauncher.launch("sinkpoint-backup.json") }) { Text(stringResource(R.string.export_backup)) }
                            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text(stringResource(R.string.import_backup)) }
                        }
                    }
                    Text(stringResource(R.string.nearby_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = model::home) { Text(stringResource(R.string.done)) }
                }
                Screen.STATISTICS -> StatisticsScreen(ui)
                Screen.LEARN -> LearnScreen(model::home)
                Screen.LOBBY -> Page {
                    Heading(stringResource(R.string.nearby_play))
                    Text(stringResource(R.string.nearby_explanation))
                    Text(stringResource(R.string.nearby_permissions), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    var name by remember(ui.preferences.name) { mutableStateOf(ui.preferences.name) }
                    OutlinedTextField(name, { name = it.take(32); model.settings(ui.preferences.copy(name = name)) }, label = { Text(stringResource(R.string.display_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !ui.connected && !ui.searching)
                    if (ui.connected) {
                        Section { Text(stringResource(R.string.connected_waiting)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    } else if (ui.searching) {
                        Section {
                            Text(stringResource(if (ui.hosting) R.string.hosting else R.string.searching), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.search_hint)); LinearProgressIndicator(Modifier.fillMaxWidth())
                            ui.peers.forEach { peer -> OutlinedButton(onClick = { model.connect(peer) }, modifier = Modifier.fillMaxWidth()) { Text(peer.name + " · " + stringResource(R.string.connect)) } }
                        }
                    } else {
                        if (ui.nearby != null) Button(onClick = { nearby(false, true) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reconnect)) }
                        Section {
                            Text(configSummary(ui.preferences.config))
                            Text(stringResource(R.string.fleet_lengths, ui.preferences.config.lengths.joinToString(" · ")))
                            Button(onClick = { replaceIfNeeded(Mode.NEARBY) { nearby(true, false) } }, enabled = !ui.storageBlocked, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.host)) }
                            OutlinedButton(onClick = { replaceIfNeeded(Mode.NEARBY) { nearby(false, false) } }, enabled = !ui.storageBlocked, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.join)) }
                            TextButton(onClick = { model.navigate(Screen.OPTIONS) }) { Text(stringResource(R.string.game_options)) }
                        }
                    }
                }
            }
        }
    }
    if (ui.busy) AlertDialog(onDismissRequest = model::cancelSetup, title = { Text(stringResource(R.string.checking_fleet)) }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }, confirmButton = { TextButton(onClick = model::cancelSetup) { Text(stringResource(R.string.cancel)) } })
    replaceAction?.let { action -> ConfirmDialog(R.string.replace_title, R.string.replace_body, R.string.replace, { replaceAction = null }, { replaceAction = null; action() }) }
    if (resign) ConfirmDialog(R.string.resign_title, R.string.resign_body, R.string.resign, { resign = false }, { resign = false; model.resign() })
    ui.pairing?.let { p -> AlertDialog(onDismissRequest = { model.pairing(false) }, title = { Text(stringResource(R.string.pair_title)) }, text = { Text(stringResource(R.string.pair_detail, p.peer.name, p.code)) }, confirmButton = { Button(onClick = { model.pairing(true) }) { Text(stringResource(R.string.codes_match)) } }, dismissButton = { TextButton(onClick = { model.pairing(false) }) { Text(stringResource(R.string.cancel)) } }) }
    ui.offer?.let { offer -> AlertDialog(onDismissRequest = model::home, title = { Text(stringResource(R.string.offer_title)) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val config = requireNotNull(offer.config)
        Text(configSummary(config)); Text(stringResource(R.string.fleet_lengths, config.lengths.joinToString(" · ")))
        Text(stringResource(if (config.touching) R.string.touch_yes else R.string.touch_no))
    } }, confirmButton = { Button(onClick = model::acceptOffer) { Text(stringResource(R.string.accept_rules)) } }, dismissButton = { TextButton(onClick = model::home) { Text(stringResource(R.string.cancel)) } }) }
    ui.importPreview?.let { backup -> AlertDialog(onDismissRequest = { model.importBackup(false) }, title = { Text(stringResource(R.string.import_title)) }, text = { Text(stringResource(R.string.import_detail, backup.recentResults)) }, confirmButton = { Button(onClick = { model.importBackup(true) }) { Text(stringResource(R.string.import_confirm)) } }, dismissButton = { TextButton(onClick = { model.importBackup(false) }) { Text(stringResource(R.string.cancel)) } }) }
    if (ui.resultVisible && ui.match != null) ResultDialog(ui, model)
    ui.error?.let { error -> AlertDialog(onDismissRequest = model::dismissError, title = { Text(stringResource(R.string.error_title)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(error))
            if (error in listOf(R.string.connection_error, R.string.permissions_needed, R.string.play_services_needed)) {
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text(stringResource(R.string.app_settings)) }
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }) { Text(stringResource(R.string.wireless_settings)) }
            }
        }
    }, confirmButton = { TextButton(onClick = { if (ui.storageBlocked) model.retryStorage() else model.dismissError() }) { Text(stringResource(if (ui.storageBlocked) R.string.retry else R.string.close)) } }) }
}

@Composable fun Page(width: Int = 640, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = width.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}
@Composable fun Heading(text: String) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() }) }
@Composable fun Section(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable fun DifficultyRow(difficulty: Difficulty, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).clickable(enabled, onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (LocalDensity.current.fontScale <= 1.3f) Surface(shape = RoundedCornerShape(14.dp), color = when (difficulty) { Difficulty.EASY -> MaterialTheme.colorScheme.secondaryContainer; Difficulty.NORMAL -> MaterialTheme.colorScheme.primaryContainer; Difficulty.HARD -> MaterialTheme.colorScheme.tertiaryContainer }) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Text("${difficulty.ordinal + 1}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(difficultyName(difficulty), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(when (difficulty) { Difficulty.EASY -> R.string.easy_detail; Difficulty.NORMAL -> R.string.normal_detail; Difficulty.HARD -> R.string.hard_detail }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.titleLarge)
    }
}
@Composable fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value, role = Role.Switch, onValueChange = onChange).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(value, null)
    }
}
@Composable fun ConfirmDialog(title: Int, body: Int, action: Int, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(title)) }, text = { Text(stringResource(body)) }, confirmButton = { Button(onClick = confirm) { Text(stringResource(action)) } }, dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun OptionsScreen(config: GameConfig, onChange: (GameConfig) -> Unit) {
    Page {
        Text(stringResource(R.string.config_note))
        Section {
            Heading(stringResource(R.string.board_size, config.size))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (6..12).forEach { size -> FilterChip(config.size == size, { onChange(config.copy(size = size)) }, label = { Text("$size") }) } }
        }
        Section {
            Heading(stringResource(R.string.fleet))
            config.lengths.forEachIndexed { i, length ->
                Column {
                    Text(stringResource(R.string.piece_number, i + 1, length))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (2..6).forEach { n -> FilterChip(length == n, { onChange(config.copy(lengths = config.lengths.toMutableList().apply { this[i] = n })) }, label = { Text("$n") }) }
                        TextButton(onClick = { onChange(config.copy(lengths = config.lengths.filterIndexed { j, _ -> j != i })) }, enabled = config.lengths.size > 1) { Text(stringResource(R.string.remove)) }
                    }
                }
            }
            OutlinedButton(onClick = { onChange(config.copy(lengths = config.lengths + 2)) }, enabled = config.lengths.size < 10) { Text(stringResource(R.string.add_piece)) }
            ToggleRow(stringResource(R.string.touching), config.touching) { onChange(config.copy(touching = it)) }
            Text(stringResource(R.string.touching_detail), style = MaterialTheme.typography.bodySmall)
        }
        Section {
            Heading(stringResource(R.string.turn_rules))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TurnRule.entries.forEach { rule -> FilterChip(config.rule == rule, { onChange(config.copy(rule = rule)) }, label = { Text(stringResource(if (rule == TurnRule.CLASSIC) R.string.classic else R.string.salvo)) }) } }
            Text(stringResource(if (config.rule == TurnRule.CLASSIC) R.string.classic_detail else R.string.salvo_detail))
            if (config.rule == TurnRule.CLASSIC) {
                ToggleRow(stringResource(R.string.fire_again_on_hit), config.fireAgainOnHit) { onChange(config.copy(fireAgainOnHit = it)) }
                Text(stringResource(R.string.fire_again_on_hit_detail), style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(onClick = { onChange(GameConfig()) }) { Text(stringResource(R.string.restore_default)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ResultDialog(ui: UiState, model: GameViewModel) {
    val match = ui.match ?: return
    val won = match.winner == match.localId
    AlertDialog(onDismissRequest = { model.showResult(false) }, modifier = Modifier.widthIn(max = 440.dp), shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(outcomeTitle(match)), fontWeight = FontWeight.ExtraBold) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (won) VictoryCelebration(match.id)
            TextButton(onClick = { model.showResult(false) }) { Text(stringResource(R.string.view_board)) }
            Surface(color = if (won) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(20.dp)) {
                Text(stringResource(if (match.resigned != null) R.string.resigned_result else if (won) R.string.victory_detail else R.string.defeat_detail), Modifier.padding(16.dp))
            }
            Text(configSummary(match.config)); Text(if (ui.mode == Mode.AI) difficultyName(ui.difficulty) else stringResource(R.string.nearby))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(stringResource(R.string.shots), match.outgoing.tried.size.toString())
                Metric(stringResource(R.string.hits), match.outgoing.hits.size.toString())
                Metric(stringResource(R.string.accuracy), stringResource(R.string.percent, 100 * match.outgoing.hits.size / match.outgoing.tried.size.coerceAtLeast(1)))
            }
            if (ui.mode == Mode.AI) OutlinedButton(onClick = { model.showResult(false); model.startSolo(ui.difficulty, match.config) }) { Text(stringResource(R.string.play_again)) }
            else {
                if (ui.rematchRequested) Text(stringResource(R.string.rematch_offer))
                if (ui.rematchSent) Text(stringResource(R.string.rematch_wait))
                OutlinedButton(onClick = { model.rematch(); model.showResult(false) }, enabled = ui.connected && !ui.rematchSent) { Text(stringResource(R.string.play_again)) }
            }
        } }, confirmButton = { Button(onClick = model::home) { Text(stringResource(R.string.main_menu)) } })
}

@Composable fun Metric(label: String, value: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(16.dp)) { Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.bodySmall) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun StatisticsScreen(ui: UiState) {
    var mode by rememberSaveable { mutableStateOf("ALL") }
    var custom by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var limit by rememberSaveable { mutableIntStateOf(10) }
    Page {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ALL" to R.string.all, "AI" to R.string.ai, "NEARBY" to R.string.nearby).forEach { (id, label) -> FilterChip(mode == id, { mode = id; limit = 10 }, label = { Text(stringResource(label)) }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null to R.string.all, false to R.string.standard_rules, true to R.string.custom_rules).forEach { (id, label) -> FilterChip(custom == id, { custom = id; limit = 10 }, label = { Text(stringResource(label)) }) }
        }
        val totals = ui.totals.filterKeys { key -> (mode == "ALL" || key.startsWith("$mode:")) && (custom == null || key.endsWith(if (custom == true) "CUSTOM" else "CLASSIC")) }.values
        val count = totals.sumOf { it.played }; val wins = totals.sumOf { it.wins }
        Section {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(stringResource(R.string.played), count.toString()); Metric(stringResource(R.string.wins), wins.toString())
                Metric(stringResource(R.string.win_rate), stringResource(R.string.percent, wins * 100 / count.coerceAtLeast(1)))
                Metric(stringResource(R.string.accuracy), stringResource(R.string.percent, totals.sumOf { it.hits } * 100 / totals.sumOf { it.shots }.coerceAtLeast(1)))
            }
        }
        if (mode != "NEARBY") Difficulty.entries.forEach { d ->
            val records = ui.totals.filterKeys { it.startsWith("AI:${d.name}:") && (custom == null || it.endsWith(if (custom == true) "CUSTOM" else "CLASSIC")) }.values
            Section { Heading(difficultyName(d)); Text("${stringResource(R.string.wins)}: ${records.sumOf { it.wins }}  ·  ${stringResource(R.string.played)}: ${records.sumOf { it.played }}") }
        }
        Heading(stringResource(R.string.recent_results))
        val records = ui.records.filter { (mode == "ALL" || it.mode == mode) && (custom == null || it.custom == custom) }
        if (records.isEmpty()) Section { Heading(stringResource(R.string.no_results)); Text(stringResource(R.string.no_results_detail)) }
        records.take(limit).forEach { r -> Section {
            Heading(stringResource(if (r.won) R.string.won else R.string.lost))
            Text((r.difficulty?.let { difficultyName(it) } ?: stringResource(R.string.nearby)) + " · " + stringResource(if (r.custom) R.string.custom_rules else R.string.standard_rules))
            Text(DateFormat.getDateInstance().format(Date(r.date)), style = MaterialTheme.typography.bodySmall)
            Text("${r.shots} ${stringResource(R.string.shots)} · ${stringResource(R.string.percent, r.hits * 100 / r.shots.coerceAtLeast(1))} ${stringResource(R.string.accuracy)}")
        } }
        if (records.size > limit) TextButton(onClick = { limit += 10 }) { Text(stringResource(R.string.show_more)) }
    }
}

@Composable fun LearnScreen(done: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var fired by rememberSaveable { mutableStateOf(false) }
    Page(560) {
        LinearProgressIndicator(progress = { (step + 1) / 4f }, modifier = Modifier.fillMaxWidth())
        Heading(stringResource(listOf(R.string.tutorial_title_1, R.string.tutorial_title_2, R.string.tutorial_title_3, R.string.tutorial_title_4)[step]))
        Text(stringResource(listOf(R.string.tutorial_body_1, R.string.tutorial_body_2, R.string.tutorial_body_3, R.string.tutorial_body_4)[step]))
        if (step == 1) {
            val config = GameConfig(size = 6, lengths = listOf(2))
            val knowledge = Knowledge(config, if (fired) listOf(Move("tutorial", 0, "you", listOf(8), Outcome(listOf(8), emptyList()))) else emptyList())
            FleetBoard("tutorial", config, knowledge = knowledge, selected = if (!fired) setOfNotNull(selected) else emptySet(), enabled = !fired, onSelect = { selected = it })
            if (selected == 8 && !fired) Text(stringResource(R.string.tutorial_success))
            if (fired) Text(stringResource(R.string.tutorial_hit))
            Button(onClick = { fired = true }, enabled = selected == 8 && !fired) { Text(stringResource(R.string.fire)) }
        }
        Button(onClick = { if (step < 3) step++ else done() }) { Text(stringResource(if (step < 3) R.string.next else R.string.start_playing)) }
    }
}

