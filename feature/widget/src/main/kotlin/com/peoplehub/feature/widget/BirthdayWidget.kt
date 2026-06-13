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
import com.peoplehub.core.domain.model.UpcomingBirthday
import com.peoplehub.core.domain.navigation.DeepLinks
import kotlinx.coroutines.flow.first

/** Home-screen widget listing the next three upcoming birthdays with days remaining. */
class BirthdayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val birthdays = widgetEntryPoint(context).getUpcomingBirthdays().invoke().first().take(MAX_ROWS)
        provideContent { BirthdayWidgetContent(context, birthdays) }
    }

    private companion object {
        const val MAX_ROWS = 3
    }
}

/** Receiver registering [BirthdayWidget] with the framework. */
class BirthdayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BirthdayWidget()
}

@Composable
private fun BirthdayWidgetContent(context: Context, birthdays: List<UpcomingBirthday>) {
    WidgetScaffold(title = context.getString(R.string.widget_birthdays_title)) {
        if (birthdays.isEmpty()) {
            Text(
                text = context.getString(R.string.widget_birthdays_empty),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
            )
        } else {
            birthdays.forEach { birthday ->
                Column(
                    modifier = GlanceModifier
                        .padding(vertical = 4.dp)
                        .clickable(deepLinkAction(context, DeepLinks.person(birthday.personId))),
                ) {
                    Text(
                        text = birthday.fullName,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium),
                    )
                    Text(
                        text = birthdayDaysLabel(context, birthday.daysUntil),
                        style = TextStyle(color = WidgetGold),
                    )
                }
            }
        }
    }
}

private fun birthdayDaysLabel(context: Context, daysUntil: Int): String = when (daysUntil) {
    0 -> context.getString(R.string.widget_today)
    1 -> context.getString(R.string.widget_tomorrow)
    else -> context.getString(R.string.widget_in_days, daysUntil)
}
