package com.peoplehub.feature.people.edit

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.domain.model.Interest
import com.peoplehub.core.ui.components.AmbientGlow
import com.peoplehub.core.ui.components.GlassIconButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.Hairline
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.PersonAvatar
import com.peoplehub.core.ui.components.PhotoCropDialog
import com.peoplehub.core.ui.components.SettingsCard
import com.peoplehub.core.ui.components.StepperRow
import com.peoplehub.core.ui.components.SwitchRow
import com.peoplehub.core.ui.components.TooltipIconButton
import com.peoplehub.core.ui.components.VellutoBackground
import com.peoplehub.core.ui.components.VellutoButton
import com.peoplehub.core.ui.components.VellutoButtonStyle
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.VellutoElevation
import com.peoplehub.core.ui.modifier.lightEdge
import com.peoplehub.core.ui.modifier.riseIn
import com.peoplehub.core.ui.modifier.safeBottomBarPadding
import com.peoplehub.core.ui.modifier.softShadow
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes
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
    val spacing = PeopleHubTheme.spacing

    LaunchedEffect(saved) {
        if (saved) onBack()
    }
    LaunchedEffect(error) {
        val message = error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onErrorShown()
        }
    }

    val scope = rememberCoroutineScope()
    var cropUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val photoLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) cropUri = uri
        }

    cropUri?.let { uri ->
        PhotoCropDialog(
            sourceUri = uri,
            onCancel = { cropUri = null },
            onCropped = { bitmap, _ ->
                cropUri = null
                scope.launch {
                    val path = PhotoStorage.saveBitmap(context, bitmap)
                    if (path != null) viewModel.onPhotoChange(path)
                }
            },
        )
    }

    VellutoBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                PeopleHubTopBar(
                    title =
                        if (viewModel.isEditing) {
                            stringResource(R.string.edit_title_edit)
                        } else {
                            stringResource(R.string.edit_title_new)
                        },
                    navigationIcon = {
                        GlassIconButton(
                            icon = VellutoIcons.ArrowLeft,
                            description = stringResource(R.string.action_back),
                            onClick = onBack,
                            modifier = Modifier.padding(start = spacing.s, end = spacing.xs),
                        )
                    },
                )
            },
            bottomBar = {
                VellutoButton(
                    text = stringResource(R.string.action_save),
                    onClick = viewModel::onSave,
                    enabled = form.canSave,
                    icon = VellutoIcons.Check,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .safeBottomBarPadding()
                            .padding(horizontal = spacing.gutter, vertical = spacing.m),
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            AddEditPersonContent(
                form = form,
                onPickPhoto = {
                    photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onFirstNameChange = viewModel::onFirstNameChange,
                onLastNameChange = viewModel::onLastNameChange,
                onBirthdayChange = viewModel::onBirthdayChange,
                onAddTag = viewModel::onAddTag,
                onRemoveTag = viewModel::onRemoveTag,
                onAddInterest = viewModel::onAddInterest,
                onInterestChange = viewModel::onInterestChange,
                onRemoveInterest = viewModel::onRemoveInterest,
                onNotificationsEnabledChange = viewModel::onNotificationsEnabledChange,
                onBirthdayOnlyChange = viewModel::onBirthdayOnlyChange,
                onIsFamilyChange = viewModel::onIsFamilyChange,
                onThresholdEnabledChange = viewModel::onThresholdEnabledChange,
                onThresholdChange = viewModel::onThresholdChange,
                onNotesChange = viewModel::onNotesChange,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

/**
 * The stateless person form: portrait picker, then the fields grouped in glass panels (identity,
 * birthday, tags, interests, tracking switches, cadence, notes). The save bar lives in the screen.
 */
@Composable
internal fun AddEditPersonContent(
    form: PersonForm,
    onPickPhoto: () -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onBirthdayChange: (LocalDate?) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onAddInterest: () -> Unit,
    onInterestChange: (Int, String, String) -> Unit,
    onRemoveInterest: (Int) -> Unit,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    onBirthdayOnlyChange: (Boolean) -> Unit,
    onIsFamilyChange: (Boolean) -> Unit,
    onThresholdEnabledChange: (Boolean) -> Unit,
    onThresholdChange: (Int, Int) -> Unit,
    onNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = PeopleHubTheme.spacing
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = spacing.gutter, end = spacing.gutter, top = spacing.s, bottom = spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(spacing.ml),
    ) {
        PhotoPicker(
            initials =
                form.firstName
                    .take(1)
                    .uppercase()
                    .ifEmpty { "+" },
            photoPath = form.photoPath,
            onClick = onPickPhoto,
            modifier = Modifier.align(Alignment.CenterHorizontally).riseIn(0),
        )

        FormPanel(modifier = Modifier.riseIn(1)) {
            OutlinedTextField(
                value = form.firstName,
                onValueChange = onFirstNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.edit_first_name)) },
                isError = form.firstName.isBlank(),
            )
            OutlinedTextField(
                value = form.lastName,
                onValueChange = onLastNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.edit_last_name)) },
            )
        }

        BirthdayField(birthday = form.birthday, onBirthdayChange = onBirthdayChange, modifier = Modifier.riseIn(2))

        TagEditor(tags = form.tags, onAddTag = onAddTag, onRemoveTag = onRemoveTag, modifier = Modifier.riseIn(3))

        InterestEditor(
            interests = form.interests,
            onAddInterest = onAddInterest,
            onInterestChange = onInterestChange,
            onRemoveInterest = onRemoveInterest,
            modifier = Modifier.riseIn(4),
        )

        FormPanel(modifier = Modifier.riseIn(5)) {
            SwitchRow(
                label = stringResource(R.string.edit_notifications_title),
                supporting = stringResource(R.string.edit_notifications_hint),
                checked = form.notificationsEnabled,
                onCheckedChange = onNotificationsEnabledChange,
            )
            Hairline()
            SwitchRow(
                label = stringResource(R.string.edit_birthday_only_title),
                supporting = stringResource(R.string.edit_birthday_only_hint),
                checked = form.birthdayOnly,
                onCheckedChange = onBirthdayOnlyChange,
            )
            Hairline()
            SwitchRow(
                label = stringResource(R.string.edit_family_title),
                supporting = stringResource(R.string.edit_family_hint),
                checked = form.isFamily,
                onCheckedChange = onIsFamilyChange,
            )
        }

        ThresholdEditor(
            threshold = form.checkInThreshold,
            onEnabledChange = onThresholdEnabledChange,
            onChange = onThresholdChange,
            modifier = Modifier.riseIn(6),
        )

        FormPanel(modifier = Modifier.riseIn(7)) {
            OutlinedTextField(
                value = form.notes,
                onValueChange = onNotesChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 20,
                label = { Text(stringResource(R.string.edit_notes)) },
            )
        }
    }
}

