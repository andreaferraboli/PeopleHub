package com.peoplehub.feature.events.edit

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.peoplehub.core.domain.model.CropTransform
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.ChipSelectedStyle
import com.peoplehub.core.ui.components.GlassIconButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.PhotoCropDialog
import com.peoplehub.core.ui.components.SurfaceCard
import com.peoplehub.core.ui.components.SwitchRow
import com.peoplehub.core.ui.components.TagChip
import com.peoplehub.core.ui.components.VellutoBackground
import com.peoplehub.core.ui.components.VellutoButton
import com.peoplehub.core.ui.components.VellutoButtonStyle
import com.peoplehub.core.ui.components.coverSceneFor
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.VellutoElevation
import com.peoplehub.core.ui.modifier.lightEdge
import com.peoplehub.core.ui.modifier.riseIn
import com.peoplehub.core.ui.modifier.safeBottomBarPadding
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes
import com.peoplehub.feature.events.R
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")
private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Wide banner ratio for event card backgrounds, matching how they render on cards and detail. */
private const val EVENT_BACKGROUND_ASPECT_RATIO = 16f / 9f

/**
 * A pending crop: the image to frame, where its original lives (so it can be re-framed later), and
 * the framing to open with — [CropTransform.Default] for a fresh pick, the saved one when
 * repositioning.
 */
private data class CropRequest(
    val uri: Uri,
    val sourcePath: String?,
    val initialCrop: CropTransform,
)

