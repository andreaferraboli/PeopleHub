package com.peoplehub.dashboard

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.R
import com.peoplehub.core.domain.model.CheckInStatus
import com.peoplehub.core.domain.model.CheckInUrgency
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.model.PersonEvent
import com.peoplehub.core.domain.model.UpcomingBirthday
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.CheckInStatusBadge
import com.peoplehub.core.ui.components.EmptyView
import com.peoplehub.core.ui.components.GhostButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.GoldDivider
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.PersonAvatar
import com.peoplehub.core.ui.components.PrimaryGoldButton
import com.peoplehub.core.ui.components.SectionHeader
import com.peoplehub.core.ui.components.UiStateContent
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.util.RelativeTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MonthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())

/** Home dashboard ("Reflect"): network vitality, urgent check-ins, milestones, pinned event. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onPersonClick: (Long) -> Unit,
    onEventClick: (Long) -> Unit,
    onSeeAllPeople: () -> Unit,
    onSeeAllBirthdays: () -> Unit,
    onAddPerson: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PeopleHubTopBar(
                title = stringResource(R.string.brand_wordmark),
                centered = true,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        UiStateContent(
            state = state,
            emptyContent = {
                EmptyView(
                    title = stringResource(R.string.dashboard_empty_title),
                    description = stringResource(R.string.dashboard_empty_desc),
                    action = {
                        PrimaryGoldButton(
                            text = stringResource(R.string.dashboard_empty_cta),
                            onClick = onAddPerson,
                        )
                    },
                )
            },
        ) { data ->
            DashboardContent(
                data = data,
                contentPadding = innerPadding,
                onPersonClick = onPersonClick,
                onEventClick = onEventClick,
                onSeeAllPeople = onSeeAllPeople,
                onSeeAllBirthdays = onSeeAllBirthdays,
                onQuickCheckIn = viewModel::onQuickCheckIn,
            )
        }
    }
}

@Composable
private fun DashboardContent(
    data: DashboardData,
    contentPadding: PaddingValues,
    onPersonClick: (Long) -> Unit,
    onEventClick: (Long) -> Unit,
    onSeeAllPeople: () -> Unit,
    onSeeAllBirthdays: () -> Unit,
    onQuickCheckIn: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
            start = 20.dp,
            end = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "header") {
            Column {
                Text(
                    text = stringResource(R.string.dashboard_welcome),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.dashboard_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        item(key = "vitality") { VitalityPanel(data) }

        if (data.urgentCheckIns.isNotEmpty()) {
            item(key = "urgent-header") {
                SectionHeader(
                    title = stringResource(R.string.dashboard_urgent_title),
                    actionLabel = stringResource(R.string.dashboard_view_all),
                    onActionClick = onSeeAllPeople,
                )
            }
            items(data.urgentCheckIns, key = { "urgent-${it.person.id}" }) { urgency ->
                UrgentCheckInCard(urgency, onPersonClick, onQuickCheckIn)
            }
        }

        item(key = "divider") { GoldDivider() }

        if (data.upcomingBirthdays.isNotEmpty()) {
            item(key = "milestones-header") {
                SectionHeader(
                    title = stringResource(R.string.dashboard_milestones),
                    actionLabel = stringResource(R.string.dashboard_view_all),
                    onActionClick = onSeeAllBirthdays,
                )
            }
            items(data.upcomingBirthdays, key = { "bday-${it.personId}" }) { birthday ->
                MilestoneRow(birthday, onPersonClick)
            }
        }

        data.pinnedEvent?.let { event ->
            item(key = "pinned") { PinnedEventCard(event, onEventClick) }
        }
    }
}

@Composable
private fun VitalityPanel(data: DashboardData) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.dashboard_vitality),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            StatRow(stringResource(R.string.dashboard_active), data.peopleCount, emphasized = false)
            StatRow(stringResource(R.string.dashboard_birthdays), data.upcomingBirthdayCount, emphasized = false)
            StatRow(stringResource(R.string.dashboard_attention), data.urgentCount, emphasized = true)
        }
    }
}

@Composable
private fun StatRow(label: String, value: Int, emphasized: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun UrgentCheckInCard(urgency: CheckInUrgency, onPersonClick: (Long) -> Unit, onQuickCheckIn: (Long) -> Unit) {
    GlassPanel(modifier = Modifier.fillMaxWidth().clickable { onPersonClick(urgency.person.id) }) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(initials = urgency.person.initials, photoPath = urgency.person.photoPath, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(urgency.person.fullName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                CheckInStatusBadge(label = RelativeTime.seenLabel(urgency.daysSince), status = urgency.status)
            }
            GhostButton(text = stringResource(R.string.action_check_in), onClick = { onQuickCheckIn(urgency.person.id) })
        }
    }
}

@Composable
private fun MilestoneRow(birthday: UpcomingBirthday, onPersonClick: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPersonClick(birthday.personId) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CapsLabel(text = birthday.nextOccurrence.format(MonthFormatter))
                Text(
                    text = birthday.nextOccurrence.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            imageVector = Icons.Outlined.CardGiftcard,
            contentDescription = stringResource(R.string.milestone_birthday),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(birthday.fullName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = milestoneSubtitle(birthday),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun milestoneSubtitle(birthday: UpcomingBirthday): String =
    if (birthday.daysUntil == 0) {
        stringResource(R.string.dashboard_birthday_today)
    } else {
        stringResource(R.string.dashboard_birthday_in_days, birthday.daysUntil)
    }

@Composable
private fun PinnedEventCard(event: PersonEvent, onEventClick: (Long) -> Unit) {
    GlassPanel(modifier = Modifier.fillMaxWidth().clickable { onEventClick(event.id) }) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Celebration,
                    contentDescription = stringResource(R.string.milestone_event),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                CapsLabel(text = stringResource(R.string.dashboard_pinned))
            }
            Spacer(Modifier.size(8.dp))
            event.category?.let {
                Text(it.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = event.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Preview(name = "Phone", device = "spec:width=411dp,height=891dp")
@Preview(name = "Tablet", device = "spec:width=800dp,height=1280dp")
@Composable
private fun DashboardPreview() {
    PeopleHubTheme {
        DashboardContent(
            data = DashboardData(
                peopleCount = 12,
                urgentCount = 3,
                upcomingBirthdayCount = 2,
                urgentCheckIns = listOf(
                    CheckInUrgency(Person(id = 1, firstName = "Eleanor", lastName = "Vance"), 40, CheckInStatus.OVERDUE),
                ),
                upcomingBirthdays = listOf(
                    UpcomingBirthday(
                        personId = 2,
                        fullName = "Marcus Thorne",
                        photoPath = null,
                        birthday = LocalDate.of(1990, 10, 12),
                        nextOccurrence = LocalDate.of(2026, 10, 12),
                        daysUntil = 5,
                        turningAge = 36,
                    ),
                ),
                pinnedEvent = PersonEvent(id = 3, title = "The Annual Foundation Gala", dateTime = LocalDateTime.of(2026, 11, 15, 19, 0)),
            ),
            contentPadding = PaddingValues(0.dp),
            onPersonClick = {},
            onEventClick = {},
            onSeeAllPeople = {},
            onSeeAllBirthdays = {},
            onQuickCheckIn = {},
        )
    }
}
