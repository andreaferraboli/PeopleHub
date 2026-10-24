package com.peoplehub.feature.reminders.list

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.core.domain.model.ReminderCategory
import com.peoplehub.core.ui.components.AccentFab
import com.peoplehub.core.ui.components.AmbientGlow
import com.peoplehub.core.ui.components.CategoryChip
import com.peoplehub.core.ui.components.DayCountDisplay
import com.peoplehub.core.ui.components.EmptyView
import com.peoplehub.core.ui.components.ErrorView
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.Hairline
import com.peoplehub.core.ui.components.LoadingView
import com.peoplehub.core.ui.components.LocalNavBarClearance
import com.peoplehub.core.ui.components.NotchButton
import com.peoplehub.core.ui.components.NotchedCard
import com.peoplehub.core.ui.components.PersonAvatar
import com.peoplehub.core.ui.components.SceneArt
import com.peoplehub.core.ui.components.ScreenHero
import com.peoplehub.core.ui.components.StatusPill
import com.peoplehub.core.ui.components.SurfaceCard
import com.peoplehub.core.ui.components.TagChip
import com.peoplehub.core.ui.components.TextAction
import com.peoplehub.core.ui.components.TooltipIconButton
import com.peoplehub.core.ui.components.VellutoBackground
import com.peoplehub.core.ui.components.VellutoButton
import com.peoplehub.core.ui.components.VellutoButtonStyle
import com.peoplehub.core.ui.components.VellutoSwitch
import com.peoplehub.core.ui.components.accentTitle
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.ShadowSpec
import com.peoplehub.core.ui.modifier.VellutoElevation
import com.peoplehub.core.ui.modifier.lightEdge
import com.peoplehub.core.ui.modifier.riseIn
import com.peoplehub.core.ui.modifier.softShadow
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes
import com.peoplehub.feature.reminders.R
import com.peoplehub.feature.reminders.labelRes
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue
import com.peoplehub.core.ui.R as UiR

/** Short "last done on" date, e.g. 4 Aug 2026. */
private val ReminderDoneDateFormat: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

/** Time of day of a tap in the done history, e.g. 18:42. */
private val ReminderDoneTimeFormat: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

/** Short weekday of the week strip, e.g. "ven" / "Fri". */
private val WeekdayFormat: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

/** Days shown by the week strip, today included. */
private const val WEEK_DAYS = 7

/** Most dots a day of the week strip draws; more reminders on one day still show this many. */
private const val MAX_DAY_DOTS = 3

/** Most taps drawn as dots on the hero card, oldest faintest. */
private const val MAX_HISTORY_DOTS = 7
private const val HISTORY_DOT_MIN_ALPHA = 0.35f

/** Cards that still cascade in after the hero, strip, chips and hero card (riseIn indices 4..8). */
private const val CASCADE_CARDS = 5
private const val FIRST_CARD_RISE_INDEX = 4

/** Stateful entry point for the global reminders list. */
@Composable
fun RemindersListScreen(
    onAddReminder: () -> Unit,
    onEditReminder: (Long) -> Unit,
    onOpenScience: () -> Unit,
    onPersonClick: (Long) -> Unit,
    viewModel: RemindersListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RemindersListContent(
        state = state,
        onAddReminder = onAddReminder,
        onOpenScience = onOpenScience,
        onCategoryChange = viewModel::onCategoryChange,
        onReminderClick = onEditReminder,
        onPersonClick = onPersonClick,
        onToggleEnabled = viewModel::onToggleEnabled,
        onDelete = viewModel::onDelete,
        onMarkDone = viewModel::onMarkDone,
    )
}

/**
 * Stateless Promemoria tab: hero with the hourglass, the next-seven-days strip, the category filters,
 * the most urgent due reminder as the dark notched hero card, then every other reminder as a card.
 * Draws under the floating navigation bar, so the list and the FAB clear [LocalNavBarClearance].
 */