/** Add or edit an event. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditEventScreen(
    onBack: () -> Unit,
    viewModel: AddEditEventViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val categorySuggestions by viewModel.categorySuggestions.collectAsStateWithLifecycle()
    val backgroundImageSuggestions by viewModel.backgroundImageSuggestions.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cropRequest by remember { mutableStateOf<CropRequest?>(null) }
    val imageLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            // Copy the pick into internal storage first: the picker's read grant is short-lived, but the
            // original has to outlive it so the framing can be adjusted again later.
            scope.launch {
                val sourcePath = EventImageStorage.saveSource(context, uri)
                cropRequest =
                    CropRequest(
                        uri = sourcePath?.let { Uri.fromFile(File(it)) } ?: uri,
                        sourcePath = sourcePath,
                        initialCrop = CropTransform.Default,
                    )
            }
        }

    cropRequest?.let { request ->
        PhotoCropDialog(
            sourceUri = request.uri,
            aspectRatio = EVENT_BACKGROUND_ASPECT_RATIO,
            initialCrop = request.initialCrop,
            onCancel = { cropRequest = null },
            onCropped = { bitmap, crop ->
                cropRequest = null
                scope.launch {
                    val path = EventImageStorage.saveBitmap(context, bitmap)
                    if (path != null) viewModel.onBackgroundCropped(path, request.sourcePath, crop)
                }
            },
        )
    }

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

    val spacing = PeopleHubTheme.spacing
    VellutoBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                PeopleHubTopBar(
                    title =
                        if (viewModel.isEditing) {
                            stringResource(R.string.edit_event_title_edit)
                        } else {
                            stringResource(R.string.edit_event_title_new)
                        },
                    navigationIcon = {
                        GlassIconButton(
                            icon = VellutoIcons.ArrowLeft,
                            description = stringResource(R.string.action_back),
                            onClick = onBack,
                            modifier = Modifier.padding(start = spacing.s),
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
                            .padding(horizontal = spacing.heroInset, vertical = spacing.l),
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
                        .padding(horizontal = spacing.gutter, vertical = spacing.s),
                verticalArrangement = Arrangement.spacedBy(spacing.ml),
            ) {
                FormSection(modifier = Modifier.riseIn(0)) {
                    OutlinedTextField(
                        value = form.title,
                        onValueChange = viewModel::onTitleChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.event_field_title)) },
                        isError = form.title.isBlank(),
                    )
                }

                FormSection(title = stringResource(R.string.event_field_date), modifier = Modifier.riseIn(1)) {
                    DateTimeFields(
                        date = form.dateTime.toLocalDate().format(DateFormatter),
                        time = form.dateTime.toLocalTime().format(TimeFormatter),
                        dateMillis =
                            form.dateTime
                                .toLocalDate()
                                .atStartOfDay(ZoneOffset.UTC)
                                .toInstant()
                                .toEpochMilli(),
                        initialHour = form.dateTime.hour,
                        initialMinute = form.dateTime.minute,
                        onDateMillis = { millis ->
                            viewModel.onDateChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                        },
                        onTime = viewModel::onTimeChange,
                    )
                }

                FormSection(title = stringResource(R.string.event_field_category), modifier = Modifier.riseIn(2)) {
                    OutlinedTextField(
                        value = form.category,
                        onValueChange = viewModel::onCategoryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.event_field_category)) },
                    )
                    CategorySuggestions(
                        suggestions = categorySuggestions,
                        selected = form.category,
                        onSelect = viewModel::onCategoryChange,
                    )
                }

                FormSection(title = stringResource(R.string.event_field_background), modifier = Modifier.riseIn(3)) {
                    BackgroundImageSection(
                        selectedPath = form.backgroundImagePath,
                        previewCategory = form.category,
                        previewTitle = form.title,
                        suggestions = backgroundImageSuggestions,
                        onPickNew = {
                            imageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onReposition = {
                            val source = form.backgroundCropSource ?: return@BackgroundImageSection
                            cropRequest =
                                CropRequest(
                                    uri = Uri.fromFile(File(source)),
                                    sourcePath = form.backgroundSourcePath,
                                    // Only the stored original was framed by that transform; a cropped stand-in
                                    // starts fresh, otherwise we'd re-apply the zoom on top of itself.
                                    initialCrop =
                                        if (form.backgroundSourcePath != null) {
                                            form.backgroundCrop
                                        } else {
                                            CropTransform.Default
                                        },
                                )
                        },
                        onSelectExisting = viewModel::onBackgroundImageChange,
                        onRemove = { viewModel.onBackgroundImageChange(null) },
                    )
                }

                FormSection(title = stringResource(R.string.event_field_person), modifier = Modifier.riseIn(4)) {
                    PersonPicker(
                        people = people,
                        selectedId = form.personId,
                        onPersonChange = viewModel::onPersonChange,
                    )
                }

                FormSection(title = stringResource(R.string.event_field_description), modifier = Modifier.riseIn(5)) {
                    OutlinedTextField(
                        value = form.description,
                        onValueChange = viewModel::onDescriptionChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        label = { Text(stringResource(R.string.event_field_description)) },
                    )
                }

                FormSection(modifier = Modifier.riseIn(6)) {
                    SwitchRow(
                        label = stringResource(R.string.event_field_pin),
                        checked = form.pinnedToWidget,
                        onCheckedChange = viewModel::onPinnedChange,
                    )
                }
            }
        }
    }
}

/** A form group: glass card with an optional serif title over its fields. */
@Composable
private fun FormSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = PeopleHubTheme.spacing
    GlassPanel(modifier = modifier.fillMaxWidth(), shape = VellutoShapes.CardLarge) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.l),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = PeopleHubTheme.colors.text,
                    modifier = Modifier.padding(horizontal = spacing.xxs).semantics { heading() },
                )
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeFields(
    date: String,
    time: String,
    dateMillis: Long,
    initialHour: Int,
    initialMinute: Int,
    onDateMillis: (Long) -> Unit,
    onTime: (LocalTime) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        PickerField(
            label = stringResource(R.string.event_field_date),
            value = date,
            icon = VellutoIcons.Calendar,
            onClick = { showDatePicker = true },
            modifier = Modifier.weight(1.4f),
        )
        PickerField(
            label = stringResource(R.string.event_field_time),
            value = time,
            icon = VellutoIcons.Clock,
            onClick = { showTimePicker = true },
            modifier = Modifier.weight(1f),
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let(onDateMillis)
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
            colors = DatePickerDefaults.colors(containerColor = c.surface),
        ) {
            DatePicker(state = pickerState, colors = DatePickerDefaults.colors(containerColor = c.surface))
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute)
        Dialog(onDismissRequest = { showTimePicker = false }) {
            SurfaceCard(shape = VellutoShapes.Hero, shadow = VellutoElevation.Raised) {
                Column(
                    modifier = Modifier.padding(spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.l),
                ) {
                    TimePicker(state = timeState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.s, Alignment.End),
                    ) {
                        TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.action_cancel)) }
                        TextButton(onClick = {
                            onTime(LocalTime.of(timeState.hour, timeState.minute))
                            showTimePicker = false
                        }) { Text(stringResource(R.string.action_confirm)) }
                    }
                }
            }
        }
    }
}

