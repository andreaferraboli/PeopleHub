package com.peoplehub.feature.people.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
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
import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.GhostButton
import com.peoplehub.core.ui.components.GoldDivider
import com.peoplehub.core.ui.components.PersonAvatar
import com.peoplehub.core.ui.components.PrimaryGoldButton
import com.peoplehub.core.ui.components.TooltipIconButton
import com.peoplehub.feature.people.R
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val BirthdayInputFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

/** Add or edit a person. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPersonScreen(
    onBack: () -> Unit,
    viewModel: AddEditPersonViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    androidx.compose.runtime.LaunchedEffect(saved) {
        if (saved) onBack()
    }
    androidx.compose.runtime.LaunchedEffect(error) {
        val message = error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onErrorShown()
        }
    }

    val scope = rememberCoroutineScope()
    val photoLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                scope.launch {
                    val path = PhotoStorage.savePhoto(context, uri)
                    if (path != null) viewModel.onPhotoChange(path)
                }
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text =
                            if (viewModel.isEditing) {
                                stringResource(R.string.edit_title_edit)
                            } else {
                                stringResource(R.string.edit_title_new)
                            },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                navigationIcon = {
                    TooltipIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        description = stringResource(R.string.action_back),
                        onClick = onBack,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PersonAvatar(
                initials =
                    form.firstName
                        .take(1)
                        .uppercase()
                        .ifEmpty { "+" },
                photoPath = form.photoPath,
                size = 110.dp,
                shape = RoundedCornerShape(12.dp),
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable {
                            photoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
            )

            OutlinedTextField(
                value = form.firstName,
                onValueChange = viewModel::onFirstNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.edit_first_name)) },
                isError = form.firstName.isBlank(),
                shape = RoundedCornerShape(6.dp),
            )
            OutlinedTextField(
                value = form.lastName,
                onValueChange = viewModel::onLastNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.edit_last_name)) },
                shape = RoundedCornerShape(6.dp),
            )

            BirthdayField(birthday = form.birthday, onBirthdayChange = viewModel::onBirthdayChange)

            TagEditor(tags = form.tags, onAddTag = viewModel::onAddTag, onRemoveTag = viewModel::onRemoveTag)

            InterestEditor(
                interests = form.interests,
                onAddInterest = viewModel::onAddInterest,
                onInterestChange = viewModel::onInterestChange,
                onRemoveInterest = viewModel::onRemoveInterest,
            )

            SettingToggleRow(
                title = stringResource(R.string.edit_notifications_title),
                description = stringResource(R.string.edit_notifications_hint),
                checked = form.notificationsEnabled,
                onCheckedChange = viewModel::onNotificationsEnabledChange,
            )

            SettingToggleRow(
                title = stringResource(R.string.edit_birthday_only_title),
                description = stringResource(R.string.edit_birthday_only_hint),
                checked = form.birthdayOnly,
                onCheckedChange = viewModel::onBirthdayOnlyChange,
            )

            SettingToggleRow(
                title = stringResource(R.string.edit_family_title),
                description = stringResource(R.string.edit_family_hint),
                checked = form.isFamily,
                onCheckedChange = viewModel::onIsFamilyChange,
            )

            ThresholdEditor(
                threshold = form.checkInThreshold,
                onEnabledChange = viewModel::onThresholdEnabledChange,
                onChange = viewModel::onThresholdChange,
            )

            OutlinedTextField(
                value = form.notes,
                onValueChange = viewModel::onNotesChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                label = { Text(stringResource(R.string.edit_notes)) },
                shape = RoundedCornerShape(6.dp),
            )

            PrimaryGoldButton(
                text = stringResource(R.string.action_save),
                onClick = viewModel::onSave,
                enabled = form.canSave,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthdayField(birthday: LocalDate?, onBirthdayChange: (LocalDate?) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Column {
        CapsLabel(text = stringResource(R.string.edit_birthday))
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = birthday?.format(BirthdayInputFormatter) ?: stringResource(R.string.edit_birthday_add),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (showPicker) {
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = birthday?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onBirthdayChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditor(tags: List<String>, onAddTag: (String) -> Unit, onRemoveTag: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    Column {
        CapsLabel(text = stringResource(R.string.edit_tags))
        FlowRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                AssistChip(
                    onClick = { onRemoveTag(tag) },
                    label = { Text(tag) },
                    trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = null) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.edit_tag_add)) },
                shape = RoundedCornerShape(6.dp),
            )
            TooltipIconButton(
                icon = Icons.Filled.Add,
                description = stringResource(R.string.edit_tag_add),
                onClick = {
                    onAddTag(input)
                    input = ""
                },
            )
        }
    }
}

@Composable
private fun InterestEditor(
    interests: List<com.peoplehub.core.domain.model.Interest>,
    onAddInterest: () -> Unit,
    onInterestChange: (Int, String, String) -> Unit,
    onRemoveInterest: (Int) -> Unit,
) {
    Column {
        CapsLabel(text = stringResource(R.string.edit_interests))
        GoldDivider(modifier = Modifier.padding(vertical = 8.dp))
        interests.forEachIndexed { index, interest ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = interest.key,
                    onValueChange = { onInterestChange(index, it, interest.value) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.edit_interest_key)) },
                    shape = RoundedCornerShape(6.dp),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = interest.value,
                    onValueChange = { onInterestChange(index, interest.key, it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.edit_interest_value)) },
                    shape = RoundedCornerShape(6.dp),
                )
                TooltipIconButton(
                    icon = Icons.Outlined.Close,
                    description = stringResource(R.string.action_remove),
                    onClick = { onRemoveInterest(index) },
                )
            }
        }
        GhostButton(
            text = stringResource(R.string.edit_interest_add),
            onClick = onAddInterest,
            icon = Icons.Filled.Add,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            CapsLabel(text = title)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ThresholdEditor(
    threshold: CheckInThreshold?,
    onEnabledChange: (Boolean) -> Unit,
    onChange: (Int, Int) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CapsLabel(text = stringResource(R.string.edit_threshold_title))
                Text(
                    text = stringResource(R.string.edit_threshold_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = threshold != null, onCheckedChange = onEnabledChange)
        }
        if (threshold != null) {
            ThresholdStepper(
                label = stringResource(R.string.edit_threshold_warning),
                value = threshold.warningDays,
                decrementDescription = stringResource(R.string.cd_threshold_decrease_warning),
                incrementDescription = stringResource(R.string.cd_threshold_increase_warning),
                onDecrement = { onChange(threshold.warningDays - 1, threshold.criticalDays) },
                onIncrement = { onChange(threshold.warningDays + 1, threshold.criticalDays) },
            )
            ThresholdStepper(
                label = stringResource(R.string.edit_threshold_critical),
                value = threshold.criticalDays,
                decrementDescription = stringResource(R.string.cd_threshold_decrease_critical),
                incrementDescription = stringResource(R.string.cd_threshold_increase_critical),
                onDecrement = { onChange(threshold.warningDays, threshold.criticalDays - 1) },
                onIncrement = { onChange(threshold.warningDays, threshold.criticalDays + 1) },
            )
        }
    }
}

@Composable
private fun ThresholdStepper(
    label: String,
    value: Int,
    decrementDescription: String,
    incrementDescription: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TooltipIconButton(
                icon = Icons.Outlined.Remove,
                description = decrementDescription,
                onClick = onDecrement,
            )
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            TooltipIconButton(
                icon = Icons.Filled.Add,
                description = incrementDescription,
                onClick = onIncrement,
            )
        }
    }
}
