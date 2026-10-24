package com.peoplehub.feature.birthdays

import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.core.domain.model.UpcomingBirthday
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.DateTile
import com.peoplehub.core.ui.components.DisplayTitle
import com.peoplehub.core.ui.components.EmptyView
import com.peoplehub.core.ui.components.ErrorView
import com.peoplehub.core.ui.components.FloatingScene
import com.peoplehub.core.ui.components.GlassIconButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.LoadingView
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.PersonAvatar
import com.peoplehub.core.ui.components.SceneArt
import com.peoplehub.core.ui.components.SegmentedPill
import com.peoplehub.core.ui.components.StatusPill
import com.peoplehub.core.ui.components.SurfaceCard
import com.peoplehub.core.ui.components.VellutoBackground
import com.peoplehub.core.ui.components.VellutoButton
import com.peoplehub.core.ui.components.VellutoButtonStyle
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.VellutoElevation
import com.peoplehub.core.ui.modifier.lightEdge
import com.peoplehub.core.ui.modifier.riseIn
import com.peoplehub.core.ui.modifier.softShadow
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val DAYS_IN_WEEK = 7
private const val MONTHS_PER_ROW = 3
private const val MONTHS_IN_YEAR = 12

/** Entrance cascade: hero 0, view switch 1, then the calendar or the first list rows. */
private const val FIRST_BODY_INDEX = 2
private const val LAST_RISE_INDEX = 8

/** Localized "month day" title (e.g. "October 3") for the per-day birthday dialog. */
private val DayTitleFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d")

/**
 * Stateful entry point for the birthdays ("Milestones") screen. Wires the [BirthdaysViewModel] to
 * the stateless [BirthdaysContent], handles the storage-access import/export launchers and the
 * share sheet, and surfaces import results through a snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdaysScreen(
    onPersonClick: (Long) -> Unit,
    onAddBirthdays: () -> Unit,
    onBack: () -> Unit,
    viewModel: BirthdaysViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val visibleMonth by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val csvImportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val csv =
                    runCatching {
                        context.contentResolver
                            .openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                    }.getOrNull()
                if (csv != null) viewModel.onCsvImported(csv)
            }
        }
    val jsonImportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val json =
                    runCatching {
                        context.contentResolver
                            .openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                    }.getOrNull()
                if (json != null) viewModel.onJsonImported(json)
            }
        }
    val csvExportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver
                        .openOutputStream(uri)
                        ?.bufferedWriter()
                        ?.use { it.write(viewModel.exportCsv()) }
                }
            }
        }
    val jsonExportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver
                        .openOutputStream(uri)
                        ?.bufferedWriter()
                        ?.use { it.write(viewModel.exportJson()) }
                }
            }
        }

    val csvImportFailed = stringResource(R.string.import_failed)
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        val text =
            when (current) {
                is BirthdayMessage.CsvImported ->
                    context.getString(R.string.import_result, current.imported, current.errors)
                is BirthdayMessage.ProfileImported ->
                    context.getString(R.string.import_one_profile, current.name)
                BirthdayMessage.ImportFailed -> csvImportFailed
            }
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown()
    }

    val spacing = PeopleHubTheme.spacing
    VellutoBackground(secondGlow = true) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                // The hero below carries the serif title, so the bar stays a quiet row of glass buttons.
                PeopleHubTopBar(
                    title = "",
                    centered = true,
                    navigationIcon = {
                        GlassIconButton(
                            icon = VellutoIcons.ArrowLeft,
                            description = stringResource(R.string.action_back),
                            onClick = onBack,
                            modifier = Modifier.padding(start = spacing.s),
                        )
                    },
                    actions = {
                        OverflowMenu(
                            onImportCsv = { csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) },
                            onImportJson = { jsonImportLauncher.launch(arrayOf("application/json")) },
                            onExportCsv = { csvExportLauncher.launch(DEFAULT_CSV_NAME) },
                            onExportJson = { jsonExportLauncher.launch(DEFAULT_JSON_NAME) },
                            onShare = {
                                val intent =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_TEXT, viewModel.exportCsv())
                                    }
                                context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_birthdays)))
                            },
                        )
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            BirthdaysContent(
                state = state,
                viewMode = viewMode,
                visibleMonth = visibleMonth,
                today = LocalDate.now(),
                contentPadding = innerPadding,
                onPersonClick = onPersonClick,
                onAddBirthdays = onAddBirthdays,
                onViewModeChange = viewModel::onViewModeChange,
                onPreviousMonth = viewModel::onPreviousMonth,
                onNextMonth = viewModel::onNextMonth,
            )
        }
    }
}

/** Stateless milestones layout, switching on [viewMode]; safe to drive from previews and tests. */
@Composable
internal fun BirthdaysContent(
    state: UiState<BirthdaysData>,
    viewMode: BirthdayViewMode,
    visibleMonth: YearMonth,
    today: LocalDate,
    contentPadding: PaddingValues,
    onPersonClick: (Long) -> Unit,
    onAddBirthdays: () -> Unit,
    onViewModeChange: (BirthdayViewMode) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    // Birthdays of the calendar day the user tapped; non-empty while the day dialog is shown.
    var selectedDay by remember { mutableStateOf<List<UpcomingBirthday>>(emptyList()) }
    val spacing = PeopleHubTheme.spacing

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + spacing.section,
            ),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        item(key = "header") { BirthdaysHero(onAddBirthdays = onAddBirthdays) }
        item(key = "view_mode") {
            ViewModeSwitch(
                current = viewMode,
                onViewModeChange = onViewModeChange,
                modifier =
                    Modifier
                        .padding(horizontal = spacing.gutter)
                        .padding(bottom = spacing.s)
                        .riseIn(1),
            )
        }

        when (state) {
            UiState.Loading -> item(key = "loading") { StateBox { LoadingView() } }
            UiState.Empty ->
                item(key = "empty") {
                    StateBox {
                        EmptyView(
                            title = stringResource(R.string.birthdays_empty_title),
                            description = stringResource(R.string.birthdays_empty_desc),
                            icon = VellutoIcons.Cake,
                        )
                    }
                }
            is UiState.Error -> item(key = "error") { StateBox { ErrorView(message = state.message) } }
            is UiState.Success ->
                birthdaysBody(
                    data = state.data,
                    viewMode = viewMode,
                    visibleMonth = visibleMonth,
                    today = today,
                    onPersonClick = onPersonClick,
                    onDaySelected = { selectedDay = it },
                    onMonthChange = { target -> if (target.isBefore(visibleMonth)) onPreviousMonth() else onNextMonth() },
                )
        }
    }

    if (selectedDay.isNotEmpty()) {
        BirthdayDayDialog(
            birthdays = selectedDay,
            onPersonClick = { personId ->
                selectedDay = emptyList()
                onPersonClick(personId)
            },
            onDismiss = { selectedDay = emptyList() },
        )
    }
}

