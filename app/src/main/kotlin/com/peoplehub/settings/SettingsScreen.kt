package com.peoplehub.settings

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.R
import com.peoplehub.core.domain.model.AppSettings
import com.peoplehub.core.domain.model.ImportStrategy
import com.peoplehub.core.domain.model.ReminderOffset
import com.peoplehub.core.ui.components.BarSegment
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.GroupedRows
import com.peoplehub.core.ui.components.Hairline
import com.peoplehub.core.ui.components.LocalNavBarClearance
import com.peoplehub.core.ui.components.SceneArt
import com.peoplehub.core.ui.components.ScreenHero
import com.peoplehub.core.ui.components.SegmentBar
import com.peoplehub.core.ui.components.SettingsCard
import com.peoplehub.core.ui.components.SettingsRow
import com.peoplehub.core.ui.components.StatusPill
import com.peoplehub.core.ui.components.StepperRow
import com.peoplehub.core.ui.components.SwitchRow
import com.peoplehub.core.ui.components.TextAction
import com.peoplehub.core.ui.components.ToggleChip
import com.peoplehub.core.ui.components.VellutoBackground
import com.peoplehub.core.ui.components.VellutoButton
import com.peoplehub.core.ui.components.VellutoButtonStyle
import com.peoplehub.core.ui.components.WithTooltip
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.lightEdge
import com.peoplehub.core.ui.modifier.riseIn
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes
import com.peoplehub.io.readTextRobust
import com.peoplehub.locale.AppLocale
import com.peoplehub.update.AvailableUpdate
import com.peoplehub.update.UpdateUiState
import com.peoplehub.update.UpdateViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.peoplehub.core.ui.R as UiR