/** A tappable field on the inner surface that opens a picker: caps label over the value, with an icon. */
@Composable
private fun PickerField(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    Row(
        modifier =
            modifier
                .heightIn(min = 64.dp)
                .clip(VellutoShapes.Inner)
                .background(c.surface2)
                .lightEdge(VellutoShapes.Inner, Color.Transparent, c.line)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = spacing.ml, vertical = spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Icon(icon, contentDescription = null, tint = c.accentText, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            CapsLabel(text = label)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Quick-pick chips for categories already used by other events. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySuggestions(
    suggestions: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    if (suggestions.isEmpty()) return
    val spacing = PeopleHubTheme.spacing
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
        verticalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        suggestions.forEach { category ->
            TagChip(
                label = category,
                selected = category.equals(selected, ignoreCase = true),
                onClick = { onSelect(category) },
                selectedStyle = ChipSelectedStyle.Soft,
            )
        }
    }
}

/**
 * Lets the user attach a card background image: a preview of the current choice (or, without one, the
 * illustrated cover the event will get), a button to pick a brand-new image, and a row of images
 * already used by other events for quick reuse.
 */
@Composable
private fun BackgroundImageSection(
    selectedPath: String?,
    previewCategory: String,
    previewTitle: String,
    suggestions: List<String>,
    onPickNew: () -> Unit,
    onReposition: () -> Unit,
    onSelectExisting: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    val previewModifier =
        Modifier
            .fillMaxWidth()
            .aspectRatio(EVENT_BACKGROUND_ASPECT_RATIO)
            .clip(VellutoShapes.Hero)
    if (selectedPath != null) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = File(selectedPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = previewModifier.clickable(role = Role.Button, onClick = onReposition),
            )
            Box(Modifier.matchParentSize().lightEdge(VellutoShapes.Hero, Color.Transparent, c.line))
        }
        VellutoButton(
            text = stringResource(R.string.event_background_reposition),
            onClick = onReposition,
            style = VellutoButtonStyle.Neutral,
            icon = VellutoIcons.Crop,
            modifier = Modifier.fillMaxWidth(),
        )
        VellutoButton(
            text = stringResource(R.string.event_background_remove),
            onClick = onRemove,
            style = VellutoButtonStyle.Danger,
            icon = VellutoIcons.Trash2,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        val scene = remember(previewCategory, previewTitle) { coverSceneFor(previewCategory.ifBlank { null }, previewTitle) }
        Image(
            painter = painterResource(scene.drawable),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = previewModifier,
        )
    }
    VellutoButton(
        text = stringResource(R.string.event_background_add),
        onClick = onPickNew,
        style = VellutoButtonStyle.Soft,
        icon = VellutoIcons.ImagePlus,
        modifier = Modifier.fillMaxWidth(),
    )
    if (suggestions.isNotEmpty()) {
        Text(
            text = stringResource(R.string.event_background_existing),
            style = MaterialTheme.typography.bodySmall,
            color = c.text2,
            modifier = Modifier.padding(start = spacing.xxs, top = spacing.xxs),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            items(suggestions, key = { it }) { path ->
                val isSelected = path == selectedPath
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(72.dp)
                            .clip(VellutoShapes.Tile)
                            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) c.accent else c.line, VellutoShapes.Tile)
                            .clickable(role = Role.Button) { onSelectExisting(path) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonPicker(
    people: List<PersonOption>,
    selectedId: Long?,
    onPersonChange: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val noneLabel = stringResource(R.string.event_person_none)
    val selectedName = people.firstOrNull { it.id == selectedId }?.name ?: noneLabel

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            modifier =
                Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            label = { Text(stringResource(R.string.event_field_person)) },
            leadingIcon = { Icon(VellutoIcons.Users, contentDescription = null, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                Icon(
                    if (expanded) VellutoIcons.ChevronUp else VellutoIcons.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(noneLabel) },
                onClick = {
                    onPersonChange(null)
                    expanded = false
                },
            )
            people.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        onPersonChange(option.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