private fun LazyListScope.birthdaysBody(
    data: BirthdaysData,
    viewMode: BirthdayViewMode,
    visibleMonth: YearMonth,
    today: LocalDate,
    onPersonClick: (Long) -> Unit,
    onDaySelected: (List<UpcomingBirthday>) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
) {
    when (viewMode) {
        BirthdayViewMode.YEAR ->
            item(key = "year") {
                YearGrid(
                    year = visibleMonth.year,
                    byMonthDay = data.byMonthDay,
                    today = today,
                    modifier = Modifier.padding(horizontal = PeopleHubTheme.spacing.gutter).riseIn(FIRST_BODY_INDEX),
                )
            }
        BirthdayViewMode.MONTH ->
            item(key = "month") {
                MonthCalendar(
                    month = visibleMonth,
                    byMonthDay = data.byMonthDay,
                    today = today,
                    onPreviousMonth = { onMonthChange(visibleMonth.minusMonths(1)) },
                    onNextMonth = { onMonthChange(visibleMonth.plusMonths(1)) },
                    onDaySelected = onDaySelected,
                    modifier = Modifier.padding(horizontal = PeopleHubTheme.spacing.gutter).riseIn(FIRST_BODY_INDEX),
                )
            }
        BirthdayViewMode.LIST ->
            itemsIndexed(data.all, key = { _, birthday -> birthday.personId }) { index, birthday ->
                val rise = FIRST_BODY_INDEX + index
                BirthdayRow(
                    birthday = birthday,
                    onClick = { onPersonClick(birthday.personId) },
                    modifier =
                        Modifier
                            .padding(horizontal = PeopleHubTheme.spacing.gutter)
                            .then(if (rise <= LAST_RISE_INDEX) Modifier.riseIn(rise) else Modifier),
                )
            }
    }
}