/** Settings ("Vault"): backup & restore, frequency thresholds, and reminder preferences. */
@Composable
fun SettingsScreen(
    onOpenImportGuide: () -> Unit,
    onOpenBirthdayOnly: () -> Unit,
    onOpenLanguage: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf(readNotificationDiagnostics(context)) }
    val languageTag = remember { AppLocale.currentTag(context) }
    val checkNowMessage = stringResource(R.string.vault_diag_check_now_sent)

    // Re-read on every resume so returning from a system settings screen reflects the new grant.
    LifecycleResumeEffect(Unit) {
        diagnostics = readNotificationDiagnostics(context)
        viewModel.refreshDiagnostics()
        onPauseOrDispose { }
    }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onMessageShown()
        }
    }

    val exportJsonLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val content = viewModel.buildBackupJson()
                    context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                }
            }
        }

    val exportCsvLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("text/csv"),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val content = viewModel.buildBirthdaysCsv()
                    context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                }
            }
        }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val json = readTextRobust(context, uri)
                if (json != null) pendingImportJson = json
            }
        }

    SettingsContent(
        state = state,
        currentVersion = updateViewModel.currentVersion,
        updateState = updateState,
        diagnostics = diagnostics,
        languageTag = languageTag,
        snackbarHostState = snackbarHostState,
        actions =
            SettingsActions(
                onCheckUpdates = { updateViewModel.check(silent = false) },
                onInstallUpdate = { update -> updateViewModel.downloadAndInstall(update) },
                onOpenLanguage = onOpenLanguage,
                onOpenImportGuide = onOpenImportGuide,
                onOpenBirthdayOnly = onOpenBirthdayOnly,
                onExportAll = { exportJsonLauncher.launch("peoplehub-backup.json") },
                onImportAll = { importLauncher.launch(arrayOf("application/json")) },
                onExportBirthdays = { exportCsvLauncher.launch("peoplehub-birthdays.csv") },
                onShareBirthdays = {
                    scope.launch {
                        val csv = viewModel.buildBirthdaysCsv()
                        val send =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_TEXT, csv)
                            }
                        context.startActivity(Intent.createChooser(send, null))
                    }
                },
                onThresholdChange = viewModel::onThresholdChange,
                onToggleReminder = viewModel::onToggleReminder,
                onToggleExactAlarms = viewModel::onToggleExactAlarms,
                onReminderHourChange = viewModel::onReminderHourChange,
                onFixNotifications = { openNotificationSettings(context) },
                onFixExactAlarms = { openExactAlarmSettings(context) },
                onFixBattery = { openBatterySettings(context) },
                onCheckNow = {
                    viewModel.runNotificationCheckNow()
                    scope.launch { snackbarHostState.showSnackbar(checkNowMessage) }
                },
                onDeleteAll = { showDeleteAllDialog = true },
            ),
    )

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringResource(R.string.vault_delete_all_confirm_title)) },
            text = { Text(stringResource(R.string.vault_delete_all_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAllDialog = false
                    viewModel.deleteEveryone()
                }) { Text(stringResource(R.string.vault_delete_all)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    val json = pendingImportJson
    if (json != null) {
        ImportStrategyDialog(
            onReplace = {
                viewModel.importFullBackup(json, ImportStrategy.REPLACE)
                pendingImportJson = null
            },
            onMerge = {
                viewModel.importFullBackup(json, ImportStrategy.MERGE)
                pendingImportJson = null
            },
            onDismiss = { pendingImportJson = null },
        )
    }
}

/** Every user action the Vault exposes, grouped so [SettingsContent] stays stateless and previewable. */
@Immutable
internal class SettingsActions(
    val onCheckUpdates: () -> Unit = {},
    val onInstallUpdate: (AvailableUpdate) -> Unit = {},
    val onOpenLanguage: () -> Unit = {},
    val onOpenImportGuide: () -> Unit = {},
    val onOpenBirthdayOnly: () -> Unit = {},
    val onExportAll: () -> Unit = {},
    val onImportAll: () -> Unit = {},
    val onExportBirthdays: () -> Unit = {},
    val onShareBirthdays: () -> Unit = {},
    val onThresholdChange: (Int, Int) -> Unit = { _, _ -> },
    val onToggleReminder: (ReminderOffset, Boolean) -> Unit = { _, _ -> },
    val onToggleExactAlarms: (Boolean) -> Unit = {},
    val onReminderHourChange: (Int) -> Unit = {},
    val onFixNotifications: () -> Unit = {},
    val onFixExactAlarms: () -> Unit = {},
    val onFixBattery: () -> Unit = {},
    val onCheckNow: () -> Unit = {},
    val onDeleteAll: () -> Unit = {},
)

/**
 * The stateless Vault ("La Cassaforte"): hero with the floating lock, then one glass card per
 * section, the grouped navigation rows and the danger zone. All state comes in, all actions go out
 * through [actions].
 */
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    currentVersion: String,
    updateState: UpdateUiState,
    diagnostics: NotificationDiagnostics,
    languageTag: String,
    snackbarHostState: SnackbarHostState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    val navClearance = LocalNavBarClearance.current
    val cardModifier = Modifier.padding(horizontal = spacing.gutter)
    VellutoBackground(modifier = modifier, secondGlow = true) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = navClearance)) },
        ) { innerPadding ->
            Column(
                modifier =
                    Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = navClearance + 60.dp),
                verticalArrangement = Arrangement.spacedBy(spacing.ml),
            ) {
                VaultHero()
                Spacer(Modifier.height(spacing.s))

                if (state.isBusy) {
                    LinearProgressIndicator(
                        modifier = cardModifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = c.track,
                    )
                }

                BackupCard(
                    onExportAll = actions.onExportAll,
                    onImportAll = actions.onImportAll,
                    onExportBirthdays = actions.onExportBirthdays,
                    onShareBirthdays = actions.onShareBirthdays,
                    onOpenImportGuide = actions.onOpenImportGuide,
                    modifier = cardModifier.riseIn(1),
                )

                ThresholdCard(
                    warningDays = state.settings.defaultCheckInThreshold.warningDays,
                    criticalDays = state.settings.defaultCheckInThreshold.criticalDays,
                    onChange = actions.onThresholdChange,
                    modifier = cardModifier.riseIn(2),
                )

                BirthdayRemindersCard(
                    enabledOffsets = state.settings.birthdayReminderOffsets,
                    useExactAlarms = state.settings.useExactAlarms,
                    reminderHour = state.settings.dailyReminderHour,
                    onToggle = actions.onToggleReminder,
                    onToggleExact = actions.onToggleExactAlarms,
                    onHourChange = actions.onReminderHourChange,
                    modifier = cardModifier.riseIn(3),
                )

                DiagnosticsCard(
                    diagnostics = diagnostics,
                    lastBirthdaySweep = state.lastBirthdaySweep,
                    lastRelationshipSweep = state.lastRelationshipSweep,
                    onFixNotifications = actions.onFixNotifications,
                    onFixExactAlarms = actions.onFixExactAlarms,
                    onFixBattery = actions.onFixBattery,
                    onCheckNow = actions.onCheckNow,
                    modifier = cardModifier.riseIn(4),
                )

                UpdatesCard(
                    currentVersion = currentVersion,
                    updateState = updateState,
                    onCheck = actions.onCheckUpdates,
                    onInstall = actions.onInstallUpdate,
                    modifier = cardModifier.riseIn(5),
                )

                PreferencesCard(
                    languageTag = languageTag,
                    onOpenLanguage = actions.onOpenLanguage,
                    onOpenBirthdayOnly = actions.onOpenBirthdayOnly,
                    modifier = cardModifier.riseIn(6),
                )

                Spacer(Modifier.height(spacing.s))

                DangerCard(onDeleteAll = actions.onDeleteAll, modifier = cardModifier)
            }
        }
    }
}

