package com.peoplehub.feature.birthdays

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.core.ui.components.DateTile
import com.peoplehub.core.ui.components.GlassIconButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.IconTile
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.VellutoBackground
import com.peoplehub.core.ui.components.VellutoButton
import com.peoplehub.core.ui.components.VellutoButtonStyle
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.lightEdge
import com.peoplehub.core.ui.modifier.riseIn
import com.peoplehub.core.ui.modifier.safeBottomBarPadding
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DraftDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

/** Only the first rows of the form rise in; rows added later simply appear. */
private const val LAST_RISE_INDEX = 6

/**
 * Lets the user add several birthday-only people in one pass: each row is just a name and a date.
 * Saving persists every complete row as a bare birthday (hidden from The Circle, kept in Milestones)
 * and pops back to the calendar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBirthdaysScreen(
    onBack: () -> Unit,
    viewModel: AddBirthdaysViewModel = hiltViewModel(),
) {
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()

    LaunchedEffect(saved) {
        if (saved) onBack()
    }

    val canSave = drafts.any { it.isComplete }
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing

    VellutoBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                PeopleHubTopBar(
                    title = stringResource(R.string.birthdays_add_title),
                    centered = true,
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
                    enabled = canSave,
                    icon = VellutoIcons.Check,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .safeBottomBarPadding()
                            .padding(horizontal = spacing.heroInset, vertical = spacing.l),
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        top = innerPadding.calculateTopPadding() + spacing.s,
                        bottom = innerPadding.calculateBottomPadding() + spacing.l,
                        start = spacing.gutter,
                        end = spacing.gutter,
                    ),
                verticalArrangement = Arrangement.spacedBy(spacing.m),
            ) {
                item(key = "header") {
                    Text(
                        text = stringResource(R.string.birthdays_add_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.text2,
                        modifier = Modifier.padding(horizontal = spacing.xxs).padding(bottom = spacing.xs).riseIn(0),
                    )
                }

                itemsIndexed(drafts, key = { _, draft -> draft.rowId }) { index, draft ->
                    val rise = index + 1
                    BirthdayDraftRow(
                        draft = draft,
                        onNameChange = { viewModel.onNameChange(draft.rowId, it) },
                        onDateChange = { viewModel.onDateChange(draft.rowId, it) },
                        onRemove = { viewModel.onRemoveRow(draft.rowId) },
                        modifier = if (rise <= LAST_RISE_INDEX) Modifier.riseIn(rise) else Modifier,
                    )
                }

                item(key = "add_row") {
                    VellutoButton(
                        text = stringResource(R.string.add_birthday_add_another),
                        onClick = viewModel::onAddRow,
                        style = VellutoButtonStyle.Ghost,
                        icon = VellutoIcons.Plus,
                        modifier = Modifier.fillMaxWidth().padding(top = spacing.xxs),
                    )
                }
            }
        }
    }
}

/**
 * One draft as a glass card: a date tile (or the cake tile while no date is picked), the name field
 * with its remove button, and the date field that opens the picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthdayDraftRow(
    draft: BirthdayDraft,
    onNameChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    var showPicker by remember { mutableStateOf(false) }
    GlassPanel(modifier = modifier.fillMaxWidth(), shape = VellutoShapes.Card) {
        Column(modifier = Modifier.padding(spacing.l), verticalArrangement = Arrangement.spacedBy(spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                val date = draft.date
                if (date != null) {
                    DateTile(
                        month = date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()).trimEnd('.'),
                        day = date.dayOfMonth.toString(),
                    )
                } else {
                    IconTile(icon = VellutoIcons.Cake)
                }
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = onNameChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.add_birthday_name)) },
                )
                GlassIconButton(
                    icon = VellutoIcons.X,
                    description = stringResource(R.string.add_birthday_remove),
                    onClick = onRemove,
                    tint = c.text2,
                )
            }
            DateField(
                value = draft.date?.format(DraftDateFormatter),
                placeholder = stringResource(R.string.add_birthday_pick_date),
                onClick = { showPicker = true },
            )
        }
    }

    if (showPicker) {
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    draft.date
                        ?.atStartOfDay(ZoneOffset.UTC)
                        ?.toInstant()
                        ?.toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onDateChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
            colors = DatePickerDefaults.colors(containerColor = c.surface),
        ) {
            DatePicker(state = pickerState, colors = DatePickerDefaults.colors(containerColor = c.surface))
        }
    }
}

/** A tappable field row on the inner surface that opens a picker: calendar icon + value (or hint). */
@Composable
private fun DateField(
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(VellutoShapes.Inner)
                .background(c.surface2)
                .lightEdge(VellutoShapes.Inner, Color.Transparent, c.line)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = spacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        Icon(VellutoIcons.Calendar, contentDescription = null, tint = c.text2, modifier = Modifier.size(20.dp))
        Text(
            text = value ?: placeholder,
            style = MaterialTheme.typography.bodyLarge,
            color = if (value != null) c.text else c.text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(VellutoIcons.ChevronDown, contentDescription = null, tint = c.text2, modifier = Modifier.size(18.dp))
    }
}
