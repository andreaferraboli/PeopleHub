package com.peoplehub.feature.events.detail

import android.content.res.Configuration
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.core.domain.model.PersonEvent
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.CoverCountdown
import com.peoplehub.core.ui.components.CoverPin
import com.peoplehub.core.ui.components.EventCover
import com.peoplehub.core.ui.components.GlassIconButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.IconTile
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.PersonAvatar
import com.peoplehub.core.ui.components.UiStateContent
import com.peoplehub.core.ui.components.VellutoBackground
import com.peoplehub.core.ui.components.VellutoButton
import com.peoplehub.core.ui.components.VellutoButtonStyle
import com.peoplehub.core.ui.components.coverSceneFor
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.riseIn
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes
import com.peoplehub.feature.events.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

private val EventDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy • HH:mm")

/** Height of the cover that opens the detail screen. */
private val DetailCoverHeight = 320.dp

/** Detail screen for a single event. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onPersonClick: (Long) -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val closed by viewModel.closed.collectAsStateWithLifecycle()

    LaunchedEffect(closed) {
        if (closed) onBack()
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    val spacing = PeopleHubTheme.spacing

    VellutoBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                PeopleHubTopBar(
                    title = "",
                    navigationIcon = {
                        GlassIconButton(
                            icon = VellutoIcons.ArrowLeft,
                            description = stringResource(R.string.action_back),
                            onClick = onBack,
                            modifier = Modifier.padding(start = spacing.s),
                        )
                    },
                    actions = {
                        val data = (state as? UiState.Success)?.data
                        if (data != null) {
                            GlassIconButton(
                                icon = VellutoIcons.Pencil,
                                description = stringResource(R.string.event_detail_edit),
                                onClick = { onEdit(data.event.id) },
                                modifier = Modifier.padding(end = spacing.s),
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            UiStateContent(state = state) { data ->
                EventDetailContent(
                    data = data,
                    contentPadding = innerPadding,
                    onTogglePin = viewModel::onTogglePin,
                    onPersonClick = onPersonClick,
                    onEdit = { onEdit(data.event.id) },
                    onDelete = { showDeleteDialog = true },
                )
            }
        }
    }

    if (showDeleteDialog) {
        val c = PeopleHubTheme.colors
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = c.surface,
            titleContentColor = c.text,
            textContentColor = c.text2,
            icon = { Icon(VellutoIcons.Trash2, contentDescription = null, tint = c.err) },
            title = { Text(stringResource(R.string.event_detail_delete_title)) },
            text = { Text(stringResource(R.string.event_detail_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.onDelete()
                }) { Text(stringResource(R.string.event_detail_delete), color = c.err) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/**
 * Stateless event detail: the event as a tall cover (photo or illustrated scene, countdown and widget
 * pin), then glass sections for the date, the linked person, the description and the actions.
 */
