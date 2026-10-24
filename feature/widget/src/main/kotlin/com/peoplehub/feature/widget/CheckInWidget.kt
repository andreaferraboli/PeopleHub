package com.peoplehub.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import com.peoplehub.core.domain.model.CheckInUrgency
import com.peoplehub.core.domain.navigation.DeepLinks
import kotlinx.coroutines.flow.first

/** Home-screen widget listing the people most overdue for a check-in. */
class CheckInWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val urgent =
            widgetEntryPoint(context)
                .getUrgentCheckIns()
                .invoke()
                .first()
                .take(MAX_ROWS)
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
            WidgetEmptyText(context.getString(R.string.widget_checkins_empty))
        } else {
            urgent.forEachIndexed { index, item ->
                if (index > 0) WidgetRowGap()
                WidgetRow(
                    primary = item.person.fullName,
                    trailing = checkInLabel(context, item.daysSince),
                    action = deepLinkAction(context, DeepLinks.person(item.person.id)),
                )
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