/** "La / *Cassaforte*" over the floating lock: the title breaks before its last word, as in the mockup. */
@Composable
private fun VaultHero() {
    val title = stringResource(R.string.vault_title)
    val split = title.trimEnd().lastIndexOf(' ')
    val accent = if (split > 0) title.substring(split + 1).trimEnd() else null
    val display = if (split > 0) title.substring(0, split) + "\n" + accent else title
    ScreenHero(
        title = display,
        titleAccent = accent,
        eyebrow = stringResource(R.string.tab_vault),
        scene = SceneArt.Lock,
        sceneOffsetX = 32.dp,
        sceneOffsetY = 30.dp,
    )
}

@Composable
private fun BackupCard(
    onExportAll: () -> Unit,
    onImportAll: () -> Unit,
    onExportBirthdays: () -> Unit,
    onShareBirthdays: () -> Unit,
    onOpenImportGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(
        title = stringResource(R.string.vault_backup_title),
        icon = VellutoIcons.Archive,
        description = stringResource(R.string.vault_backup_desc),
        modifier = modifier,
    ) {
        VellutoButton(
            text = stringResource(R.string.vault_export_all),
            onClick = onExportAll,
            icon = VellutoIcons.Upload,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PeopleHubTheme.spacing.sm)) {
            VellutoButton(
                text = stringResource(R.string.vault_import_all),
                onClick = onImportAll,
                style = VellutoButtonStyle.Neutral,
                icon = VellutoIcons.Download,
                height = 52.dp,
                modifier = Modifier.weight(1f),
            )
            VellutoButton(
                text = stringResource(R.string.vault_export_birthdays),
                onClick = onExportBirthdays,
                style = VellutoButtonStyle.Neutral,
                icon = VellutoIcons.Gift,
                height = 52.dp,
                modifier = Modifier.weight(1f),
            )
        }
        VellutoButton(
            text = stringResource(R.string.vault_share_birthdays),
            onClick = onShareBirthdays,
            style = VellutoButtonStyle.Ghost,
            icon = VellutoIcons.Share,
            modifier = Modifier.fillMaxWidth(),
        )
        TextAction(
            label = stringResource(R.string.vault_open_guide),
            onClick = onOpenImportGuide,
            trailing = { Icon(VellutoIcons.ArrowUpRight, contentDescription = null, modifier = Modifier.size(16.dp)) },
        )
    }
}

