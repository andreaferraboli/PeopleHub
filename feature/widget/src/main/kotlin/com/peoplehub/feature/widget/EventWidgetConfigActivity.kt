package com.peoplehub.feature.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.peoplehub.core.domain.model.EventFilter
import com.peoplehub.core.domain.model.PersonEvent
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.DateTile
import com.peoplehub.core.ui.components.EmptyView
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.VellutoBackground
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.riseIn
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * Configuration activity launched when an event widget is added (and again when it is reconfigured):
 * the user picks which event that widget instance shows.
 *
 * The result must be reported back with the widget id, otherwise the launcher discards the widget.
 * It is therefore pre-set to `RESULT_CANCELED`, so backing out leaves nothing behind.
 */
class EventWidgetConfigActivity : ComponentActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        appWidgetId =
            intent?.extras?.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val events = widgetEntryPoint(this).getEvents().invoke(EventFilter())

        setContent {
            PeopleHubTheme {
                val eventList by produceState(initialValue = emptyList<PersonEvent>(), events) {
                    events.collect { value = it }
                }
                EventPicker(events = eventList, onPick = ::bindEvent)
            }
        }
    }

    /** Stores the chosen event on this widget instance, redraws it, and hands the id back. */
    private fun bindEvent(event: PersonEvent) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@EventWidgetConfigActivity).getGlanceIdBy(appWidgetId)
            writeEventId(this@EventWidgetConfigActivity, glanceId, event.id)
            PinnedEventWidget().update(this@EventWidgetConfigActivity, glanceId)

            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
        }
    }
}

private val EventDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
private val TileMonthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM")

/** Rows that play the cascading entrance; the rest of a long list appears in place. */
private const val ENTRANCE_ROWS = 8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventPicker(events: List<PersonEvent>, onPick: (PersonEvent) -> Unit) {
    val spacing = PeopleHubTheme.spacing
    VellutoBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { PeopleHubTopBar(title = stringResource(R.string.widget_event_config_title)) },
        ) { innerPadding ->
            if (events.isEmpty()) {
                Box(
                    modifier = Modifier.padding(innerPadding).fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyView(
                        title = stringResource(R.string.widget_event_config_empty),
                        description = "",
                        icon = VellutoIcons.CalendarX,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(innerPadding).fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.m),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    itemsIndexed(events, key = { _, event -> event.id }) { index, event ->
                        EventRow(
                            event = event,
                            onClick = { onPick(event) },
                            modifier = if (index < ENTRANCE_ROWS) Modifier.riseIn(index) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

/** A glass row: date tile, category caps, title and full date, with a chevron. */
@Composable
private fun EventRow(event: PersonEvent, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    val date = event.dateTime.toLocalDate()
    GlassPanel(modifier = modifier.fillMaxWidth(), shape = VellutoShapes.Card) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(spacing.ml),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.ml),
        ) {
            DateTile(month = date.format(TileMonthFormat), day = date.dayOfMonth.toString())
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                event.category?.let { CapsLabel(text = it) }
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = c.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = date.format(EventDateFormat),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.text2,
                )
            }
            Icon(
                imageVector = VellutoIcons.ChevronRight,
                contentDescription = null,
                tint = c.text2,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
