package com.peoplehub.feature.birthdays

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.core.domain.model.UpcomingBirthday
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.DayCountDisplay
import com.peoplehub.core.ui.components.EmptyView
import com.peoplehub.core.ui.components.ErrorView
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.LoadingView
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.PersonAvatar
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.core.ui.theme.PeopleHubTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private const val DAYS_IN_WEEK = 7
private const val MONTHS_PER_ROW = 4
private const val MONTHS_IN_YEAR = 12

/**
 * Stateful entry point for the birthdays ("Milestones") screen. Wires the [BirthdaysViewModel] to
 * the stateless [BirthdaysContent], handles the storage-access import/export launchers and the
 * share sheet, and surfaces import results through a snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdaysScreen(
    onPersonClick: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: BirthdaysViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val visibleMonth by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val csvImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val csv = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (csv != null) viewModel.onCsvImported(csv)
        }
    }
    val jsonImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val json = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (json != null) viewModel.onJsonImported(json)
        }
    }
    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(viewModel.exportCsv()) }
            }
        }
    }
    val jsonExportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(viewModel.exportJson()) }
                }
            }
        }

    val csvImportFailed = stringResource(R.string.import_failed)
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        val text = when (current) {
            is BirthdayMessage.CsvImported ->
                context.getString(R.string.import_result, current.imported, current.errors)
            is BirthdayMessage.ProfileImported ->
                context.getString(R.string.import_one_profile, current.name)
            BirthdayMessage.ImportFailed -> csvImportFailed
        }
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown()
    }

    Scaffold(
        topBar = {
            PeopleHubTopBar(
                title = stringResource(R.string.birthdays_title),
                centered = true,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    ViewModeMenu(current = viewMode, onViewModeChange = viewModel::onViewModeChange)
                    OverflowMenu(
                        onImportCsv = { csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) },
                        onImportJson = { jsonImportLauncher.launch(arrayOf("application/json")) },
                        onExportCsv = { csvExportLauncher.launch(DEFAULT_CSV_NAME) },
                        onExportJson = { jsonExportLauncher.launch(DEFAULT_JSON_NAME) },
                        onShare = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
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
            onPreviousMonth = viewModel::onPreviousMonth,
            onNextMonth = viewModel::onNextMonth,
        )
    }
}

/** Stateless milestones layout, switching on [viewMode]; safe to drive from previews. */
@Composable
private fun BirthdaysContent(
    state: UiState<BirthdaysData>,
    viewMode: BirthdayViewMode,
    visibleMonth: YearMonth,
    today: LocalDate,
    contentPadding: PaddingValues,
    onPersonClick: (Long) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
            start = 20.dp,
            end = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "header") { BirthdaysHeader() }

        when (state) {
            UiState.Loading -> item(key = "loading") { StateBox { LoadingView() } }
            UiState.Empty -> item(key = "empty") {
                StateBox {
                    EmptyView(
                        title = stringResource(R.string.birthdays_empty_title),
                        description = stringResource(R.string.birthdays_empty_desc),
                    )
                }
            }
            is UiState.Error -> item(key = "error") { StateBox { ErrorView(message = state.message) } }
            is UiState.Success -> birthdaysBody(
                data = state.data,
                viewMode = viewMode,
                visibleMonth = visibleMonth,
                today = today,
                onPersonClick = onPersonClick,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
            )
        }
    }
}

private fun LazyListScope.birthdaysBody(
    data: BirthdaysData,
    viewMode: BirthdayViewMode,
    visibleMonth: YearMonth,
    today: LocalDate,
    onPersonClick: (Long) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    when (viewMode) {
        BirthdayViewMode.YEAR -> item(key = "year") {
            YearGrid(year = visibleMonth.year, byMonthDay = data.byMonthDay)
        }
        BirthdayViewMode.MONTH -> item(key = "month") {
            MonthCalendar(
                month = visibleMonth,
                byMonthDay = data.byMonthDay,
                today = today,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onDayClick = onPersonClick,
            )
        }
        BirthdayViewMode.LIST -> items(data.all, key = { it.personId }) { birthday ->
            BirthdayRow(birthday = birthday, onClick = { onPersonClick(birthday.personId) })
        }
    }
}