@Composable
private fun ThresholdCard(
    warningDays: Int,
    criticalDays: Int,
    onChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val warningLabel = stringResource(R.string.vault_threshold_warning)
    val criticalLabel = stringResource(R.string.vault_threshold_critical)
    val decrease = stringResource(R.string.cd_stepper_decrease)
    val increase = stringResource(R.string.cd_stepper_increase)
    SettingsCard(
        title = stringResource(R.string.vault_threshold_title),
        icon = VellutoIcons.Clock,
        description = stringResource(R.string.vault_threshold_desc),
        modifier = modifier,
    ) {
        ThresholdScale(
            warningDays = warningDays,
            criticalDays = criticalDays,
            description = "$warningLabel $warningDays, $criticalLabel $criticalDays",
        )
        StepperRow(
            label = warningLabel,
            value = warningDays.toString(),
            onDecrement = { onChange((warningDays - 1).coerceAtLeast(1), criticalDays) },
            onIncrement = { onChange(warningDays + 1, criticalDays) },
            decrementLabel = decrease,
            incrementLabel = increase,
            minusIcon = VellutoIcons.Minus,
            plusIcon = VellutoIcons.Plus,
            dotColor = c.warn,
        )
        StepperRow(
            label = criticalLabel,
            value = criticalDays.toString(),
            onDecrement = { onChange(warningDays, (criticalDays - 1).coerceAtLeast(warningDays + 1)) },
            onIncrement = { onChange(warningDays, criticalDays + 1) },
            decrementLabel = decrease,
            incrementLabel = increase,
            minusIcon = VellutoIcons.Minus,
            plusIcon = VellutoIcons.Plus,
            dotColor = c.err,
        )
    }
}

/** One label above the threshold bar, centred on [fraction] of the bar's width. */
private data class ScaleMark(
    val text: String,
    val fraction: Float,
    val color: Color,
    val emphasised: Boolean,
)

/**
 * The ok / warn / err bar sized on the real thresholds ("today ... 14 ... 30"): the warning and
 * critical numbers sit above the boundaries they mark. The tail past the critical threshold is half
 * of it, as in the mockup (14 / 16 / 15 for 14 and 30 days).
 */
@Composable
private fun ThresholdScale(
    warningDays: Int,
    criticalDays: Int,
    description: String,
) {
    val c = PeopleHubTheme.colors
    val okWeight = warningDays.coerceAtLeast(1)
    val warnWeight = (criticalDays - warningDays).coerceAtLeast(1)
    val errWeight = (criticalDays / 2).coerceAtLeast(1)
    val total = (okWeight + warnWeight + errWeight).toFloat()
    val marks =
        listOf(
            ScaleMark(stringResource(UiR.string.ui_today), 0f, c.text2, emphasised = false),
            ScaleMark(warningDays.toString(), okWeight / total, c.warn, emphasised = true),
            ScaleMark(criticalDays.toString(), (okWeight + warnWeight) / total, c.err, emphasised = true),
        )
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = PeopleHubTheme.spacing.xs)
                .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(PeopleHubTheme.spacing.xs),
    ) {
        ScaleLabels(marks)
        SegmentBar(
            segments =
                listOf(
                    BarSegment(okWeight.toFloat(), c.ok),
                    BarSegment(warnWeight.toFloat(), c.warn),
                    BarSegment(errWeight.toFloat(), c.err),
                ),
            description = description,
        )
    }
}