/** A glass group of form fields without a heading, with the same rhythm as a [SettingsCard]. */
@Composable
private fun FormPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val spacing = PeopleHubTheme.spacing
    GlassPanel(modifier = modifier.fillMaxWidth(), shape = VellutoShapes.CardLarge, shadow = VellutoElevation.Raised) {
        Column(modifier = Modifier.padding(spacing.xl), verticalArrangement = Arrangement.spacedBy(spacing.m)) {
            content()
        }
    }
}

/**
 * The portrait: a large round avatar on a soft glow, with the accent "add photo" badge tucked into its
 * lower-right edge. The whole portrait opens the photo picker.
 */
@Composable
private fun PhotoPicker(
    initials: String,
    photoPath: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    Box(modifier = modifier.padding(top = PeopleHubTheme.spacing.s)) {
        AmbientGlow(size = 220.dp, strong = false, modifier = Modifier.matchParentSize())
        PersonAvatar(
            initials = initials,
            photoPath = photoPath,
            size = 112.dp,
            ringColor = c.accentEdge,
            modifier =
                Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onClick),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(36.dp)
                    .softShadow(c.glowStrong, CircleShape, VellutoElevation.GlowSmall)
                    .background(c.accent, CircleShape)
                    .lightEdge(CircleShape, c.hi, c.bg, width = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(VellutoIcons.ImagePlus, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthdayField(
    birthday: LocalDate?,
    onBirthdayChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    SettingsCard(title = stringResource(R.string.edit_birthday), icon = VellutoIcons.Cake, modifier = modifier) {
        VellutoButton(
            text = birthday?.format(BirthdayInputFormatter) ?: stringResource(R.string.edit_birthday_add),
            onClick = { showPicker = true },
            style = if (birthday == null) VellutoButtonStyle.Soft else VellutoButtonStyle.Neutral,
            icon = VellutoIcons.Calendar,
            modifier = Modifier.fillMaxWidth(),
        )
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
private fun TagEditor(
    tags: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    val spacing = PeopleHubTheme.spacing
    SettingsCard(title = stringResource(R.string.edit_tags), icon = VellutoIcons.Users, modifier = modifier) {
        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
                verticalArrangement = Arrangement.spacedBy(spacing.s),
            ) {
                tags.forEach { tag -> RemovableTag(label = tag, onRemove = { onRemoveTag(tag) }) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.edit_tag_add)) },
            )
            GlassIconButton(
                icon = VellutoIcons.Plus,
                description = stringResource(R.string.edit_tag_add),
                selected = true,
                onClick = {
                    onAddTag(input)
                    input = ""
                },
            )
        }
    }
}

/**
 * A tag already on the person: a 48dp soft accent pill with its name and a trailing cross. Tapping it
 * removes the tag, as the old assist chip did.
 */
@Composable
private fun RemovableTag(
    label: String,
    onRemove: () -> Unit,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    Row(
        modifier =
            Modifier
                .heightIn(min = spacing.touchTarget)
                .background(c.accentSoft, CircleShape)
                .lightEdge(CircleShape, Color.Transparent, c.accentEdge)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onRemove)
                .padding(start = spacing.l, end = spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = c.accentOnSoft,
            maxLines = 1,
        )
        Icon(VellutoIcons.X, contentDescription = null, tint = c.accentOnSoft, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun InterestEditor(
    interests: List<Interest>,
    onAddInterest: () -> Unit,
    onInterestChange: (Int, String, String) -> Unit,
    onRemoveInterest: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    SettingsCard(title = stringResource(R.string.edit_interests), icon = VellutoIcons.Sparkles, modifier = modifier) {
        interests.forEachIndexed { index, interest ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
            ) {
                OutlinedTextField(
                    value = interest.key,
                    onValueChange = { onInterestChange(index, it, interest.value) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.edit_interest_key)) },
                )
                OutlinedTextField(
                    value = interest.value,
                    onValueChange = { onInterestChange(index, interest.key, it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.edit_interest_value)) },
                )
                TooltipIconButton(
                    icon = VellutoIcons.X,
                    description = stringResource(R.string.action_remove),
                    onClick = { onRemoveInterest(index) },
                    tint = c.text2,
                )
            }
        }
        VellutoButton(
            text = stringResource(R.string.edit_interest_add),
            onClick = onAddInterest,
            style = VellutoButtonStyle.Soft,
            icon = VellutoIcons.Plus,
        )
    }
}