/** The editorial top of the screen: two-tone serif title, subtitle, add action and the floating gift. */
@Composable
private fun BirthdaysHero(onAddBirthdays: () -> Unit) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    Box(modifier = Modifier.fillMaxWidth()) {
        FloatingScene(
            art = SceneArt.Gift,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 30.dp, y = (-6).dp),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = spacing.heroInset, end = spacing.heroInset, top = spacing.xxl, bottom = spacing.s)
                    .riseIn(0),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            DisplayTitle(
                text = stringResource(R.string.birthdays_title),
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                text = stringResource(R.string.birthdays_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = c.text2,
                modifier = Modifier.widthIn(max = 230.dp),
            )
            VellutoButton(
                text = stringResource(R.string.birthdays_add),
                onClick = onAddBirthdays,
                style = VellutoButtonStyle.Soft,
                icon = VellutoIcons.Plus,
                modifier = Modifier.padding(top = spacing.xs),
            )
        }
    }
}

/** Year / Month / List switch. */
@Composable
private fun ViewModeSwitch(
    current: BirthdayViewMode,
    onViewModeChange: (BirthdayViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = viewModeOptions()
    SegmentedPill(
        options = options.map { stringResource(it.second) },
        selectedIndex = options.indexOfFirst { it.first == current },
        onSelect = { index -> onViewModeChange(options[index].first) },
        modifier = modifier,
    )
}

@Composable
private fun StateBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(320.dp).padding(horizontal = PeopleHubTheme.spacing.gutter),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * One upcoming birthday as a milestone row: date tile, name, "Birthday · Turns X" and a "when" pill.
 * Today's row sits on a soft accent band with the solid accent "Today" pill, as in the mockup.
 */
@Composable
private fun BirthdayRow(
    birthday: UpcomingBirthday,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    val isToday = birthday.daysUntil == 0
    val date = birthday.nextOccurrence
    val month = remember(date) { date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()).trimEnd('.') }
    val label = stringResource(R.string.birthday_label)
    val subtitle = birthday.turningAge?.let { "$label · ${stringResource(R.string.birthday_turns, it)}" } ?: label
    SurfaceCard(
        modifier = modifier.fillMaxWidth(),
        shape = VellutoShapes.CardSmall,
        shadow = if (isToday) VellutoElevation.Card else null,
        borderColor = if (isToday) c.accentEdge else c.line,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(if (isToday) c.accentSoft else Color.Transparent)
                    .clickable(role = Role.Button, onClick = onClick)
                    .heightIn(min = 76.dp)
                    .padding(horizontal = spacing.m, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.ml),
        ) {
            DateTile(month = month, day = date.dayOfMonth.toString(), highlighted = isToday)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = birthday.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            WhenPill(daysUntil = birthday.daysUntil)
        }
    }
}

/** "Today" as a solid accent pill with the gift, otherwise a quiet "In N days". */
@Composable
private fun WhenPill(daysUntil: Int) {
    val c = PeopleHubTheme.colors
    if (daysUntil == 0) {
        StatusPill(
            label = stringResource(R.string.birthday_today),
            content = c.onAccent,
            container = c.accent,
            icon = VellutoIcons.Gift,
            modifier = Modifier.softShadow(c.glowStrong, CircleShape, VellutoElevation.GlowSmall),
        )
    } else {
        val prefix = stringResource(R.string.birthday_in_days)
        val unit = stringResource(R.string.birthday_days_unit).lowercase(Locale.getDefault())
        StatusPill(
            label = "$prefix $daysUntil $unit",
            content = c.text2,
            container = c.surface2,
            dot = false,
            border = c.line,
        )
    }
}

