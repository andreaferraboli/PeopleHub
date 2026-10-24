package com.peoplehub.dashboard

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.R
import com.peoplehub.core.domain.model.CheckInStatus
import com.peoplehub.core.domain.model.CheckInUrgency
import com.peoplehub.core.domain.model.Outing
import com.peoplehub.core.domain.model.OutingAttendee
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.model.PersonEvent
import com.peoplehub.core.domain.model.UpcomingBirthday
import com.peoplehub.core.domain.util.DateCalculations
import com.peoplehub.core.ui.components.BarSegment
import com.peoplehub.core.ui.components.CheckInStatusBadge
import com.peoplehub.core.ui.components.CoverCountdown
import com.peoplehub.core.ui.components.DateTile
import com.peoplehub.core.ui.components.DisplayTitle
import com.peoplehub.core.ui.components.EmptyView
import com.peoplehub.core.ui.components.ErrorView
import com.peoplehub.core.ui.components.EventCover
import com.peoplehub.core.ui.components.FloatingScene
import com.peoplehub.core.ui.components.GlassIconButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.GoldDivider
import com.peoplehub.core.ui.components.KpiRow
import com.peoplehub.core.ui.components.LegendDot
import com.peoplehub.core.ui.components.LoadingView
import com.peoplehub.core.ui.components.LocalNavBarClearance
import com.peoplehub.core.ui.components.NotchButton
import com.peoplehub.core.ui.components.NotchedCard
import com.peoplehub.core.ui.components.OutingCard
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.PersonAvatar
import com.peoplehub.core.ui.components.SceneArt
import com.peoplehub.core.ui.components.SectionHeader
import com.peoplehub.core.ui.components.SegmentBar
import com.peoplehub.core.ui.components.StatusPill
import com.peoplehub.core.ui.components.SurfaceCard
import com.peoplehub.core.ui.components.TextAction
import com.peoplehub.core.ui.components.UiStateContent
import com.peoplehub.core.ui.components.VellutoBackground
import com.peoplehub.core.ui.components.VellutoButton
import com.peoplehub.core.ui.components.VellutoButtonStyle
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.VellutoElevation
import com.peoplehub.core.ui.modifier.radialGlow
import com.peoplehub.core.ui.modifier.riseIn
import com.peoplehub.core.ui.modifier.softShadow
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes
import com.peoplehub.core.ui.theme.statusColorsFor
import com.peoplehub.core.ui.util.RelativeTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.peoplehub.core.ui.R as UiR

private val MonthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
private val EventDateFormatter = DateTimeFormatter.ofPattern("EEE d MMMM · HH:mm", Locale.getDefault())

/** Opacity of the status ring's glow around an urgent person's avatar. */
private const val RING_GLOW_ALPHA = 0.4f

/** Entrance index of the first urgent card (after hero, button and vitality card). */
private const val URGENT_RISE_START = 3

/** Only the first cards cascade in; the rest of the list appears with the scroll. */
private const val MAX_RISE_INDEX = 8

/** Home dashboard ("Reflect"): network vitality, urgent check-ins, milestones, pinned event. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onPersonClick: (Long) -> Unit,
    onEventClick: (Long) -> Unit,
    onSeeAllPeople: () -> Unit,
    onSeeAllBirthdays: () -> Unit,
    onAddPerson: () -> Unit,
    onRecordMeetup: () -> Unit,
    onOpenOutings: () -> Unit,
    onEditOuting: (Long) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val clearance = LocalNavBarClearance.current

    VellutoBackground(secondGlow = true) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            topBar = {
                DashboardTopBar(
                    showCalendar = state is UiState.Success,
                    onOpenOutings = onOpenOutings,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { innerPadding ->
            val stateModifier = Modifier.padding(innerPadding).padding(bottom = clearance)
            UiStateContent(
                state = state,
                loadingContent = { LoadingView(modifier = stateModifier) },
                emptyContent = {
                    EmptyView(
                        title = stringResource(R.string.dashboard_empty_title),
                        description = stringResource(R.string.dashboard_empty_desc),
                        icon = VellutoIcons.Users,
                        modifier = stateModifier,
                        action = {
                            VellutoButton(
                                text = stringResource(R.string.dashboard_empty_cta),
                                onClick = onAddPerson,
                                icon = VellutoIcons.UserPlus,
                                breathing = true,
                            )
                        },
                    )
                },
                errorContent = { message -> ErrorView(message = message, modifier = stateModifier) },
            ) { data ->
                DashboardContent(
                    data = data,
                    contentPadding = innerPadding,
                    onPersonClick = onPersonClick,
                    onEventClick = onEventClick,
                    onSeeAllPeople = onSeeAllPeople,
                    onSeeAllBirthdays = onSeeAllBirthdays,
                    onQuickCheckIn = viewModel::onQuickCheckIn,
                    onRecordMeetup = onRecordMeetup,
                    onOpenOutings = onOpenOutings,
                    onEditOuting = onEditOuting,
                )
            }
        }
    }
}

/** Brand mark + italic serif wordmark, with the outings calendar as a glass action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar(
    showCalendar: Boolean,
    onOpenOutings: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    PeopleHubTopBar(
        title = stringResource(R.string.brand_wordmark),
        showLogo = true,
        scrollBehavior = scrollBehavior,
        actions = {
            if (showCalendar) {
                GlassIconButton(
                    icon = VellutoIcons.CalendarDays,
                    description = stringResource(R.string.dashboard_outings_calendar_cta),
                    onClick = onOpenOutings,
                    modifier = Modifier.padding(end = PeopleHubTheme.spacing.m),
                )
            }
        },
    )
}

/**
 * The stateless body of the home screen, rendered for [UiState.Success]. [today] drives the pinned
 * event's countdown (a parameter so previews and screenshot tests are deterministic).
 */