@Composable
internal fun RemindersListContent(
    state: RemindersScreenState,
    onAddReminder: () -> Unit,
    onOpenScience: () -> Unit,
    onCategoryChange: (ReminderCategory?) -> Unit,
    onReminderClick: (Long) -> Unit,
    onPersonClick: (Long) -> Unit,
    onToggleEnabled: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onMarkDone: (Long) -> Unit,
) {
    val spacing = PeopleHubTheme.spacing
    val clearance = LocalNavBarClearance.current
    val reminders = (state.listState as? UiState.Success)?.data
    VellutoBackground {
        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                AccentFab(
                    icon = VellutoIcons.Plus,
                    description = stringResource(R.string.reminders_add),
                    onClick = onAddReminder,
                    modifier = Modifier.padding(bottom = clearance),
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        top = innerPadding.calculateTopPadding(),
                        bottom = innerPadding.calculateBottomPadding() + clearance + 60.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(spacing.ml),
            ) {
                item(key = "hero") {
                    ScreenHero(
                        title = stringResource(R.string.reminders_title),
                        eyebrow = heroEyebrow(reminders),
                        subtitle = stringResource(R.string.reminders_subtitle),
                        scene = SceneArt.Hourglass,
                        sceneOffsetX = 0.dp,
                        sceneOffsetY = 58.dp,
                        subtitleMaxWidth = 262.dp,
                    ) {
                        ScienceLink(onClick = onOpenScience)
                    }
                }

                if (!reminders.isNullOrEmpty()) {
                    item(key = "week") {
                        WeekStrip(
                            reminders = reminders,
                            today = reminders.first().today,
                            modifier = Modifier.padding(horizontal = spacing.gutter).riseIn(1),
                        )
                    }
                }

                item(key = "category-filter") {
                    CategoryFilterRow(
                        selected = state.category,
                        reminders = reminders,
                        onCategoryChange = onCategoryChange,
                        modifier = Modifier.riseIn(2),
                    )
                }

                remindersListBody(
                    listState = state.listState,
                    onReminderClick = onReminderClick,
                    onPersonClick = onPersonClick,
                    onToggleEnabled = onToggleEnabled,
                    onDelete = onDelete,
                    onMarkDone = onMarkDone,
                )
            }
        }
    }
}

/** "2 in scadenza" when something is due, otherwise the brand wordmark. */
@Composable
private fun heroEyebrow(reminders: List<ReminderListItem>?): String {
    val dueCount = reminders?.count { it.isDue && it.enabled } ?: 0
    return if (dueCount > 0) {
        "$dueCount ${stringResource(R.string.reminder_due)}"
    } else {
        stringResource(R.string.brand_wordmark)
    }
}

/** The accent "why an irregular cadence?" link, its help glyph in a small glass circle. */
@Composable
private fun ScienceLink(onClick: () -> Unit) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    Row(
        modifier =
            Modifier
                .heightIn(min = spacing.touchTarget)
                .clip(VellutoShapes.Pill)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(end = spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .background(c.glass, CircleShape)
                    .lightEdge(CircleShape, highlight = c.hi, border = c.accentEdge),
            contentAlignment = Alignment.Center,
        ) {
            Icon(VellutoIcons.CircleHelp, contentDescription = null, tint = c.accentText, modifier = Modifier.size(16.dp))
        }
        Text(
            text = stringResource(R.string.reminders_why),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = c.accentText,
        )
    }
}

/**
 * The next seven days as a glass strip: today in solid accent, one dot per active reminder falling
 * on that day (overdue ones count on today), read straight off each item's days-until.
 */
