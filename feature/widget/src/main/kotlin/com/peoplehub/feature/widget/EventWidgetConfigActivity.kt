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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.peoplehub.core.domain.model.EventFilter
import com.peoplehub.core.domain.model.PersonEvent
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.theme.PeopleHubTheme
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

@Composable
private fun EventPicker(events: List<PersonEvent>, onPick: (PersonEvent) -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.widget_event_config_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (events.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.widget_event_config_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(events, key = { it.id }) { event ->
                        EventRow(event = event, onClick = { onPick(event) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: PersonEvent, onClick: () -> Unit) {
    GlassPanel(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            event.category?.let { CapsLabel(text = it) }
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = event.dateTime.toLocalDate().format(EventDateFormat),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
