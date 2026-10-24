package com.peoplehub.feature.people.outings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.core.domain.model.Outing
import com.peoplehub.core.ui.components.DisplayTitle
import com.peoplehub.core.ui.components.EmptyView
import com.peoplehub.core.ui.components.GlassIconButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.OutingCard
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.SectionHeader
import com.peoplehub.core.ui.components.UiStateContent
import com.peoplehub.core.ui.components.VellutoBackground
import com.peoplehub.core.ui.components.VellutoButton
import com.peoplehub.core.ui.components.VellutoButtonStyle
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.ShadowSpec
import com.peoplehub.core.ui.modifier.VellutoElevation
import com.peoplehub.core.ui.modifier.riseIn
import com.peoplehub.core.ui.modifier.softShadow
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes
import com.peoplehub.feature.people.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val DAYS_IN_WEEK = 7

/** Outing cards past this index enter without the cascade. */
private const val MAX_RISE_INDEX = 8

private val DayTitleFormatter: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.getDefault())

/**
 * The outings history on a calendar: a month grid marking every day something was recorded, and the
 * outings of the selected day (or the whole history, when no day is selected) as editable cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutingsCalendarScreen(
    onBack: () -> Unit,
    onEditOuting: (Long) -> Unit,
    viewModel: OutingsCalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today = viewModel.today
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val spacing = PeopleHubTheme.spacing

    VellutoBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                PeopleHubTopBar(
                    title = stringResource(R.string.outings_calendar_title),
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
        ) { innerPadding ->
            UiStateContent(
                state = state,
                emptyContent = {
                    EmptyView(
                        title = stringResource(R.string.outings_calendar_title),
                        description = stringResource(R.string.outings_empty),
                        icon = VellutoIcons.CalendarDays,
                        modifier = Modifier.padding(innerPadding),
                    )
                },
            ) { data ->
                OutingsCalendarContent(
                    data = data,
                    month = month,
                    today = today,
                    selectedDate = selectedDate,
                    contentPadding = innerPadding,
                    onPreviousMonth = { month = month.minusMonths(1) },
                    onNextMonth = { month = month.plusMonths(1) },
                    onSelectDate = { selectedDate = it },
                    onEditOuting = onEditOuting,
                )
            }
        }
    }
}

/**
 * The stateless body: the month grid in a glass panel, a serif header for the day (or "all outings")
 * and the matching outing cards.
 */