@Composable
private fun WeekStrip(
    reminders: List<ReminderListItem>,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val counts =
        remember(reminders) {
            IntArray(WEEK_DAYS).also { perDay ->
                reminders
                    .filter { it.enabled }
                    .forEach { reminder ->
                        val day = reminder.daysUntil.coerceAtLeast(0L)
                        if (day < WEEK_DAYS) perDay[day.toInt()]++
                    }
            }
        }
    GlassPanel(modifier = modifier.fillMaxWidth(), shape = VellutoShapes.CardSmall, shadow = null) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(PeopleHubTheme.spacing.s),
            horizontalArrangement = Arrangement.spacedBy(PeopleHubTheme.spacing.xxs),
        ) {
            repeat(WEEK_DAYS) { offset ->
                WeekDay(
                    day = today.plusDays(offset.toLong()),
                    count = counts[offset],
                    isToday = offset == 0,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WeekDay(
    day: LocalDate,
    count: Int,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val shape = VellutoShapes.DateTile
    val content = if (isToday) c.onAccent else c.text
    val muted = if (isToday) c.onAccent else c.text2
    val dot = if (isToday) c.onAccent else c.accentText
    Column(
        modifier =
            modifier
                .height(66.dp)
                .then(
                    if (isToday) {
                        Modifier.softShadow(c.glowStrong, shape, ShadowSpec(blur = 20.dp, offsetY = 8.dp)).background(c.accent, shape)
                    } else {
                        Modifier
                    },
                ).semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Text(
            text =
                if (isToday) {
                    stringResource(UiR.string.ui_today)
                } else {
                    day.format(WeekdayFormat).replaceFirstChar { it.titlecase(Locale.getDefault()) }
                },
            style = MaterialTheme.typography.labelSmall,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Text(
            text = day.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = content,
        )
        Row(modifier = Modifier.height(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(count.coerceAtMost(MAX_DAY_DOTS)) {
                Box(Modifier.size(5.dp).background(dot, CircleShape))
            }
        }
    }
}

/**
 * The category filters as solid-accent pills with their icon. Counts come from the list on screen:
 * every chip has one while the list is unfiltered, only the selected chip once a category is picked
 * (the others' reminders are not loaded then).
 */
@Composable
private fun CategoryFilterRow(
    selected: ReminderCategory?,
    reminders: List<ReminderListItem>?,
    onCategoryChange: (ReminderCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = PeopleHubTheme.spacing
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        items(categoryOptions(), key = { it?.name ?: "all" }) { option ->
            val count =
                when {
                    reminders == null -> null
                    selected == null && option == null -> reminders.size
                    selected == null -> reminders.count { it.category == option }
                    option == selected -> reminders.size
                    else -> null
                }
            TagChip(
                label =
                    if (option == null) {
                        stringResource(R.string.reminder_filter_all)
                    } else {
                        stringResource(option.labelRes())
                    },
                selected = selected == option,
                onClick = { onCategoryChange(option) },
                count = count?.toString(),
                icon = option?.icon(),
            )
        }
    }
}

private fun LazyListScope.remindersListBody(
    listState: UiState<List<ReminderListItem>>,
    onReminderClick: (Long) -> Unit,
    onPersonClick: (Long) -> Unit,
    onToggleEnabled: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onMarkDone: (Long) -> Unit,
) {
    when (listState) {
        UiState.Loading -> item(key = "loading") { StateBox { LoadingView() } }
        UiState.Empty ->
            item(key = "empty") {
                StateBox {
                    EmptyView(
                        title = stringResource(R.string.reminders_empty_title),
                        description = stringResource(R.string.reminders_empty_desc),
                    )
                }
            }
        is UiState.Error -> item(key = "error") { StateBox { ErrorView(message = listState.message) } }
        is UiState.Success -> {
            val hero = listState.data.filter { it.isDue && it.enabled }.minByOrNull { it.daysUntil }
            val rest = if (hero == null) listState.data else listState.data.filterNot { it.id == hero.id }
            if (hero != null) {
                item(key = "due-hero-${hero.id}") {
                    DueHeroCard(
                        reminder = hero,
                        onClick = { onReminderClick(hero.id) },
                        onPersonClick = { onPersonClick(hero.personId) },
                        onToggleEnabled = { onToggleEnabled(hero.id, !hero.enabled) },
                        onDelete = { onDelete(hero.id) },
                        onMarkDone = { onMarkDone(hero.id) },
                        modifier = Modifier.padding(top = 8.dp).riseIn(3),
                    )
                }
            }
            itemsIndexed(rest, key = { _, reminder -> reminder.id }) { index, reminder ->
                ReminderCard(
                    reminder = reminder,
                    onClick = { onReminderClick(reminder.id) },
                    onPersonClick = { onPersonClick(reminder.personId) },
                    onToggleEnabled = { onToggleEnabled(reminder.id, !reminder.enabled) },
                    onDelete = { onDelete(reminder.id) },
                    onMarkDone = { onMarkDone(reminder.id) },
                    modifier =
                        if (index < CASCADE_CARDS) Modifier.riseIn(FIRST_CARD_RISE_INDEX + index) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun StateBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
        content()
    }
}

/**
 * The most urgent due reminder as the always-dark notched hero: category and "due" pills, the title
 * in big serif with its last word in italic accent, the person, the last time it was done, the
 * recent taps as dots, and the round "done" button sitting in the notch.
 */
@Composable
private fun DueHeroCard(
    reminder: ReminderListItem,
    onClick: () -> Unit,
    onPersonClick: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
    onMarkDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    val doneLabel = stringResource(if (reminder.doneToday) R.string.reminder_done_again else R.string.reminder_mark_done)
    var showHistory by remember { mutableStateOf(false) }
    val fill = remember(c.heroBg) { SolidColor(c.heroBg) }
    NotchedCard(
        modifier = modifier.padding(horizontal = spacing.gutter),
        buttonSize = 68.dp,
        buttonCenterFromEnd = 58.dp,
        background = fill,
        border = c.accentEdge,
        contentColor = c.onMedia,
        decoration = {
            AmbientGlow(
                size = 300.dp,
                color = c.glowHero,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 90.dp, y = (-110).dp),
            )
        },
        button = {
            NotchButton(
                icon = VellutoIcons.Check,
                description = doneLabel,
                onClick = onMarkDone,
                caption = doneLabel,
                size = 68.dp,
                glow = c.glowHero,
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(VellutoShapes.Inner).clickable(onClick = onClick),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryChip(
                    label = stringResource(reminder.category.labelRes()),
                    onMedia = true,
                    icon = reminder.category.icon(),
                )
                Box(Modifier.weight(1f))
                StatusPill(label = stringResource(R.string.reminder_due), content = c.onAccent, container = c.accent)
            }
            Text(
                text = remember(reminder.title, c.accentOnHero) { accentTitle(reminder.title, c.accentOnHero) },
                style = MaterialTheme.typography.displayMedium,
                color = c.onMedia,
                modifier = Modifier.padding(top = spacing.xs),
            )
            ReminderPersonRow(
                reminder = reminder,
                onPersonClick = onPersonClick,
                onToggleEnabled = onToggleEnabled,
                onDelete = onDelete,
                avatarSize = 34.dp,
                onHero = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                Icon(VellutoIcons.Clock, contentDescription = null, tint = c.onMediaMuted, modifier = Modifier.size(16.dp))
                Text(text = doneSummary(reminder), style = MaterialTheme.typography.bodySmall, color = c.onMediaMuted)
            }
            if (reminder.doneHistory.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    HistoryDots(reminder.doneHistory)
                    TextAction(
                        label = historyToggleLabel(showHistory),
                        onClick = { showHistory = !showHistory },
                        color = c.accentOnHero,
                    )
                }
                if (showHistory) DoneHistoryList(reminder.doneHistory, color = c.onMediaMuted)
            }
        }
    }
}

/** The latest taps (at most seven) as accent dots, oldest faintest, newest on the right. */
@Composable
private fun HistoryDots(history: List<DoneMoment>) {
    val accent = PeopleHubTheme.colors.accent
    val shown = history.take(MAX_HISTORY_DOTS).reversed()
    Row(horizontalArrangement = Arrangement.spacedBy(PeopleHubTheme.spacing.xs)) {
        shown.forEachIndexed { index, _ ->
            val fraction = if (shown.size == 1) 1f else index.toFloat() / (shown.size - 1)
            Box(
                Modifier
                    .size(8.dp)
                    .alpha(HISTORY_DOT_MIN_ALPHA + (1f - HISTORY_DOT_MIN_ALPHA) * fraction)
                    .background(accent, CircleShape),
            )
        }
    }
}

/**
 * A reminder in "Prossimi gesti": opaque card (accent edge when due) with category chip, serif title,
 * the countdown or the due pill, the person, a hairline, and the done row with its history.
 */
@Composable
private fun ReminderCard(
    reminder: ReminderListItem,
    onClick: () -> Unit,
    onPersonClick: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
    onMarkDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    SurfaceCard(
        modifier = modifier.padding(horizontal = spacing.gutter).fillMaxWidth(),
        shape = VellutoShapes.Card,
        shadow = VellutoElevation.Card,
        borderColor = if (reminder.isDue) c.accentEdge else c.line,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(spacing.l),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    CategoryChip(label = stringResource(reminder.category.labelRes()), icon = reminder.category.icon())
                    Text(
                        text = reminder.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = c.text,
                    )
                    if (!reminder.note.isNullOrBlank()) {
                        Text(
                            text = reminder.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.text2,
                        )
                    }
                }
                ReminderWhen(reminder)
            }
            ReminderPersonRow(
                reminder = reminder,
                onPersonClick = onPersonClick,
                onToggleEnabled = onToggleEnabled,
                onDelete = onDelete,
                avatarSize = 30.dp,
                onHero = false,
            )
            Hairline()
            ReminderDoneRow(reminder = reminder, onMarkDone = onMarkDone)
        }
    }
}

/** Top-right of a card: the "due" pill, "tomorrow", or the TRA / N / GIORNI countdown. */
@Composable
private fun ReminderWhen(reminder: ReminderListItem) {
    val c = PeopleHubTheme.colors
    when {
        reminder.isDue && reminder.enabled ->
            StatusPill(label = stringResource(R.string.reminder_due), content = c.onAccent, container = c.accent)
        reminder.isDue ->
            StatusPill(
                label = stringResource(R.string.reminder_due),
                content = c.accentOnSoft,
                container = c.accentSoft,
                border = c.accentEdge,
            )
        reminder.daysUntil == 1L ->
            StatusPill(
                label = stringResource(UiR.string.ui_tomorrow),
                content = c.accentOnSoft,
                container = c.accentSoft,
                border = c.accentEdge,
                dot = false,
            )
        else ->
            DayCountDisplay(
                number = reminder.daysUntil.absoluteValue.toInt(),
                unitLabel = stringResource(R.string.reminder_days),
                prefix = stringResource(R.string.reminder_in),
                emphasized = false,
            )
    }
}

/**
 * The person the reminder is for (avatar and name, opening their profile) with the pause switch and
 * the delete action at the end. [onHero] switches to the light colours of the dark hero card.
 */
@Composable
private fun ReminderPersonRow(
    reminder: ReminderListItem,
    onPersonClick: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
    avatarSize: Dp,
    onHero: Boolean,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = spacing.touchTarget)
                    .clip(VellutoShapes.Pill)
                    .clickable(onClick = onPersonClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            PersonAvatar(initials = reminder.personName.toInitials(), photoPath = null, size = avatarSize)
            Text(
                text = reminder.personName,
                style =
                    if (onHero) {
                        MaterialTheme.typography.titleSmall
                    } else {
                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    },
                color = if (onHero) c.onMedia else c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        VellutoSwitch(checked = reminder.enabled, onCheckedChange = { onToggleEnabled() })
        TooltipIconButton(
            icon = VellutoIcons.Trash2,
            description = stringResource(R.string.reminder_delete),
            onClick = onDelete,
            tint = if (onHero) c.onMediaMuted else c.text2,
        )
    }
}

/**
 * The "done" control and the history it writes to: ticking the reminder off logs this moment and
 * redraws its next occurrence, so the button doubles as the answer to "when did I last actually do
 * this?".
 *
 * The button stays tappable once it has been ticked today — doing the same thing twice is allowed, and
 * the second tick is logged too and restarts the cadence from that moment — but its label says the day
 * is already recorded, so the state is never ambiguous. Tapping the summary unfolds every recorded tap.
 */
@Composable
private fun ReminderDoneRow(reminder: ReminderListItem, onMarkDone: () -> Unit) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    var showHistory by remember { mutableStateOf(false) }
    val hasHistory = reminder.doneHistory.isNotEmpty()
    val statusColor = if (reminder.doneToday) c.ok else c.text2
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = spacing.touchTarget)
                        .clip(VellutoShapes.Tile)
                        .clickable(enabled = hasHistory) { showHistory = !showHistory },
            ) {
                Icon(
                    imageVector =
                        when {
                            reminder.doneToday -> VellutoIcons.CircleCheck
                            reminder.lastDoneOn != null -> VellutoIcons.Clock
                            else -> VellutoIcons.CircleMinus
                        },
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = doneSummary(reminder),
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (reminder.doneToday) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    color = statusColor,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (hasHistory) {
                    Icon(
                        imageVector = if (showHistory) VellutoIcons.ChevronUp else VellutoIcons.ChevronDown,
                        contentDescription = historyToggleLabel(showHistory),
                        tint = c.text2,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            VellutoButton(
                text = stringResource(if (reminder.doneToday) R.string.reminder_done_again else R.string.reminder_mark_done),
                onClick = onMarkDone,
                icon = VellutoIcons.Check,
                height = spacing.touchTarget,
                style =
                    when {
                        reminder.doneToday -> VellutoButtonStyle.Ghost
                        reminder.isDue && reminder.enabled -> VellutoButtonStyle.Primary
                        else -> VellutoButtonStyle.Soft
                    },
            )
        }
        if (showHistory && hasHistory) DoneHistoryList(reminder.doneHistory, color = c.text2)
    }
}

/** "Done today", "Last done 4 Aug 2026 · 3 times" or "Never marked as done yet". */
@Composable
private fun doneSummary(reminder: ReminderListItem): String =
    when {
        reminder.doneToday -> stringResource(R.string.reminder_done_today)
        reminder.lastDoneOn != null ->
            stringResource(
                R.string.reminder_done_last,
                reminder.lastDoneOn.format(ReminderDoneDateFormat),
                reminder.timesDone,
            )
        else -> stringResource(R.string.reminder_done_never)
    }

@Composable
private fun historyToggleLabel(showing: Boolean): String =
    stringResource(if (showing) R.string.reminder_done_history_hide else R.string.reminder_done_history_show)

/** Every recorded tap, most recent first. */
@Composable
private fun DoneHistoryList(
    history: List<DoneMoment>,
    color: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PeopleHubTheme.spacing.xxs)) {
        history.forEach { moment ->
            Text(
                text =
                    moment.time?.let { time ->
                        stringResource(
                            R.string.reminder_done_history_entry,
                            moment.day.format(ReminderDoneDateFormat),
                            time.format(ReminderDoneTimeFormat),
                        )
                    } ?: moment.day.format(ReminderDoneDateFormat),
                style = MaterialTheme.typography.bodySmall,
                color = color,
            )
        }
    }
}