/** The 12-month overview grid for [BirthdayViewMode.YEAR]. */
@Composable
private fun YearGrid(
    year: Int,
    byMonthDay: Map<Int, List<UpcomingBirthday>>,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val spacing = PeopleHubTheme.spacing
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        (0 until MONTHS_IN_YEAR step MONTHS_PER_ROW).forEach { rowStart ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                (rowStart until rowStart + MONTHS_PER_ROW).forEach { monthIndex ->
                    val month = YearMonth.of(year, monthIndex + 1)
                    MiniMonthCard(
                        month = month,
                        byMonthDay = byMonthDay,
                        isCurrent = month == YearMonth.from(today),
                        today = today,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniMonthCard(
    month: YearMonth,
    byMonthDay: Map<Int, List<UpcomingBirthday>>,
    isCurrent: Boolean,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    val count =
        remember(month, byMonthDay) {
            (1..month.lengthOfMonth()).sumOf { day -> byMonthDay[monthDayKey(month.monthValue, day)]?.size ?: 0 }
        }
    SurfaceCard(
        modifier = modifier,
        shape = VellutoShapes.Inner,
        shadow = if (isCurrent) VellutoElevation.Card else null,
        borderColor = if (isCurrent) c.accentEdge else c.line,
    ) {
        Column(modifier = Modifier.padding(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text =
                        month.month
                            .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                            .trimEnd('.')
                            .uppercase(),
                    style = PeopleHubTheme.type.tileCaps,
                    color = if (isCurrent) c.accentTextSurface else c.text,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (count > 0) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = c.text2,
                    )
                }
            }
            MiniMonthDots(month = month, byMonthDay = byMonthDay, today = today)
        }
    }
}

@Composable
private fun MiniMonthDots(
    month: YearMonth,
    byMonthDay: Map<Int, List<UpcomingBirthday>>,
    today: LocalDate,
) {
    val c = PeopleHubTheme.colors
    val firstDayOffset = (month.atDay(1).dayOfWeek.value % DAYS_IN_WEEK)
    val cells = firstDayOffset + month.lengthOfMonth()
    val rows = (cells + DAYS_IN_WEEK - 1) / DAYS_IN_WEEK
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        (0 until rows).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (0 until DAYS_IN_WEEK).forEach { col ->
                    val cellIndex = row * DAYS_IN_WEEK + col
                    val day = cellIndex - firstDayOffset + 1
                    val inMonth = day in 1..month.lengthOfMonth()
                    val hasBirthday = inMonth && byMonthDay.containsKey(monthDayKey(month.monthValue, day))
                    val isToday = inMonth && month == YearMonth.from(today) && today.dayOfMonth == day
                    val fill =
                        when {
                            !inMonth -> Color.Transparent
                            hasBirthday -> c.accent
                            isToday -> c.text2
                            else -> c.track
                        }
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(fill, CircleShape),
                    )
                }
            }
        }
    }
}