@Composable
internal fun DashboardContent(
    data: DashboardData,
    contentPadding: PaddingValues,
    onPersonClick: (Long) -> Unit,
    onEventClick: (Long) -> Unit,
    onSeeAllPeople: () -> Unit,
    onSeeAllBirthdays: () -> Unit,
    onQuickCheckIn: (Long) -> Unit,
    onRecordMeetup: () -> Unit,
    onOpenOutings: () -> Unit,
    onEditOuting: (Long) -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    val spacing = PeopleHubTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding =
            PaddingValues(
                top = contentPadding.calculateTopPadding() + spacing.s,
                bottom = contentPadding.calculateBottomPadding() + LocalNavBarClearance.current + 60.dp,
                start = spacing.gutter,
                end = spacing.gutter,
            ),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        item(key = "header") { DashboardHero(onRecordMeetup = onRecordMeetup) }

        item(key = "vitality") {
            VitalityCard(
                data = data,
                onSeeAllPeople = onSeeAllPeople,
                modifier = Modifier.padding(top = spacing.ml).riseIn(2),
            )
        }

        if (data.urgentCheckIns.isNotEmpty()) {
            item(key = "urgent-header") {
                SectionHeader(
                    title = stringResource(R.string.dashboard_urgent_title),
                    actionLabel = stringResource(R.string.dashboard_view_all),
                    onActionClick = onSeeAllPeople,
                    modifier = Modifier.padding(top = spacing.l),
                )
            }
            itemsIndexed(data.urgentCheckIns, key = { _, urgent -> "urgent-${urgent.person.id}" }) { index, urgency ->
                UrgentCheckInCard(
                    urgency = urgency,
                    onPersonClick = onPersonClick,
                    onQuickCheckIn = onQuickCheckIn,
                    modifier = Modifier.riseIn((URGENT_RISE_START + index).coerceAtMost(MAX_RISE_INDEX)),
                )
            }
        }

        item(key = "divider") {
            GoldDivider(
                diamond = true,
                modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.xl),
            )
        }

        if (data.upcomingBirthdays.isNotEmpty()) {
            item(key = "milestones") {
                MilestonesSection(
                    birthdays = data.upcomingBirthdays,
                    onPersonClick = onPersonClick,
                    onSeeAllBirthdays = onSeeAllBirthdays,
                )
            }
        }

        data.pinnedEvent?.let { event ->
            item(key = "pinned") {
                PinnedEventSection(
                    event = event,
                    today = today,
                    onEventClick = onEventClick,
                    modifier = Modifier.padding(top = spacing.l),
                )
            }
        }

        item(key = "outings-divider") {
            GoldDivider(modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.l))
        }

        item(key = "outings-header") {
            SectionHeader(
                title = stringResource(R.string.dashboard_outings_title),
                actionLabel = stringResource(R.string.dashboard_outings_calendar),
                onActionClick = onOpenOutings,
            )
        }

        if (data.recentOutings.isEmpty()) {
            item(key = "outings-empty") {
                Text(
                    text = stringResource(R.string.dashboard_outings_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PeopleHubTheme.colors.text2,
                    modifier = Modifier.padding(horizontal = spacing.xxs),
                )
            }
        } else {
            items(data.recentOutings, key = { "outing-${it.id}" }) { outing ->
                OutingCard(outing = outing, onEdit = { onEditOuting(outing.id) })
            }
        }

        item(key = "outings-calendar-cta") {
            VellutoButton(
                text = stringResource(R.string.dashboard_outings_calendar_cta),
                onClick = onOpenOutings,
                style = VellutoButtonStyle.Ghost,
                icon = VellutoIcons.CalendarDays,
                modifier = Modifier.fillMaxWidth().padding(top = spacing.xxs),
            )
        }
    }
}