@Composable
internal fun EventDetailContent(
    data: EventDetailData,
    contentPadding: PaddingValues,
    onTogglePin: (Boolean) -> Unit,
    onPersonClick: (Long) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = PeopleHubTheme.spacing
    val event = data.event
    val locale = Locale.getDefault()
    val dateLine = remember(event.dateTime, locale) { event.dateTime.format(EventDateFormatter).replaceFirstChar { it.titlecase(locale) } }
    val isPast = data.signedDays < 0
    val days = data.signedDays.absoluteValue.toInt()
    val countdown =
        if (isPast) {
            CoverCountdown(number = days, unit = stringResource(R.string.event_days_ago))
        } else {
            CoverCountdown(number = days, unit = stringResource(R.string.event_days), prefix = stringResource(R.string.event_in))
        }
    val pinLabel = if (event.pinnedToWidget) stringResource(R.string.event_unpin) else stringResource(R.string.event_pin)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = contentPadding.calculateTopPadding() + spacing.xs,
                    bottom = contentPadding.calculateBottomPadding() + spacing.section,
                    start = spacing.gutter,
                    end = spacing.gutter,
                ),
        verticalArrangement = Arrangement.spacedBy(spacing.ml),
    ) {
        EventCover(
            title = event.title,
            dateLine = dateLine,
            // The cover is the page itself here: tapping it has nothing further to open.
            onClick = null,
            modifier = Modifier.riseIn(0),
            category = event.category,
            countdown = countdown,
            imagePath = event.backgroundImagePath,
            scene = coverSceneFor(event.category, event.title),
            pin = CoverPin(pinned = event.pinnedToWidget, label = pinLabel, onToggle = { onTogglePin(!event.pinnedToWidget) }),
            height = DetailCoverHeight,
            past = isPast,
            highlighted = !isPast,
            drift = true,
        )

        DetailSection(
            label = stringResource(R.string.event_field_date),
            modifier = Modifier.riseIn(1),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                IconTile(icon = VellutoIcons.Calendar)
                Text(
                    text = dateLine,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PeopleHubTheme.colors.text,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val personId = event.personId
        val personName = data.personName
        if (personId != null && personName != null) {
            DetailSection(
                label = stringResource(R.string.event_field_person),
                modifier = Modifier.riseIn(2),
            ) {
                LinkedPersonRow(name = personName, onClick = { onPersonClick(personId) })
            }
        }

        event.description?.takeIf { it.isNotBlank() }?.let { description ->
            DetailSection(
                label = stringResource(R.string.event_detail_description),
                modifier = Modifier.riseIn(3),
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PeopleHubTheme.colors.text2,
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = spacing.xs).riseIn(4),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            VellutoButton(
                text = stringResource(R.string.event_detail_edit),
                onClick = onEdit,
                icon = VellutoIcons.Pencil,
                modifier = Modifier.fillMaxWidth(),
            )
            VellutoButton(
                text = pinLabel,
                onClick = { onTogglePin(!event.pinnedToWidget) },
                style = VellutoButtonStyle.Soft,
                icon = if (event.pinnedToWidget) VellutoIcons.PinOff else VellutoIcons.Pin,
                modifier = Modifier.fillMaxWidth(),
            )
            VellutoButton(
                text = stringResource(R.string.event_detail_delete),
                onClick = onDelete,
                style = VellutoButtonStyle.Danger,
                icon = VellutoIcons.Trash2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A glass section with a caps label over its content. */
@Composable
private fun DetailSection(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val spacing = PeopleHubTheme.spacing
    GlassPanel(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(spacing.xl), verticalArrangement = Arrangement.spacedBy(spacing.m)) {
            CapsLabel(text = label)
            content()
        }
    }
}

/** The person this event belongs to: monogram, name and a chevron, opening their profile. */
@Composable
private fun LinkedPersonRow(
    name: String,
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
                .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        PersonAvatar(initials = name.toInitials(), photoPath = null, size = 48.dp)
        Text(
            text = stringResource(R.string.event_detail_linked_person, name),
            style = MaterialTheme.typography.titleMedium,
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(VellutoIcons.ChevronRight, contentDescription = null, tint = c.text2, modifier = Modifier.size(20.dp))
    }
}

/** Up to two uppercase initials derived from a display name. */
private fun String.toInitials(): String {
    val parts = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return buildString {
        parts.firstOrNull()?.firstOrNull()?.let(::append)
        if (parts.size > 1) parts.last().firstOrNull()?.let(::append)
    }.uppercase().ifEmpty { "?" }
}

@Composable
private fun EventDetailPreview(dark: Boolean) {
    val data =
        EventDetailData(
            event =
                PersonEvent(
                    id = 1,
                    title = "Weekend a Lisbona",
                    dateTime = LocalDateTime.of(2026, 11, 6, 9, 30),
                    description = "Volo alle 9:30 da Linate. Prenotare il tram 28 e la cena al Bairro Alto.",
                    category = "Viaggio",
                    personId = 7,
                    pinnedToWidget = true,
                ),
            personName = "Sofia Bianchi",
            signedDays = 14,
        )
    PeopleHubTheme(darkTheme = dark) {
        VellutoBackground {
            EventDetailContent(
                data = data,
                contentPadding = PaddingValues(top = 64.dp),
                onTogglePin = {},
                onPersonClick = {},
                onEdit = {},
                onDelete = {},
            )
        }
    }
}

@Preview(name = "Event detail, light", widthDp = 390, heightDp = 844)
@Composable
private fun EventDetailLightPreview() {
    EventDetailPreview(dark = false)
}

@Preview(name = "Event detail, dark", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EventDetailDarkPreview() {
    EventDetailPreview(dark = true)
}