@Composable
private fun BirthdaysHeader() {
    Column {
        Text(
            text = stringResource(R.string.birthdays_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.birthdays_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun StateBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
        content()
    }
}

/** A single upcoming-birthday row: avatar, "Birthday" label, serif name, "Turns X" and a day count. */
@Composable
private fun BirthdayRow(birthday: UpcomingBirthday, onClick: () -> Unit) {
    GlassPanel(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(
                initials = birthday.fullName.toInitials(),
                photoPath = birthday.photoPath,
                size = 64.dp,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CapsLabel(text = stringResource(R.string.birthday_label))
                Text(
                    text = birthday.fullName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                birthday.turningAge?.let { age ->
                    Text(
                        text = stringResource(R.string.birthday_turns, age),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            if (birthday.daysUntil == 0) {
                Text(
                    text = stringResource(R.string.birthday_today).uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                DayCountDisplay(
                    number = birthday.daysUntil,
                    unitLabel = stringResource(R.string.birthday_days_unit),
                    prefix = stringResource(R.string.birthday_in_days),
                )
            }
        }
    }
}

/** The 12-month overview grid for [BirthdayViewMode.YEAR]. */
@Composable
private fun YearGrid(year: Int, byMonthDay: Map<Int, List<UpcomingBirthday>>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        (0 until MONTHS_IN_YEAR step MONTHS_PER_ROW).forEach { rowStart ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                (rowStart until rowStart + MONTHS_PER_ROW).forEach { monthIndex ->
                    MiniMonthCard(
                        month = YearMonth.of(year, monthIndex + 1),
                        byMonthDay = byMonthDay,
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
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier.aspectRatio(0.8f), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            MiniMonthDots(month = month, byMonthDay = byMonthDay)
        }
    }
}

@Composable
private fun MiniMonthDots(month: YearMonth, byMonthDay: Map<Int, List<UpcomingBirthday>>) {
    val firstDayOffset = (month.atDay(1).dayOfWeek.value % DAYS_IN_WEEK)
    val cells = firstDayOffset + month.lengthOfMonth()
    val rows = (cells + DAYS_IN_WEEK - 1) / DAYS_IN_WEEK
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        (0 until rows).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (0 until DAYS_IN_WEEK).forEach { col ->
                    val cellIndex = row * DAYS_IN_WEEK + col
                    val day = cellIndex - firstDayOffset + 1
                    val hasBirthday = day in 1..month.lengthOfMonth() &&
                        byMonthDay.containsKey(monthDayKey(month.monthValue, day))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(
                                if (hasBirthday) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                            ),
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
    onDayClick: (Long) -> Unit,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            MonthHeader(month = month, onPreviousMonth = onPreviousMonth, onNextMonth = onNextMonth)
            Spacer(Modifier.height(12.dp))
            WeekdayHeaderRow()
            Spacer(Modifier.height(8.dp))
            MonthDayGrid(month = month, byMonthDay = byMonthDay, today = today, onDayClick = onDayClick)
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPreviousMonth: () -> Unit, onNextMonth: () -> Unit) {
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
            IconButton(onClick = onPreviousMonth) {
                Icon(
                    Icons.Filled.ChevronLeft,
                    contentDescription = stringResource(R.string.calendar_previous_month),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onNextMonth) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = stringResource(R.string.calendar_next_month),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WeekdayHeaderRow() {
    val weekdays = listOf(
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
    byMonthDay: Map<Int, List<UpcomingBirthday>>,
    today: LocalDate,
    onDayClick: (Long) -> Unit,
) {
    val firstDayOffset = (month.atDay(1).dayOfWeek.value % DAYS_IN_WEEK)
    val cells = firstDayOffset + month.lengthOfMonth()
    val rows = (cells + DAYS_IN_WEEK - 1) / DAYS_IN_WEEK
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (0 until rows).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until DAYS_IN_WEEK).forEach { col ->
                    val cellIndex = row * DAYS_IN_WEEK + col
                    val day = cellIndex - firstDayOffset + 1
                    Box(modifier = Modifier.weight(1f)) {
                        if (day in 1..month.lengthOfMonth()) {
                            DayCell(
                                day = day,
                                birthdays = byMonthDay[monthDayKey(month.monthValue, day)].orEmpty(),
                                isToday = today.year == month.year &&
                                    today.monthValue == month.monthValue &&
                                    today.dayOfMonth == day,
                                onDayClick = onDayClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    birthdays: List<UpcomingBirthday>,
    isToday: Boolean,
    onDayClick: (Long) -> Unit,
) {
    val hasBirthday = birthdays.isNotEmpty()
    val cellModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .clip(CircleShape)
        .then(
            if (hasBirthday) {
                Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
            } else {
                Modifier
            },
        )
        .then(
            if (isToday) {
                Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
            } else {
                Modifier
            },
        )
        .then(
            if (hasBirthday) {
                Modifier.clickable { onDayClick(birthdays.first().personId) }
            } else {
                Modifier
            },
        )
    Box(modifier = cellModifier, contentAlignment = Alignment.Center) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (hasBirthday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ViewModeMenu(current: BirthdayViewMode, onViewModeChange: (BirthdayViewMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(iconFor(current), contentDescription = stringResource(R.string.view_mode))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        viewModeOptions().forEach { (mode, labelRes) ->
            DropdownMenuItem(
                text = { Text(stringResource(labelRes)) },
                leadingIcon = { Icon(iconFor(mode), contentDescription = null) },
                onClick = {
                    onViewModeChange(mode)
                    expanded = false
                },
            )
        }
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
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        MenuRow(R.string.import_csv) { expanded = false; onImportCsv() }
        MenuRow(R.string.import_json) { expanded = false; onImportJson() }
        MenuRow(R.string.export_csv) { expanded = false; onExportCsv() }
        MenuRow(R.string.export_json) { expanded = false; onExportJson() }
        MenuRow(R.string.share_birthdays) { expanded = false; onShare() }
    }
}

@Composable
private fun MenuRow(labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(labelRes)) }, onClick = onClick)
}

private fun iconFor(mode: BirthdayViewMode): ImageVector = when (mode) {
    BirthdayViewMode.YEAR -> Icons.Outlined.CalendarViewMonth
    BirthdayViewMode.MONTH -> Icons.Outlined.CalendarMonth
    BirthdayViewMode.LIST -> Icons.AutoMirrored.Outlined.ViewList
}

private fun viewModeOptions(): List<Pair<BirthdayViewMode, Int>> = listOf(
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
): UpcomingBirthday = UpcomingBirthday(
    personId = personId,
    fullName = name,
    photoPath = null,
    birthday = LocalDate.of(1990, month, day),
    nextOccurrence = LocalDate.of(2026, month, day),
    daysUntil = daysUntil,
    turningAge = turningAge,
)

@Preview(name = "Phone", device = "spec:width=411dp,height=891dp")
@Preview(name = "Tablet", device = "spec:width=800dp,height=1280dp")
@Composable
private fun BirthdaysListPreview() {
    val data = BirthdaysData(
        all = listOf(
            previewBirthday(1, "Eleanor Vance", 10, 3, 0, 42),
            previewBirthday(2, "Marcus Thorne", 10, 12, 11, 35),
            previewBirthday(3, "Sophia Lin", 10, 25, 24, 28),
        ),
        upcoming30 = emptyList(),
        byMonthDay = emptyMap(),
    )
    PeopleHubTheme {
        BirthdaysContent(
            state = UiState.Success(data),
            viewMode = BirthdayViewMode.LIST,
            visibleMonth = YearMonth.of(2026, 10),
            today = LocalDate.of(2026, 10, 3),
            contentPadding = PaddingValues(0.dp),
            onPersonClick = {},
            onPreviousMonth = {},
            onNextMonth = {},
        )
    }
}

@Preview(name = "Phone Month", device = "spec:width=411dp,height=891dp")
@Composable
private fun BirthdaysMonthPreview() {
    val data = BirthdaysData(
        all = listOf(previewBirthday(1, "Eleanor Vance", 10, 3, 0, 42)),
        upcoming30 = emptyList(),
        byMonthDay = mapOf(
            monthDayKey(10, 3) to listOf(previewBirthday(1, "Eleanor Vance", 10, 3, 0, 42)),
            monthDayKey(10, 12) to listOf(previewBirthday(2, "Marcus Thorne", 10, 12, 11, 35)),
        ),
    )
    PeopleHubTheme {
        BirthdaysContent(
            state = UiState.Success(data),
            viewMode = BirthdayViewMode.MONTH,
            visibleMonth = YearMonth.of(2026, 10),
            today = LocalDate.of(2026, 10, 3),
            contentPadding = PaddingValues(0.dp),
            onPersonClick = {},
            onPreviousMonth = {},
            onNextMonth = {},
        )
    }
}
