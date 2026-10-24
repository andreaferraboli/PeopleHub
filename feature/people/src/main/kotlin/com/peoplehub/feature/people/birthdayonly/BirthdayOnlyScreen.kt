package com.peoplehub.feature.people.birthdayonly

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.ui.components.DateTile
import com.peoplehub.core.ui.components.EmptyView
import com.peoplehub.core.ui.components.ErrorView
import com.peoplehub.core.ui.components.GlassIconButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.LoadingView
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.PersonAvatar
import com.peoplehub.core.ui.components.VellutoBackground
import com.peoplehub.core.ui.components.VellutoSwitch
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.riseIn
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.feature.people.R
import java.time.format.DateTimeFormatter

private val MonthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM")

/** Rows past this index enter without the cascade, so a long list does not replay a long delay. */
private const val MAX_RISE_INDEX = 8

/**
 * Lets the user flag which birthdays are "birthday-only" entries: people kept solely for their
 * birthday, hidden from the directory ("The Circle") but still shown in the Milestones calendar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdayOnlyScreen(
    onBack: () -> Unit,
    viewModel: BirthdayOnlyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    VellutoBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                PeopleHubTopBar(
                    title = stringResource(R.string.birthday_only_title),
                    centered = true,
                    navigationIcon = {
                        GlassIconButton(
                            icon = VellutoIcons.ArrowLeft,
                            description = stringResource(R.string.action_back),
                            onClick = onBack,
                            modifier = Modifier.padding(start = PeopleHubTheme.spacing.s),
                        )
                    },
                )
            },
        ) { innerPadding ->
            BirthdayOnlyContent(state = state, contentPadding = innerPadding, onToggle = viewModel::onToggle)
        }
    }
}

/** The stateless list: an explanatory line, then one glass row per person with a birthday. */
@Composable
internal fun BirthdayOnlyContent(
    state: UiState<List<Person>>,
    contentPadding: PaddingValues,
    onToggle: (Person, Boolean) -> Unit,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                top = contentPadding.calculateTopPadding() + spacing.s,
                bottom = contentPadding.calculateBottomPadding() + spacing.section,
                start = spacing.gutter,
                end = spacing.gutter,
            ),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item(key = "header") {
            Text(
                text = stringResource(R.string.birthday_only_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = c.text2,
                modifier = Modifier.padding(start = spacing.xxs, end = spacing.xxs, bottom = spacing.s).riseIn(0),
            )
        }

        when (state) {
            UiState.Loading -> item(key = "loading") { LoadingView() }
            UiState.Empty ->
                item(key = "empty") {
                    EmptyView(
                        title = stringResource(R.string.birthday_only_empty_title),
                        description = stringResource(R.string.birthday_only_empty_desc),
                        icon = VellutoIcons.Cake,
                    )
                }
            is UiState.Error -> item(key = "error") { ErrorView(message = state.message) }
            is UiState.Success ->
                itemsIndexed(state.data, key = { _, person -> person.id }) { index, person ->
                    BirthdayOnlyRow(
                        person = person,
                        onToggle = onToggle,
                        modifier = Modifier.riseIn((index + 1).coerceAtMost(MAX_RISE_INDEX)),
                    )
                }
        }
    }
}

/** A glass row: the birthday as a date tile, the face and name, and the "birthday only" switch. */
@Composable
private fun BirthdayOnlyRow(
    person: Person,
    onToggle: (Person, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    GlassPanel(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.ml),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            person.birthday?.let { birthday ->
                DateTile(
                    month = birthday.format(MonthFormatter).trimEnd('.'),
                    day = birthday.dayOfMonth.toString(),
                )
            }
            PersonAvatar(initials = person.initials, photoPath = person.photoPath, size = 40.dp)
            Text(
                text = person.fullName,
                style = MaterialTheme.typography.titleMedium,
                color = c.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            VellutoSwitch(
                checked = person.birthdayOnly,
                onCheckedChange = { checked -> onToggle(person, checked) },
            )
        }
    }
}