@Composable
internal fun OutingsCalendarContent(
    data: OutingsCalendarData,
    month: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate?,
    contentPadding: PaddingValues,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate?) -> Unit,
    onEditOuting: (Long) -> Unit,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    val shown = selectedDate?.let { data.byDate[it].orEmpty() } ?: data.outings
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                top = contentPadding.calculateTopPadding() + spacing.s,
                bottom = contentPadding.calculateBottomPadding() + spacing.section,
                start = spacing.gutter,
                end = spacing.gutter,
            ),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        item(key = "calendar") {
            MonthCalendar(
                month = month,
                today = today,
                selectedDate = selectedDate,
                byDate = data.byDate,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onDayClick = { day -> onSelectDate(if (selectedDate == day) null else day) },
                modifier = Modifier.riseIn(0),
            )
        }
        item(key = "list-header") {
            SectionHeader(
                title =
                    selectedDate?.format(DayTitleFormatter)?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                        ?: stringResource(R.string.outings_all_title),
                count = shown.size.toString(),
                modifier = Modifier.padding(top = spacing.s).riseIn(1),
            )
        }
        if (selectedDate != null) {
            item(key = "clear-day") {
                VellutoButton(
                    text = stringResource(R.string.outings_show_all),
                    onClick = { onSelectDate(null) },
                    style = VellutoButtonStyle.Ghost,
                    icon = VellutoIcons.List,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (shown.isEmpty()) {
            item(key = "day-empty") {
                Text(
                    text = stringResource(R.string.outings_day_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.text2,
                    modifier = Modifier.padding(horizontal = spacing.xxs),
                )
            }
        } else {
            itemsIndexed(shown, key = { _, outing -> outing.id }) { index, outing ->
                OutingCard(
                    outing = outing,
                    onEdit = { onEditOuting(outing.id) },
                    modifier = Modifier.riseIn((index + 2).coerceAtMost(MAX_RISE_INDEX)),
                )
            }
        }
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate?,
    byDate: Map<LocalDate, List<Outing>>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = PeopleHubTheme.spacing
    GlassPanel(modifier = modifier.fillMaxWidth(), shape = VellutoShapes.CardLarge, shadow = VellutoElevation.Raised) {
        // Narrow side padding and no gaps between columns keep every day cell at least 48dp wide.
        Column(modifier = Modifier.padding(horizontal = spacing.s, vertical = spacing.l)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
            ) {
                val monthName =
                    month.month
                        .getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
                        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
                DisplayTitle(
                    text = "$monthName ${month.year}",
                    accent = month.year.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                GlassIconButton(
                    icon = VellutoIcons.ChevronLeft,
                    description = stringResource(R.string.outings_previous_month),
                    onClick = onPreviousMonth,
                )
                GlassIconButton(
                    icon = VellutoIcons.ChevronRight,
                    description = stringResource(R.string.outings_next_month),
                    onClick = onNextMonth,
                    modifier = Modifier.padding(end = spacing.xs),
                )
            }
            WeekdayHeaderRow(modifier = Modifier.padding(top = spacing.l, bottom = spacing.xs))
            MonthDayGrid(
                month = month,
                today = today,
                selectedDate = selectedDate,
                byDate = byDate,
                onDayClick = onDayClick,
            )
        }
    }
}

@Composable
private fun WeekdayHeaderRow(modifier: Modifier = Modifier) {
    // Monday-first, matching the grid below.
    val weekdays =
        listOf(
            R.string.outings_weekday_monday,
            R.string.outings_weekday_tuesday,
            R.string.outings_weekday_wednesday,
            R.string.outings_weekday_thursday,
            R.string.outings_weekday_friday,
            R.string.outings_weekday_saturday,
            R.string.outings_weekday_sunday,
        )
    Row(modifier = modifier.fillMaxWidth()) {
        weekdays.forEach { res ->
            Text(
                text = stringResource(res).uppercase(),
                style = PeopleHubTheme.type.tileCaps,
                color = PeopleHubTheme.colors.text2,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthDayGrid(
    month: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate?,
    byDate: Map<LocalDate, List<Outing>>,
    onDayClick: (LocalDate) -> Unit,
) {
    // `dayOfWeek.value` is 1 for Monday, so the offset of a Monday-first grid is simply value - 1.
    val firstDayOffset = month.atDay(1).dayOfWeek.value - 1
    val cells = firstDayOffset + month.lengthOfMonth()
    val rows = (cells + DAYS_IN_WEEK - 1) / DAYS_IN_WEEK
    Column {
        (0 until rows).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                (0 until DAYS_IN_WEEK).forEach { col ->
                    val dayOfMonth = row * DAYS_IN_WEEK + col - firstDayOffset + 1
                    Box(modifier = Modifier.weight(1f)) {
                        if (dayOfMonth in 1..month.lengthOfMonth()) {
                            val date = month.atDay(dayOfMonth)
                            DayCell(
                                date = date,
                                count = byDate[date].orEmpty().size,
                                isToday = date == today,
                                isSelected = date == selectedDate,
                                onClick = { onDayClick(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One calendar day. Today is the solid accent disc; the selected day is inverted (text-coloured disc);
 * days with outings sit on a soft accent disc with a marker under the number (a dot, or the count when
 * more than one outing was recorded). Only days with outings are tappable; empty days are inert.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    count: Int,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val c = PeopleHubTheme.colors
    val hasOutings = count > 0
    val (fill, content) =
        when {
            isSelected -> c.text to c.bg
            isToday -> c.accent to c.onAccent
            hasOutings -> c.accentSoft to c.accentOnSoft
            else -> Color.Transparent to c.text
        }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
                .then(if (hasOutings) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (isToday && !isSelected) {
                            Modifier.softShadow(c.glowSoft, CircleShape, ShadowSpec(14.dp, 4.dp))
                        } else {
                            Modifier
                        },
                    ).background(fill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (hasOutings || isToday) FontWeight.SemiBold else FontWeight.Normal,
                            fontFeatureSettings = "tnum",
                        ),
                    color = content,
                )
                if (count > 1) {
                    Text(text = count.toString(), style = PeopleHubTheme.type.tileCaps, color = content)
                } else if (hasOutings) {
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .size(5.dp)
                            .background(content, CircleShape),
                    )
                }
            }
        }
    }
}
