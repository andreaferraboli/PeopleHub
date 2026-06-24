package com.peoplehub.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.R
import com.peoplehub.core.domain.model.ImportStrategy
import com.peoplehub.core.domain.model.ReminderOffset
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.GhostButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.GoldDivider
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.PrimaryGoldButton
import com.peoplehub.core.ui.components.TooltipIconButton
import com.peoplehub.io.readTextRobust
import com.peoplehub.update.AvailableUpdate
import com.peoplehub.update.UpdateUiState
import com.peoplehub.update.UpdateViewModel
import kotlinx.coroutines.launch

/** Settings ("Vault"): backup & restore, frequency thresholds, and reminder preferences. */
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = { PeopleHubTopBar(title = stringResource(R.string.vault_title), centered = true) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
            }

            UpdatesSection(
                currentVersion = updateViewModel.currentVersion,
                updateState = updateState,
                onCheck = { updateViewModel.check(silent = false) },
                onInstall = { update -> updateViewModel.downloadAndInstall(update) },
            )

            GoldDivider()

            LanguageEntry(onOpenLanguage = onOpenLanguage)

            GoldDivider()

            ImportGuideEntry(onOpenImportGuide = onOpenImportGuide)

            GoldDivider()

            BirthdayOnlyEntry(onOpenBirthdayOnly = onOpenBirthdayOnly)

            GoldDivider()

            BackupSection(
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
            )

            GoldDivider()

            ThresholdSection(
                warningDays = state.settings.defaultCheckInThreshold.warningDays,
                criticalDays = state.settings.defaultCheckInThreshold.criticalDays,
                onChange = viewModel::onThresholdChange,
            )

            GoldDivider()

            ReminderSection(
                enabledOffsets = state.settings.birthdayReminderOffsets,
                onToggle = viewModel::onToggleReminder,
            )

            GoldDivider()

            AlarmSection(
                useExactAlarms = state.settings.useExactAlarms,
                reminderHour = state.settings.dailyReminderHour,
                onToggleExact = viewModel::onToggleExactAlarms,
                onHourChange = viewModel::onReminderHourChange,
            )

            GoldDivider()

            DangerSection(onDeleteAll = { showDeleteAllDialog = true })
        }
    }

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