/** A full single-month calendar grid for [BirthdayViewMode.MONTH]. */
@Composable
private fun MonthCalendar(
    month: YearMonth,
    byMonthDay: Map<Int, List<UpcomingBirthday>>,
    today: LocalDate,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDaySelected: (List<UpcomingBirthday>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = PeopleHubTheme.spacing
    GlassPanel(modifier = modifier.fillMaxWidth(), shape = VellutoShapes.CardLarge, shadow = VellutoElevation.Raised) {
        Column(modifier = Modifier.padding(horizontal = spacing.m, vertical = spacing.l)) {
            MonthHeader(month = month, onPreviousMonth = onPreviousMonth, onNextMonth = onNextMonth)
            Spacer(Modifier.height(spacing.ml))
            WeekdayHeaderRow()
            Spacer(Modifier.height(spacing.xs))
            MonthDayGrid(month = month, byMonthDay = byMonthDay, today = today, onDaySelected = onDaySelected)
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    val locale = Locale.getDefault()
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            CapsLabel(text = month.year.toString())
            Text(
                text = month.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) },
                style = MaterialTheme.typography.headlineMedium,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        GlassIconButton(
            icon = VellutoIcons.ChevronLeft,
            description = stringResource(R.string.calendar_previous_month),
            onClick = onPreviousMonth,
        )
        GlassIconButton(
            icon = VellutoIcons.ChevronRight,
            description = stringResource(R.string.calendar_next_month),
            onClick = onNextMonth,
        )
    }
}

@Composable
private fun WeekdayHeaderRow() {
    val weekdays =
        listOf(
            R.string.weekday_sunday,
            R.string.weekday_monday,
            R.string.weekday_tuesday,
            R.string.weekday_wednesday,
            R.string.weekday_thursday,
            R.string.weekday_friday,
            R.string.weekday_saturday,
        )
    Row(modifier = Modifier.fillMaxWidth()) {
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
    byMonthDay: Map<Int, List<UpcomingBirthday>>,
    today: LocalDate,
    onDaySelected: (List<UpcomingBirthday>) -> Unit,
) {
    val firstDayOffset = (month.atDay(1).dayOfWeek.value % DAYS_IN_WEEK)
    val cells = firstDayOffset + month.lengthOfMonth()
    val rows = (cells + DAYS_IN_WEEK - 1) / DAYS_IN_WEEK
    Column(verticalArrangement = Arrangement.spacedBy(PeopleHubTheme.spacing.xxs)) {
        (0 until rows).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                (0 until DAYS_IN_WEEK).forEach { col ->
                    val cellIndex = row * DAYS_IN_WEEK + col
                    val day = cellIndex - firstDayOffset + 1
                    Box(modifier = Modifier.weight(1f)) {
                        if (day in 1..month.lengthOfMonth()) {
                            DayCell(
                                day = day,
                                birthdays = byMonthDay[monthDayKey(month.monthValue, day)].orEmpty(),
                                isToday =
                                    today.year == month.year &&
                                        today.monthValue == month.monthValue &&
                                        today.dayOfMonth == day,
                                onDaySelected = onDaySelected,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One calendar day, a 48dp target. Today is the solid accent disc; a day with birthdays is a soft
 * accent disc with an accent hairline (today keeps a small dot to say it has one too), and a day with
 * **more than one** birthday carries an inverted count badge. Tapping a day with birthdays surfaces
 * them through [onDaySelected].
 */
@Composable
private fun DayCell(
    day: Int,
    birthdays: List<UpcomingBirthday>,
    isToday: Boolean,
    onDaySelected: (List<UpcomingBirthday>) -> Unit,
) {
    val c = PeopleHubTheme.colors
    val hasBirthday = birthdays.isNotEmpty()
    val hasMultiple = birthdays.size > 1
    val fill =
        when {
            isToday -> c.accent
            hasBirthday -> c.accentSoft
            else -> Color.Transparent
        }
    val textColor =
        when {
            isToday -> c.onAccent
            hasBirthday -> c.accentOnSoft
            else -> c.text
        }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(CircleShape)
                .then(
                    if (hasBirthday) {
                        Modifier.clickable(role = Role.Button) { onDaySelected(birthdays) }
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .then(if (isToday) Modifier.softShadow(c.glowSoft, CircleShape, VellutoElevation.GlowSmall) else Modifier)
                    .background(fill, CircleShape)
                    .then(
                        if (hasBirthday && !isToday) {
                            Modifier.lightEdge(CircleShape, Color.Transparent, c.accentEdge)
                        } else {
                            Modifier
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.toString(),
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (hasBirthday || isToday) FontWeight.SemiBold else FontWeight.Normal,
                        fontFeatureSettings = "tnum",
                    ),
                color = textColor,
            )
            if (isToday && hasBirthday && !hasMultiple) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 5.dp)
                        .size(4.dp)
                        .background(c.onAccent, CircleShape),
                )
            }
        }
        if (hasMultiple) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .background(c.text, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = birthdays.size.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.bg,
                )
            }
        }
    }
}

/**
 * A dialog listing every birthday that falls on the tapped calendar day. Each row is tappable and
 * opens that person via [onPersonClick]; [onDismiss] closes the dialog.
 */
@Composable
private fun BirthdayDayDialog(
    birthdays: List<UpcomingBirthday>,
    onPersonClick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = PeopleHubTheme.colors
    val locale = Locale.getDefault()
    val dayDate = birthdays.first().birthday
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        titleContentColor = c.text,
        textContentColor = c.text,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(PeopleHubTheme.spacing.xxs)) {
                Text(
                    text = dayDate.format(DayTitleFormatter).replaceFirstChar { it.titlecase(locale) },
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.text,
                )
                Text(
                    text = stringResource(R.string.birthday_day_count, birthdays.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.text2,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PeopleHubTheme.spacing.xxs)) {
                birthdays.forEach { birthday ->
                    DayDialogPerson(birthday = birthday, onClick = { onPersonClick(birthday.personId) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    )
}

@Composable
private fun DayDialogPerson(
    birthday: UpcomingBirthday,
    onClick: () -> Unit,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clip(VellutoShapes.Inner)
                .background(c.surface2)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = spacing.sm, vertical = spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        PersonAvatar(
            initials = birthday.fullName.toInitials(),
            photoPath = birthday.photoPath,
            size = 44.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = birthday.fullName,
                style = MaterialTheme.typography.titleMedium,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            birthday.turningAge?.let { age ->
                Text(
                    text = stringResource(R.string.birthday_turns, age),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.text2,
                )
            }
        }
        Icon(VellutoIcons.ChevronRight, contentDescription = null, tint = c.text2, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun OverflowMenu(
    onImportCsv: () -> Unit,
    onImportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onShare: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(end = PeopleHubTheme.spacing.s)) {
        GlassIconButton(
            icon = VellutoIcons.MoreVertical,
            description = stringResource(R.string.action_more),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuRow(R.string.import_csv, VellutoIcons.Upload) {
                expanded = false
                onImportCsv()
            }
            MenuRow(R.string.import_json, VellutoIcons.Upload) {
                expanded = false
                onImportJson()
            }
            MenuRow(R.string.export_csv, VellutoIcons.Download) {
                expanded = false
                onExportCsv()
            }
            MenuRow(R.string.export_json, VellutoIcons.Download) {
                expanded = false
                onExportJson()
            }
            MenuRow(R.string.share_birthdays, VellutoIcons.Share) {
                expanded = false
                onShare()
            }
        }
    }
}

@Composable
private fun MenuRow(
    labelRes: Int,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = PeopleHubTheme.colors.text2) },
        onClick = onClick,
    )
}

private fun viewModeOptions(): List<Pair<BirthdayViewMode, Int>> =
    listOf(
        BirthdayViewMode.YEAR to R.string.view_year,
        BirthdayViewMode.MONTH to R.string.view_month,
        BirthdayViewMode.LIST to R.string.view_list,
    )

private const val DEFAULT_CSV_NAME = "birthdays.csv"
private const val DEFAULT_JSON_NAME = "peoplehub-backup.json"

/** Up to two uppercase initials derived from a display name, mirroring [com.peoplehub.core.domain.model.Person.initials]. */
private fun String.toInitials(): String {
    val parts = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return buildString {
        parts.firstOrNull()?.firstOrNull()?.let(::append)
        if (parts.size > 1) parts.last().firstOrNull()?.let(::append)
    }.uppercase().ifEmpty { "?" }
}

private fun previewBirthday(
    personId: Long,
    name: String,
    month: Int,
    day: Int,
    daysUntil: Int,
    turningAge: Int?,
): UpcomingBirthday =
    UpcomingBirthday(
        personId = personId,
        fullName = name,
        photoPath = null,
        birthday = LocalDate.of(1990, month, day),
        nextOccurrence = LocalDate.of(2026, month, day),
        daysUntil = daysUntil,
        turningAge = turningAge,
    )

private fun previewData(): BirthdaysData {
    val sofia = previewBirthday(1, "Sofia Bianchi", 10, 23, 0, 29)
    val elena = previewBirthday(2, "Elena Ferraboli", 10, 27, 4, 31)
    val giulia = previewBirthday(4, "Giulia Rinaldi", 10, 27, 4, null)
    val luca = previewBirthday(3, "Luca Moretti", 11, 10, 18, 30)
    return BirthdaysData(
        all = listOf(sofia, elena, giulia, luca),
        upcoming30 = listOf(sofia, elena, giulia, luca),
        byMonthDay =
            mapOf(
                monthDayKey(10, 23) to listOf(sofia),
                monthDayKey(10, 27) to listOf(elena, giulia),
                monthDayKey(11, 10) to listOf(luca),
                monthDayKey(3, 4) to listOf(previewBirthday(5, "Marco Testa", 3, 4, 132, 41)),
            ),
    )
}

@Composable
private fun BirthdaysPreview(
    dark: Boolean,
    viewMode: BirthdayViewMode,
) {
    PeopleHubTheme(darkTheme = dark) {
        VellutoBackground(secondGlow = true) {
            BirthdaysContent(
                state = UiState.Success(previewData()),
                viewMode = viewMode,
                visibleMonth = YearMonth.of(2026, 10),
                today = LocalDate.of(2026, 10, 23),
                contentPadding = PaddingValues(top = 64.dp),
                onPersonClick = {},
                onAddBirthdays = {},
                onViewModeChange = {},
                onPreviousMonth = {},
                onNextMonth = {},
            )
        }
    }
}

@Preview(name = "List, light", widthDp = 390, heightDp = 844)
@Composable
private fun BirthdaysListLightPreview() {
    BirthdaysPreview(dark = false, viewMode = BirthdayViewMode.LIST)
}

@Preview(name = "List, dark", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BirthdaysListDarkPreview() {
    BirthdaysPreview(dark = true, viewMode = BirthdayViewMode.LIST)
}

@Preview(name = "Month, light", widthDp = 390, heightDp = 844)
@Composable
private fun BirthdaysMonthLightPreview() {
    BirthdaysPreview(dark = false, viewMode = BirthdayViewMode.MONTH)
}

@Preview(name = "Month, dark", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BirthdaysMonthDarkPreview() {
    BirthdaysPreview(dark = true, viewMode = BirthdayViewMode.MONTH)
}

@Preview(name = "Year, dark", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BirthdaysYearDarkPreview() {
    BirthdaysPreview(dark = true, viewMode = BirthdayViewMode.YEAR)
}