/** Places each [ScaleMark] centred on its fraction, kept inside the row and never overlapping the previous one. */
@Composable
private fun ScaleLabels(marks: List<ScaleMark>) {
    val markGap = PeopleHubTheme.spacing.xxs
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            marks.forEach { mark ->
                Text(
                    text = mark.text,
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (mark.emphasised) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    color = mark.color,
                    maxLines = 1,
                )
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = constraints.maxWidth
        val height = placeables.maxOfOrNull { it.height } ?: 0
        val gap = markGap.roundToPx()
        layout(width, height) {
            var previousEnd = 0
            placeables.forEachIndexed { index, placeable ->
                val centre = (marks[index].fraction * width).toInt()
                val wanted = centre - placeable.width / 2
                val minX = if (index == 0) 0 else previousEnd + gap
                val x = wanted.coerceAtLeast(minX).coerceAtMost((width - placeable.width).coerceAtLeast(0))
                placeable.placeRelative(x, 0)
                previousEnd = x + placeable.width
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BirthdayRemindersCard(
    enabledOffsets: Set<ReminderOffset>,
    useExactAlarms: Boolean,
    reminderHour: Int,
    onToggle: (ReminderOffset, Boolean) -> Unit,
    onToggleExact: (Boolean) -> Unit,
    onHourChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = PeopleHubTheme.spacing
    SettingsCard(
        title = stringResource(R.string.vault_reminders_title),
        icon = VellutoIcons.Gift,
        modifier = modifier,
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
            verticalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            ReminderOffset.entries.forEach { offset ->
                ToggleChip(
                    label = reminderLabel(offset),
                    checked = offset in enabledOffsets,
                    onCheckedChange = { checked -> onToggle(offset, checked) },
                    checkIcon = VellutoIcons.Check,
                )
            }
        }
        Hairline()
        CapsLabel(text = stringResource(R.string.vault_alarms_title))
        HourRow(
            label = stringResource(R.string.vault_reminder_hour),
            hour = reminderHour,
            onDecrement = { onHourChange((reminderHour - 1).coerceAtLeast(0)) },
            onIncrement = { onHourChange((reminderHour + 1).coerceAtMost(23)) },
        )
        SwitchRow(
            label = stringResource(R.string.vault_exact_alarms),
            checked = useExactAlarms,
            onCheckedChange = onToggleExact,
        )
    }
}

/**
 * "Ora del promemoria giornaliero  −  05:00  +": the hour shown as a neutral pill in the locale's
 * short time format, between the same hairline minus and soft-accent plus buttons as [StepperRow].
 */
@Composable
private fun HourRow(
    label: String,
    hour: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    val time = remember(hour) { LocalTime.of(hour.coerceIn(0, 23), 0).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = spacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = c.text,
            modifier = Modifier.weight(1f).padding(end = spacing.xs),
        )
        RoundStepButton(
            icon = VellutoIcons.Minus,
            description = stringResource(R.string.cd_stepper_decrease),
            onClick = onDecrement,
            container = Color.Transparent,
            content = c.text,
            edge = c.line,
        )
        Box(
            modifier =
                Modifier
                    .heightIn(min = 44.dp)
                    .background(c.surface2, VellutoShapes.Pill)
                    .lightEdge(VellutoShapes.Pill, Color.Transparent, c.line)
                    .padding(horizontal = spacing.l),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = time,
                style = PeopleHubTheme.type.kpiTile.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                color = c.text,
                maxLines = 1,
            )
        }
        RoundStepButton(
            icon = VellutoIcons.Plus,
            description = stringResource(R.string.cd_stepper_increase),
            onClick = onIncrement,
            container = c.accentSoft,
            content = c.accentOnSoft,
            edge = c.accentEdge,
        )
    }
}

/** A 48dp round step button with tooltip, styled like the ones inside [StepperRow]. */
@Composable
private fun RoundStepButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    edge: Color,
) {
    WithTooltip(description = description) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(PeopleHubTheme.spacing.touchTarget).lightEdge(CircleShape, Color.Transparent, edge),
            colors = IconButtonDefaults.iconButtonColors(containerColor = container, contentColor = content),
        ) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Surfaces the system switches that can silently mute the reminders, each with a one-tap route to
 * the screen that fixes it, plus a manual sweep so the whole chain can be verified on the spot.
 */
