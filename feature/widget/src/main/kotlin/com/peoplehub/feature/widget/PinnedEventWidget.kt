package com.peoplehub.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.peoplehub.core.domain.model.PersonEvent
import com.peoplehub.core.domain.navigation.DeepLinks
import com.peoplehub.core.domain.util.DateCalculations
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Home-screen widget showing the user's pinned event with its elapsed/remaining day counter. */
class PinnedEventWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = widgetEntryPoint(context)
        val event = entry.getPinnedEvent().invoke().first()
        val today = LocalDate.now(entry.clock())
        provideContent { PinnedEventWidgetContent(context, event, today) }
    }
}

/** Receiver registering [PinnedEventWidget] with the framework. */
class PinnedEventWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PinnedEventWidget()
}

@Composable
private fun PinnedEventWidgetContent(context: Context, event: PersonEvent?, today: LocalDate) {
    WidgetScaffold(title = context.getString(R.string.widget_event_title)) {
        if (event == null) {
            Text(
                text = context.getString(R.string.widget_event_empty),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
            )
        } else {
            Column(
                modifier =
                    GlanceModifier
                        .padding(vertical = 4.dp)
                        .clickable(deepLinkAction(context, DeepLinks.event(event.id))),
            ) {
                Text(
                    text = event.title,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold),
                )
                Text(
                    text = eventDayLabel(context, DateCalculations.signedDaysFromToday(event.dateTime.toLocalDate(), today)),
                    style = TextStyle(color = WidgetGold),
                )
            }
        }
    }
}

private fun eventDayLabel(context: Context, signedDays: Long): String =
    when {
        signedDays == 0L -> context.getString(R.string.widget_today)
        signedDays > 0L -> context.getString(R.string.widget_in_days, signedDays.toInt())
        else -> context.getString(R.string.widget_days_ago, (-signedDays).toInt())
    }