/** Two-tone welcome title, supporting line and the breathing "record a meetup" pill. */
@Composable
private fun DashboardHero(onRecordMeetup: () -> Unit) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.heroInset - spacing.gutter).riseIn(0),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            // No user name in the app: the whole greeting takes the italic accent ("Bentornato,
            // *Andrea.*" in the mockup), instead of splitting the word in half.
            val welcome = stringResource(R.string.dashboard_welcome)
            DisplayTitle(text = welcome, accent = welcome)
            Text(
                text = stringResource(R.string.dashboard_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = c.text2,
                modifier = Modifier.widthIn(max = 290.dp).padding(top = spacing.xxs),
            )
        }
        VellutoButton(
            text = stringResource(R.string.group_meetup_title),
            onClick = onRecordMeetup,
            icon = VellutoIcons.Users,
            breathing = true,
            modifier = Modifier.fillMaxWidth().padding(top = spacing.m).riseIn(1),
        )
    }
}

/**
 * "Network vitality" hero card with the notch: the people needing attention as the big accent KPI,
 * their share of the network as a bar, and active connections / upcoming birthdays below. The notch
 * button opens the people list, like the urgent section's "View all".
 */
@Composable
private fun VitalityCard(
    data: DashboardData,
    onSeeAllPeople: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    val attention = stringResource(R.string.dashboard_attention)
    val active = stringResource(R.string.dashboard_active)
    val glowPx = with(LocalDensity.current) { 34.dp.toPx() }
    NotchedCard(
        modifier = modifier,
        decoration = {
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .offset(x = (-70).dp, y = (-90).dp)
                        .size(260.dp)
                        .radialGlow(color = c.glowSoft),
                )
            }
        },
        button = {
            NotchButton(
                icon = VellutoIcons.ArrowUpRight,
                description = stringResource(R.string.dashboard_view_all),
                onClick = onSeeAllPeople,
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.dashboard_vitality),
                style = MaterialTheme.typography.headlineSmall,
                color = c.text,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = VellutoIcons.Activity,
                contentDescription = null,
                tint = c.accentTextSurface,
                modifier = Modifier.size(26.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = spacing.ml),
            horizontalArrangement = Arrangement.spacedBy(spacing.l),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = data.urgentCount.toString(),
                style = PeopleHubTheme.type.kpiHero.copy(shadow = Shadow(color = c.glowStrong, blurRadius = glowPx)),
                color = c.accentTextSurface,
                maxLines = 1,
            )
            Text(
                text = attention,
                style = MaterialTheme.typography.titleMedium,
                color = c.text,
                modifier = Modifier.weight(1f).padding(bottom = spacing.m),
            )
        }
        if (data.peopleCount > 0) {
            val rest = (data.peopleCount - data.urgentCount).coerceAtLeast(0)
            SegmentBar(
                segments =
                    listOf(
                        BarSegment(weight = data.urgentCount.toFloat(), color = c.err),
                        BarSegment(weight = rest.toFloat(), color = c.track),
                    ),
                description = "${data.urgentCount} $attention · ${data.peopleCount} $active",
                modifier = Modifier.padding(top = spacing.ml),
            )
            LegendDot(label = attention, color = c.err, modifier = Modifier.padding(top = spacing.sm))
        }
        KpiRow(
            items =
                listOf(
                    data.peopleCount.toString() to active,
                    data.upcomingBirthdayCount.toString() to stringResource(R.string.dashboard_birthdays),
                ),
            modifier = Modifier.padding(top = spacing.l),
        )
    }
}

