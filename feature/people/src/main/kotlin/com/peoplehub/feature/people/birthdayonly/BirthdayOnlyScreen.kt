package com.peoplehub.feature.people.birthdayonly

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.EmptyView
import com.peoplehub.core.ui.components.ErrorView
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.LoadingView
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.PersonAvatar
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.feature.people.R
import java.time.format.DateTimeFormatter

private val BirthdayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d")

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

    Scaffold(
        topBar = {
            PeopleHubTopBar(
                title = stringResource(R.string.birthday_only_title),
                centered = true,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        BirthdayOnlyContent(state = state, contentPadding = innerPadding, onToggle = viewModel::onToggle)
    }
}

@Composable
private fun BirthdayOnlyContent(
    state: UiState<List<Person>>,
    contentPadding: PaddingValues,
    onToggle: (Person, Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 32.dp,
                start = 20.dp,
                end = 20.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            Text(
                text = stringResource(R.string.birthday_only_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when (state) {
            UiState.Loading -> item(key = "loading") { LoadingView() }
            UiState.Empty ->
                item(key = "empty") {
                    EmptyView(
                        title = stringResource(R.string.birthday_only_empty_title),
                        description = stringResource(R.string.birthday_only_empty_desc),
                    )
                }
            is UiState.Error -> item(key = "error") { ErrorView(message = state.message) }
            is UiState.Success ->
                items(state.data, key = { it.id }) { person ->
                    BirthdayOnlyRow(person = person, onToggle = onToggle)
                }
        }
    }
}

@Composable
private fun BirthdayOnlyRow(person: Person, onToggle: (Person, Boolean) -> Unit) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(initials = person.initials, photoPath = person.photoPath, size = 48.dp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = person.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                person.birthday?.let { CapsLabel(text = it.format(BirthdayFormatter)) }
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = person.birthdayOnly,
                onCheckedChange = { checked -> onToggle(person, checked) },
            )
        }
    }
}
