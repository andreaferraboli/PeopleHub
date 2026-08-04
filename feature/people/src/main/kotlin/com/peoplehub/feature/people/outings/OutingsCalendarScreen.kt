package com.peoplehub.feature.people.outings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.core.domain.model.Outing
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.GhostButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.OutingCard
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.SectionHeader
import com.peoplehub.core.ui.components.TooltipIconButton
import com.peoplehub.core.ui.components.UiStateContent
import com.peoplehub.feature.people.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val DAYS_IN_WEEK = 7

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

    Scaffold(
        topBar = {
            PeopleHubTopBar(
                title = stringResource(R.string.outings_calendar_title),
                navigationIcon = {
                    TooltipIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        description = stringResource(R.string.action_back),
                        onClick = onBack,
                    )
                },
            )
        },
    ) { innerPadding ->
        UiStateContent(
            state = state,
            emptyContent = {
                Text(
                    text = stringResource(R.string.outings_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(innerPadding).padding(20.dp),
                )
            },
        ) { data ->
            val selected = selectedDate
            val shown = selected?.let { data.byDate[it].orEmpty() } ?: data.outings
            LazyColumn(
                contentPadding =
                    PaddingValues(
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = innerPadding.calculateBottomPadding() + 96.dp,
                        start = 20.dp,
                        end = 20.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "calendar") {
                    MonthCalendar(
                        month = month,
                        today = today,
                        selectedDate = selected,
                        byDate = data.byDate,
                        onPreviousMonth = { month = month.minusMonths(1) },
                        onNextMonth = { month = month.plusMonths(1) },
                        onDayClick = { day -> selectedDate = if (selected == day) null else day },
                    )
                }
                item(key = "list-header") {
                    SectionHeader(
                        title =
                            selected?.format(DayTitleFormatter)
                                ?: stringResource(R.string.outings_all_title),
                    )
                }
                if (selected != null) {
                    item(key = "clear-day") {
                        GhostButton(
                            text = stringResource(R.string.outings_show_all),
                            onClick = { selectedDate = null },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (shown.isEmpty()) {
                    item(key = "day-empty") {
                        Text(
                            text = stringResource(R.string.outings_day_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(shown, key = { it.id }) { outing ->
                        OutingCard(outing = outing, onEdit = { onEditOuting(outing.id) })
                    }
                }
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
) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row {
                    TooltipIconButton(
                        icon = Icons.Filled.ChevronLeft,
                        description = stringResource(R.string.outings_previous_month),
                        onClick = onPreviousMonth,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TooltipIconButton(
                        icon = Icons.Filled.ChevronRight,
                        description = stringResource(R.string.outings_next_month),
                        onClick = onNextMonth,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            WeekdayHeaderRow()
            Spacer(Modifier.height(8.dp))
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
private fun WeekdayHeaderRow() {
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
    Row(modifier = Modifier.fillMaxWidth()) {
        weekdays.forEach { res ->
            Text(
                text = stringResource(res),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (0 until rows).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
 * One calendar day. Days with outings get a gold ring (filled when selected) and a small count badge
 * when more than one outing was recorded; empty days are inert.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    count: Int,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val hasOutings = count > 0
    val cellModifier =
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CircleShape)
            .then(
                when {
                    isSelected -> Modifier.background(MaterialTheme.colorScheme.primary)
                    isToday -> Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    else -> Modifier
                },
            ).then(
                if (hasOutings) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier,
            ).then(if (hasOutings) Modifier.clickable(onClick = onClick) else Modifier)
    Box(modifier = cellModifier, contentAlignment = Alignment.Center) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color =
                when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    hasOutings -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
        )
        if (count > 1) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                CapsLabel(
                    text = count.toString(),
                    color =
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