/** An urgent person: status-ringed avatar, serif name, recency pill, note and the quick check-in. */
@Composable
private fun UrgentCheckInCard(
    urgency: CheckInUrgency,
    onPersonClick: (Long) -> Unit,
    onQuickCheckIn: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    val person = urgency.person
    val ring = statusColorsFor(urgency.status).ring
    GlassPanel(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onPersonClick(person.id) }
                    .padding(spacing.l),
            verticalArrangement = Arrangement.spacedBy(spacing.ml),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.ml),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PersonAvatar(
                    initials = person.initials,
                    photoPath = person.photoPath,
                    size = 58.dp,
                    ringColor = ring,
                    ringGlow = if (urgency.status == CheckInStatus.NEVER) Color.Transparent else ring.copy(alpha = RING_GLOW_ALPHA),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    Text(
                        text = person.fullName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = c.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (person.tags.isNotEmpty()) {
                        Text(
                            text = person.tags.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.text2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    CheckInStatusBadge(
                        label = RelativeTime.seenLabel(urgency.daysSince),
                        status = urgency.status,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
                }
            }
            if (person.notes.isNotBlank()) {
                Text(
                    text = person.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.text2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            VellutoButton(
                text = stringResource(R.string.action_check_in),
                onClick = { onQuickCheckIn(person.id) },
                style = VellutoButtonStyle.Soft,
                icon = VellutoIcons.Check,
            )
        }
    }
}

/** "Milestones": display title with the floating gift bleeding out of the corner, then the rows card. */
@Composable
private fun MilestonesSection(
    birthdays: List<UpcomingBirthday>,
    onPersonClick: (Long) -> Unit,
    onSeeAllBirthdays: () -> Unit,
) {
    val spacing = PeopleHubTheme.spacing
    Box(modifier = Modifier.fillMaxWidth().padding(top = spacing.s)) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.ml)) {
            Column(
                modifier = Modifier.padding(start = spacing.xxs, top = spacing.xxl).widthIn(max = 220.dp),
            ) {
                Text(
                    text = stringResource(R.string.dashboard_milestones),
                    style = MaterialTheme.typography.displaySmall,
                    color = PeopleHubTheme.colors.text,
                )
                TextAction(
                    label = stringResource(R.string.dashboard_view_all),
                    onClick = onSeeAllBirthdays,
                    modifier = Modifier.offset(x = -spacing.xs),
                )
            }
            SurfaceCard(shape = VellutoShapes.CardLarge, shadow = VellutoElevation.Raised) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(spacing.s),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    birthdays.forEach { birthday -> MilestoneRow(birthday, onPersonClick) }
                }
            }
        }
        FloatingScene(
            art = SceneArt.Gift,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 20.dp, y = (-40).dp),
        )
    }
}

@Composable
private fun MilestoneRow(
    birthday: UpcomingBirthday,
    onPersonClick: (Long) -> Unit,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    val isToday = birthday.daysUntil == 0
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clip(VellutoShapes.Inner)
                .background(if (isToday) c.accentSoft else Color.Transparent)
                .clickable(role = Role.Button) { onPersonClick(birthday.personId) }
                .padding(horizontal = spacing.m, vertical = spacing.s),
        horizontalArrangement = Arrangement.spacedBy(spacing.ml),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateTile(
            month = birthday.nextOccurrence.format(MonthFormatter),
            day = birthday.nextOccurrence.dayOfMonth.toString(),
            highlighted = isToday,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = birthday.fullName,
                style = MaterialTheme.typography.titleMedium,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.milestone_birthday),
                style = MaterialTheme.typography.bodySmall,
                color = c.text2,
                maxLines = 1,
            )
        }
        if (isToday) {
            StatusPill(
                label = stringResource(R.string.dashboard_birthday_today),
                content = c.onAccent,
                container = c.accent,
                dot = false,
                modifier = Modifier.softShadow(c.glowStrong, CircleShape, VellutoElevation.GlowSmall),
            )
        } else {
            Text(
                text = stringResource(R.string.dashboard_birthday_in_days, birthday.daysUntil),
                style = MaterialTheme.typography.labelLarge,
                color = c.text2,
                maxLines = 1,
            )
        }
    }
}