@Composable
private fun DiagnosticsCard(
    diagnostics: NotificationDiagnostics,
    lastBirthdaySweep: LocalDate?,
    lastRelationshipSweep: LocalDate?,
    onFixNotifications: () -> Unit,
    onFixExactAlarms: () -> Unit,
    onFixBattery: () -> Unit,
    onCheckNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    SettingsCard(
        title = stringResource(R.string.vault_diag_title),
        icon = VellutoIcons.Bell,
        description = stringResource(R.string.vault_diag_desc),
        modifier = modifier,
    ) {
        GroupedRows {
            DiagnosticRow(
                label = stringResource(R.string.vault_diag_notifications),
                ok = diagnostics.notificationsAllowed,
                onFix = onFixNotifications,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Hairline()
                DiagnosticRow(
                    label = stringResource(R.string.vault_diag_exact_alarms),
                    ok = diagnostics.exactAlarmsAllowed,
                    onFix = onFixExactAlarms,
                )
            }
            Hairline()
            DiagnosticRow(
                label = stringResource(R.string.vault_diag_battery),
                ok = diagnostics.batteryUnrestricted,
                onFix = onFixBattery,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(PeopleHubTheme.spacing.xxs)) {
            Text(
                text =
                    lastBirthdaySweep
                        ?.let { stringResource(R.string.vault_diag_last_run, it.toString()) }
                        ?: stringResource(R.string.vault_diag_last_run_never),
                style = MaterialTheme.typography.bodySmall,
                color = c.text2,
            )
            Text(
                text =
                    lastRelationshipSweep
                        ?.let { stringResource(R.string.vault_diag_last_reminder_run, it.toString()) }
                        ?: stringResource(R.string.vault_diag_last_reminder_run_never),
                style = MaterialTheme.typography.bodySmall,
                color = c.text2,
            )
        }
        VellutoButton(
            text = stringResource(R.string.vault_diag_check_now),
            onClick = onCheckNow,
            style = VellutoButtonStyle.Outline,
            icon = VellutoIcons.RefreshCw,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One health check: status disc, label, then "OK" in green or the warn "Risolvi ↗" pill. */
@Composable
private fun DiagnosticRow(
    label: String,
    ok: Boolean,
    onFix: () -> Unit,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    val tone = if (ok) c.ok else c.warn
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = spacing.ml, end = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        Box(
            modifier = Modifier.size(30.dp).background(if (ok) c.okSoft else c.warnSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (ok) VellutoIcons.Check else VellutoIcons.TriangleAlert,
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = c.text,
            modifier = Modifier.weight(1f),
        )
        if (ok) {
            Text(
                text = stringResource(R.string.vault_diag_ok),
                style = MaterialTheme.typography.labelLarge,
                color = tone,
                modifier = Modifier.padding(end = spacing.xs),
            )
        } else {
            Button(
                onClick = onFix,
                shape = VellutoShapes.Pill,
                border = BorderStroke(1.dp, c.warnEdge),
                contentPadding = PaddingValues(horizontal = spacing.ml),
                colors = ButtonDefaults.buttonColors(containerColor = c.warnSoft, contentColor = c.warn),
            ) {
                Text(text = stringResource(R.string.vault_diag_fix), style = MaterialTheme.typography.labelLarge)
                Icon(
                    VellutoIcons.ArrowUpRight,
                    contentDescription = null,
                    modifier = Modifier.padding(start = spacing.xs).size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun UpdatesCard(
    currentVersion: String,
    updateState: UpdateUiState,
    onCheck: () -> Unit,
    onInstall: (AvailableUpdate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    // "Versione attuale: %1$s" split into its caption, so the number can stand alone in big numerals.
    val caption =
        stringResource(R.string.vault_current_version, "")
            .trim()
            .trimEnd(':')
            .trim()
    SettingsCard(
        title = stringResource(R.string.vault_updates_title),
        icon = VellutoIcons.Upload,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(PeopleHubTheme.spacing.xxs),
        ) {
            Text(text = caption, style = MaterialTheme.typography.bodySmall, color = c.text2)
            Text(text = currentVersion, style = PeopleHubTheme.type.kpiMedium, color = c.text, maxLines = 1)
        }
        when (val current = updateState) {
            is UpdateUiState.Available ->
                VellutoButton(
                    text = stringResource(R.string.vault_update_to, current.update.versionName),
                    onClick = { onInstall(current.update) },
                    icon = VellutoIcons.Download,
                    modifier = Modifier.fillMaxWidth(),
                )
            UpdateUiState.UpToDate ->
                StatusPill(
                    label = stringResource(R.string.update_up_to_date),
                    content = c.ok,
                    container = c.okSoft,
                    icon = VellutoIcons.Check,
                    // The only string here is a full sentence: let it wrap instead of truncating.
                    maxLines = Int.MAX_VALUE,
                )
            is UpdateUiState.Error ->
                Text(current.message, style = MaterialTheme.typography.bodyMedium, color = c.err)
            UpdateUiState.Checking, UpdateUiState.Downloading ->
                StatusPill(
                    label = stringResource(R.string.update_checking),
                    content = c.text2,
                    container = c.surface2,
                    icon = VellutoIcons.RefreshCw,
                    border = c.line,
                )
            UpdateUiState.Idle -> Unit
        }
        VellutoButton(
            text = stringResource(R.string.vault_check_updates),
            onClick = onCheck,
            style = VellutoButtonStyle.Neutral,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The grouped glass card of navigation rows: language (with its current value) and birthday-only. */
@Composable
private fun PreferencesCard(
    languageTag: String,
    onOpenLanguage: () -> Unit,
    onOpenBirthdayOnly: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier.fillMaxWidth(), shape = VellutoShapes.CardLarge, shadow = null) {
        Column(modifier = Modifier.padding(vertical = PeopleHubTheme.spacing.xs)) {
            SettingsRow(
                label = stringResource(R.string.vault_language_title),
                onClick = onOpenLanguage,
                icon = VellutoIcons.Globe,
                value = languageLabel(languageTag),
                trailingIcon = VellutoIcons.ChevronRight,
            )
            Hairline(Modifier.padding(horizontal = 18.dp))
            SettingsRow(
                label = stringResource(R.string.vault_birthday_only_title),
                onClick = onOpenBirthdayOnly,
                icon = VellutoIcons.Gift,
                trailingIcon = VellutoIcons.ChevronRight,
            )
        }
    }
}

/** The danger zone: soft error card with an error hairline, caps title with the warning sign, outlined delete. */
@Composable
private fun DangerCard(
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(c.errSoft, VellutoShapes.CardLarge)
                .lightEdge(VellutoShapes.CardLarge, Color.Transparent, c.errEdge)
                .padding(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Icon(VellutoIcons.TriangleAlert, contentDescription = null, tint = c.err, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(R.string.vault_danger_title).uppercase(),
                style = PeopleHubTheme.type.eyebrow.copy(fontWeight = FontWeight.Bold),
                color = c.err,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(
            text = stringResource(R.string.vault_delete_all_desc),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
            color = c.text2,
        )
        VellutoButton(
            text = stringResource(R.string.vault_delete_all),
            onClick = onDeleteAll,
            style = VellutoButtonStyle.Danger,
            icon = VellutoIcons.Trash2,
            height = 50.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ImportStrategyDialog(onReplace: () -> Unit, onMerge: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vault_import_title)) },
        text = { Text(stringResource(R.string.vault_import_message)) },
        confirmButton = { TextButton(onClick = onMerge) { Text(stringResource(R.string.vault_import_merge)) } },
        dismissButton = { TextButton(onClick = onReplace) { Text(stringResource(R.string.vault_import_replace)) } },
    )
}

@Composable
private fun reminderLabel(offset: ReminderOffset): String =
    when (offset) {
        ReminderOffset.SAME_DAY -> stringResource(R.string.vault_reminder_same_day)
        ReminderOffset.ONE_DAY -> stringResource(R.string.vault_reminder_1d)
        ReminderOffset.THREE_DAYS -> stringResource(R.string.vault_reminder_3d)
        ReminderOffset.ONE_WEEK -> stringResource(R.string.vault_reminder_7d)
        ReminderOffset.ONE_MONTH -> stringResource(R.string.vault_reminder_30d)
    }

@Composable
private fun languageLabel(tag: String): String =
    when (tag) {
        AppLocale.SYSTEM -> stringResource(R.string.language_system)
        "it" -> stringResource(R.string.language_italian)
        else -> stringResource(R.string.language_english)
    }

@Composable
private fun SettingsPreview(darkTheme: Boolean) {
    PeopleHubTheme(darkTheme = darkTheme) {
        SettingsContent(
            state =
                SettingsUiState(
                    settings = AppSettings(),
                    isBusy = false,
                    message = null,
                    lastBirthdaySweep = LocalDate.of(2026, 10, 24),
                    lastRelationshipSweep = LocalDate.of(2026, 10, 24),
                ),
            currentVersion = "1.6.11",
            updateState = UpdateUiState.UpToDate,
            diagnostics =
                NotificationDiagnostics(
                    notificationsAllowed = true,
                    exactAlarmsAllowed = true,
                    batteryUnrestricted = false,
                ),
            languageTag = "it",
            snackbarHostState = remember { SnackbarHostState() },
            actions = SettingsActions(),
        )
    }
}

@Preview(name = "Cassaforte chiaro", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SettingsLightPreview() {
    SettingsPreview(darkTheme = false)
}

@Preview(name = "Cassaforte scuro", widthDp = 390, heightDp = 844, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsDarkPreview() {
    SettingsPreview(darkTheme = true)
}
