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
import com.peoplehub.core.domain.model.CheckInUrgency
import com.peoplehub.core.domain.navigation.DeepLinks
import kotlinx.coroutines.flow.first

/** Home-screen widget listing the people most overdue for a check-in. */
class CheckInWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val urgent = widgetEntryPoint(context).getUrgentCheckIns().invoke().first().take(MAX_ROWS)
        provideContent { CheckInWidgetContent(context, urgent) }
    }

    private companion object {
        const val MAX_ROWS = 3
    }
}

/** Receiver registering [CheckInWidget] with the framework. */
class CheckInWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CheckInWidget()
}

@Composable
private fun CheckInWidgetContent(context: Context, urgent: List<CheckInUrgency>) {
    WidgetScaffold(title = context.getString(R.string.widget_checkins_title)) {
        if (urgent.isEmpty()) {
            Text(
                text = context.getString(R.string.widget_checkins_empty),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
            )
        } else {
            urgent.forEach { item ->
                Column(
                    modifier = GlanceModifier
                        .padding(vertical = 4.dp)
                        .clickable(deepLinkAction(context, DeepLinks.person(item.person.id))),
                ) {
                    Text(
                        text = item.person.fullName,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium),
                    )
                    Text(
                        text = checkInLabel(context, item.daysSince),
                        style = TextStyle(color = WidgetGold),
                    )
                }
            }
        }
    }
}

private fun checkInLabel(context: Context, daysSince: Long?): String =
    if (daysSince == null) {
        context.getString(R.string.widget_never_seen)
    } else {
        context.getString(R.string.widget_days_ago, daysSince.toInt())
    }