/** "Pinned event": the event as a drifting cover (photo or scene) with the solid pin and countdown. */
@Composable
private fun PinnedEventSection(
    event: PersonEvent,
    today: LocalDate,
    onEventClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = PeopleHubTheme.spacing
    val days = DateCalculations.signedDaysFromToday(event.dateTime.toLocalDate(), today)
    val countdown =
        when {
            days > 0 ->
                CoverCountdown(
                    number = days.toInt(),
                    unit = stringResource(UiR.string.ui_days),
                    prefix = stringResource(UiR.string.ui_in_days),
                )
            days < 0 -> CoverCountdown(number = (-days).toInt(), unit = stringResource(UiR.string.ui_days_ago))
            else -> null
        }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        SectionHeader(title = stringResource(R.string.dashboard_pinned))
        Box(modifier = Modifier.fillMaxWidth()) {
            EventCover(
                title = event.title,
                dateLine = event.dateTime.format(EventDateFormatter),
                onClick = { onEventClick(event.id) },
                supportingLine = if (days == 0L) stringResource(UiR.string.ui_today) else null,
                category = event.category,
                countdown = countdown,
                imagePath = event.backgroundImagePath,
                height = 240.dp,
                highlighted = true,
                drift = true,
            )
            PinnedMark(modifier = Modifier.align(Alignment.TopEnd).padding(spacing.ml))
        }
    }
}

/**
 * The solid accent pin in the cover's corner. Read-only here: the home screen has no unpin action,
 * so it marks the state instead of using [com.peoplehub.core.ui.components.CoverPin]'s toggle.
 */
@Composable
private fun PinnedMark(modifier: Modifier = Modifier) {
    val c = PeopleHubTheme.colors
    Box(
        modifier =
            modifier
                .size(44.dp)
                .softShadow(c.shadow, CircleShape, VellutoElevation.GlowSmall)
                .background(c.accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(VellutoIcons.Pin, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(19.dp))
    }
}

private val PreviewToday: LocalDate = LocalDate.of(2026, 10, 23)

private val PreviewData =
    DashboardData(
        peopleCount = 24,
        urgentCount = 5,
        upcomingBirthdayCount = 3,
        urgentCheckIns =
            listOf(
                CheckInUrgency(
                    Person(
                        id = 1,
                        firstName = "Giulia",
                        lastName = "Ferri",
                        tags = listOf("Università", "Milano"),
                        notes = "Ha cambiato lavoro a settembre, chiederle come va il nuovo team.",
                    ),
                    42,
                    CheckInStatus.OVERDUE,
                ),
                CheckInUrgency(Person(id = 2, firstName = "Davide", lastName = "Russo", tags = listOf("Calcetto")), 19, CheckInStatus.DUE),
            ),
        upcomingBirthdays =
            listOf(
                UpcomingBirthday(
                    personId = 3,
                    fullName = "Sofia Bianchi",
                    photoPath = null,
                    birthday = LocalDate.of(1997, 10, 23),
                    nextOccurrence = PreviewToday,
                    daysUntil = 0,
                    turningAge = 29,
                ),
                UpcomingBirthday(
                    personId = 4,
                    fullName = "Luca Martini",
                    photoPath = null,
                    birthday = LocalDate.of(1993, 10, 30),
                    nextOccurrence = LocalDate.of(2026, 10, 30),
                    daysUntil = 7,
                    turningAge = 33,
                ),
            ),
        pinnedEvent =
            PersonEvent(
                id = 5,
                title = "Weekend a Lisbona",
                dateTime = LocalDateTime.of(2026, 11, 6, 7, 15),
                category = "Viaggio",
                pinnedToWidget = true,
            ),
        recentOutings =
            listOf(
                Outing(
                    id = 6,
                    date = PreviewToday,
                    timestamp = Instant.EPOCH,
                    note = "Colazione da Marchesi prima del lavoro, abbiamo chiuso i biglietti per Lisbona.",
                    attendees =
                        listOf(
                            OutingAttendee(checkInId = 7, personId = 8, fullName = "Marta Colombo", initials = "MC", photoPath = null),
                        ),
                    checkInIds = listOf(7),
                ),
            ),
    )

@Composable
private fun DashboardPreviewBody(dark: Boolean) {
    PeopleHubTheme(darkTheme = dark) {
        VellutoBackground(secondGlow = true) {
            DashboardContent(
                data = PreviewData,
                contentPadding = PaddingValues(0.dp),
                onPersonClick = {},
                onEventClick = {},
                onSeeAllPeople = {},
                onSeeAllBirthdays = {},
                onQuickCheckIn = {},
                onRecordMeetup = {},
                onOpenOutings = {},
                onEditOuting = {},
                today = PreviewToday,
            )
        }
    }
}

@Preview(name = "Riflessione, chiaro", widthDp = 390, heightDp = 844)
@Composable
private fun DashboardLightPreview() {
    DashboardPreviewBody(dark = false)
}

@Preview(name = "Riflessione, scuro", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DashboardDarkPreview() {
    DashboardPreviewBody(dark = true)
}