/** The Lucide glyph of a relationship category. */
private fun ReminderCategory.icon(): ImageVector =
    when (this) {
        ReminderCategory.LOVE -> VellutoIcons.Heart
        ReminderCategory.FRIENDSHIP -> VellutoIcons.Users
        ReminderCategory.FAMILY -> VellutoIcons.House
        ReminderCategory.CUSTOM -> VellutoIcons.Sparkle
    }

/** Up to two uppercase initials of a display name, for the monogram avatar. */
private fun String.toInitials(): String {
    val parts = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return buildString {
        parts.firstOrNull()?.firstOrNull()?.let(::append)
        if (parts.size > 1) parts.last().firstOrNull()?.let(::append)
    }.uppercase().ifEmpty { "?" }
}

private fun categoryOptions(): List<ReminderCategory?> =
    listOf(null, ReminderCategory.LOVE, ReminderCategory.FRIENDSHIP, ReminderCategory.FAMILY, ReminderCategory.CUSTOM)

@Preview(name = "Promemoria, chiaro", widthDp = 390, heightDp = 844)
@Composable
private fun RemindersListLightPreview() {
    PeopleHubTheme(darkTheme = false) { RemindersListPreviewContent() }
}

@Preview(name = "Promemoria, scuro", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RemindersListDarkPreview() {
    PeopleHubTheme(darkTheme = true) { RemindersListPreviewContent() }
}

@Composable
private fun RemindersListPreviewContent() {
    val today = LocalDate.of(2026, 10, 23)
    RemindersListContent(
        state =
            RemindersScreenState(
                listState =
                    UiState.Success(
                        listOf(
                            ReminderListItem(
                                1,
                                1,
                                "Marta Colombo",
                                "Porta i fiori",
                                null,
                                ReminderCategory.LOVE,
                                enabled = true,
                                daysUntil = 0,
                                lastDoneOn = today.minusDays(21),
                                timesDone = 7,
                                doneHistory =
                                    List(7) { i -> DoneMoment(today.minusDays(21L * (i + 1)), LocalTime.of(18, 42)) },
                                today = today,
                            ),
                            ReminderListItem(
                                2,
                                2,
                                "Matteo Galli",
                                "Tempo insieme non strutturato",
                                null,
                                ReminderCategory.FRIENDSHIP,
                                enabled = true,
                                daysUntil = 0,
                                lastDoneOn = today.minusDays(37),
                                timesDone = 2,
                                doneHistory = listOf(DoneMoment(today.minusDays(37), null)),
                                today = today,
                            ),
                            ReminderListItem(
                                3,
                                3,
                                "Elena Ferri",
                                "Senti un fratello o una sorella",
                                null,
                                ReminderCategory.FAMILY,
                                enabled = true,
                                daysUntil = 1,
                                lastDoneOn = today.minusDays(14),
                                timesDone = 12,
                                today = today,
                            ),
                            ReminderListItem(
                                4,
                                4,
                                "Laura Bassi",
                                "Chiama i tuoi genitori",
                                "Chiedi com'e andata la visita",
                                ReminderCategory.FAMILY,
                                enabled = true,
                                daysUntil = 6,
                                lastDoneOn = today,
                                timesDone = 9,
                                doneHistory = listOf(DoneMoment(today, LocalTime.of(8, 15))),
                                today = today,
                            ),
                            ReminderListItem(
                                5,
                                5,
                                "Francesca Lombardi",
                                "Organizza una visita a Torino",
                                null,
                                ReminderCategory.CUSTOM,
                                enabled = false,
                                daysUntil = 14,
                                today = today,
                            ),
                        ),
                    ),
                category = null,
            ),
        onAddReminder = {},
        onOpenScience = {},
        onCategoryChange = {},
        onReminderClick = {},
        onPersonClick = {},
        onToggleEnabled = { _, _ -> },
        onDelete = {},
        onMarkDone = {},
    )
}