@Composable
private fun UpdatesSection(
    currentVersion: String,
    updateState: UpdateUiState,
    onCheck: () -> Unit,
    onInstall: (AvailableUpdate) -> Unit,
) {
    SettingsPanel(title = stringResource(R.string.vault_updates_title)) {
        Text(
            text = stringResource(R.string.vault_current_version, currentVersion),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (val current = updateState) {
            is UpdateUiState.Available -> {
                PrimaryGoldButton(
                    text = stringResource(R.string.vault_update_to, current.update.versionName),
                    onClick = { onInstall(current.update) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            UpdateUiState.UpToDate ->
                Text(
                    stringResource(R.string.update_up_to_date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            is UpdateUiState.Error ->
                Text(current.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            UpdateUiState.Checking, UpdateUiState.Downloading ->
                Text(
                    stringResource(R.string.update_checking),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            UpdateUiState.Idle -> Unit
        }
        GhostButton(text = stringResource(R.string.vault_check_updates), onClick = onCheck, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DangerSection(onDeleteAll: () -> Unit) {
    SettingsPanel(title = stringResource(R.string.vault_danger_title)) {
        Text(
            text = stringResource(R.string.vault_delete_all_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onDeleteAll,
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
        ) {
            Text(stringResource(R.string.vault_delete_all))
        }
    }
}

@Composable
private fun ImportGuideEntry(onOpenImportGuide: () -> Unit) {
    SettingsPanel(title = stringResource(R.string.vault_guide_title)) {
        Text(
            text = stringResource(R.string.vault_guide_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GhostButton(
            text = stringResource(R.string.vault_open_guide),
            onClick = onOpenImportGuide,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LanguageEntry(onOpenLanguage: () -> Unit) {
    SettingsPanel(title = stringResource(R.string.vault_language_title)) {
        Text(
            text = stringResource(R.string.vault_language_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GhostButton(
            text = stringResource(R.string.vault_language_open),
            onClick = onOpenLanguage,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BirthdayOnlyEntry(onOpenBirthdayOnly: () -> Unit) {
    SettingsPanel(title = stringResource(R.string.vault_birthday_only_title)) {
        Text(
            text = stringResource(R.string.vault_birthday_only_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GhostButton(
            text = stringResource(R.string.vault_birthday_only_open),
            onClick = onOpenBirthdayOnly,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BackupSection(
    onExportAll: () -> Unit,
    onImportAll: () -> Unit,
    onExportBirthdays: () -> Unit,
    onShareBirthdays: () -> Unit,
) {
    SettingsPanel(title = stringResource(R.string.vault_backup_title)) {
        Text(
            text = stringResource(R.string.vault_backup_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GhostButton(text = stringResource(R.string.vault_export_all), onClick = onExportAll, modifier = Modifier.fillMaxWidth())
        GhostButton(text = stringResource(R.string.vault_import_all), onClick = onImportAll, modifier = Modifier.fillMaxWidth())
        GhostButton(text = stringResource(R.string.vault_export_birthdays), onClick = onExportBirthdays, modifier = Modifier.fillMaxWidth())
        GhostButton(text = stringResource(R.string.vault_share_birthdays), onClick = onShareBirthdays, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ThresholdSection(warningDays: Int, criticalDays: Int, onChange: (Int, Int) -> Unit) {
    SettingsPanel(title = stringResource(R.string.vault_threshold_title)) {
        Text(
            text = stringResource(R.string.vault_threshold_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Stepper(
            label = stringResource(R.string.vault_threshold_warning),
            value = warningDays,
            onDecrement = { onChange((warningDays - 1).coerceAtLeast(1), criticalDays) },
            onIncrement = { onChange(warningDays + 1, criticalDays) },
        )
        Stepper(
            label = stringResource(R.string.vault_threshold_critical),
            value = criticalDays,
            onDecrement = { onChange(warningDays, (criticalDays - 1).coerceAtLeast(warningDays + 1)) },
            onIncrement = { onChange(warningDays, criticalDays + 1) },
        )
    }
}

@Composable
private fun ReminderSection(enabledOffsets: Set<ReminderOffset>, onToggle: (ReminderOffset, Boolean) -> Unit) {
    SettingsPanel(title = stringResource(R.string.vault_reminders_title)) {
        ReminderOffset.entries.forEach { offset ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = reminderLabel(offset),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Checkbox(
                    checked = offset in enabledOffsets,
                    onCheckedChange = { checked -> onToggle(offset, checked) },
                )
            }
        }
    }
}

@Composable
private fun AlarmSection(
    useExactAlarms: Boolean,
    reminderHour: Int,
    onToggleExact: (Boolean) -> Unit,
    onHourChange: (Int) -> Unit,
) {
    SettingsPanel(title = stringResource(R.string.vault_alarms_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.vault_exact_alarms),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Switch(checked = useExactAlarms, onCheckedChange = onToggleExact)
        }
        Stepper(
            label = stringResource(R.string.vault_reminder_hour),
            value = reminderHour,
            onDecrement = { onHourChange((reminderHour - 1).coerceAtLeast(0)) },
            onIncrement = { onHourChange((reminderHour + 1).coerceAtMost(23)) },
        )
    }
}

@Composable
private fun Stepper(label: String, value: Int, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TooltipIconButton(
                icon = Icons.Outlined.Remove,
                description = stringResource(R.string.cd_stepper_decrease),
                onClick = onDecrement,
            )
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            TooltipIconButton(
                icon = Icons.Outlined.Add,
                description = stringResource(R.string.cd_stepper_increase),
                onClick = onIncrement,
            )
        }
    }
}

@Composable
private fun SettingsPanel(title: String, content: @Composable () -> Unit) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CapsLabel(text = title)
            content()
        }
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
        ReminderOffset.ONE_DAY -> stringResource(R.string.vault_reminder_1d)
        ReminderOffset.THREE_DAYS -> stringResource(R.string.vault_reminder_3d)
        ReminderOffset.ONE_WEEK -> stringResource(R.string.vault_reminder_7d)
        ReminderOffset.ONE_MONTH -> stringResource(R.string.vault_reminder_30d)
    }