@Composable
private fun ThresholdEditor(
    threshold: CheckInThreshold?,
    onEnabledChange: (Boolean) -> Unit,
    onChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    SettingsCard(title = stringResource(R.string.edit_threshold_title), icon = VellutoIcons.Clock, modifier = modifier) {
        SwitchRow(
            label = stringResource(R.string.edit_threshold_hint),
            checked = threshold != null,
            onCheckedChange = onEnabledChange,
        )
        if (threshold != null) {
            StepperRow(
                label = stringResource(R.string.edit_threshold_warning),
                value = threshold.warningDays.toString(),
                onDecrement = { onChange(threshold.warningDays - 1, threshold.criticalDays) },
                onIncrement = { onChange(threshold.warningDays + 1, threshold.criticalDays) },
                decrementLabel = stringResource(R.string.cd_threshold_decrease_warning),
                incrementLabel = stringResource(R.string.cd_threshold_increase_warning),
                minusIcon = VellutoIcons.Minus,
                plusIcon = VellutoIcons.Plus,
                dotColor = c.warn,
            )
            StepperRow(
                label = stringResource(R.string.edit_threshold_critical),
                value = threshold.criticalDays.toString(),
                onDecrement = { onChange(threshold.warningDays, threshold.criticalDays - 1) },
                onIncrement = { onChange(threshold.warningDays, threshold.criticalDays + 1) },
                decrementLabel = stringResource(R.string.cd_threshold_decrease_critical),
                incrementLabel = stringResource(R.string.cd_threshold_increase_critical),
                minusIcon = VellutoIcons.Minus,
                plusIcon = VellutoIcons.Plus,
                dotColor = c.err,
            )
        }
    }
}

// region Previews (sample data lives only here)

@Composable
private fun AddEditPersonPreview(dark: Boolean) {
    val form =
        PersonForm(
            id = 7L,
            firstName = "Francesca",
            lastName = "Lombardi",
            birthday = LocalDate.of(1994, 11, 3),
            tags = listOf("Amici", "Università"),
            interests = listOf(Interest("Libro preferito", "Le città invisibili")),
            notes = "Si è trasferita a Torino a settembre.",
            checkInThreshold = CheckInThreshold.Default,
            notificationsEnabled = true,
        )
    PeopleHubTheme(darkTheme = dark) {
        VellutoBackground {
            AddEditPersonContent(
                form = form,
                onPickPhoto = {},
                onFirstNameChange = {},
                onLastNameChange = {},
                onBirthdayChange = {},
                onAddTag = {},
                onRemoveTag = {},
                onAddInterest = {},
                onInterestChange = { _, _, _ -> },
                onRemoveInterest = {},
                onNotificationsEnabledChange = {},
                onBirthdayOnlyChange = {},
                onIsFamilyChange = {},
                onThresholdEnabledChange = {},
                onThresholdChange = { _, _ -> },
                onNotesChange = {},
                modifier = Modifier.statusBarsPadding().padding(top = 64.dp),
            )
        }
    }
}

@Preview(name = "Edit person, light", widthDp = 390, heightDp = 844)
@Composable
private fun AddEditPersonLightPreview() {
    AddEditPersonPreview(dark = false)
}

@Preview(name = "Edit person, dark", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AddEditPersonDarkPreview() {
    AddEditPersonPreview(dark = true)
}

// endregion
