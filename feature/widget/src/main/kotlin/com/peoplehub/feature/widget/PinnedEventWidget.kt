package com.peoplehub.feature.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.peoplehub.core.domain.model.PersonEvent
import com.peoplehub.core.domain.navigation.DeepLinks
import com.peoplehub.core.domain.util.DateCalculations
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.absoluteValue

/**
 * Home-screen widget showing one event with its background photo and elapsed/remaining day counter,
 * rendered to match the event card in the app.
 *
 * Which event is shown is chosen per widget instance in [EventWidgetConfigActivity], so several can
 * sit on the home screen at once. Widgets placed before per-instance selection existed carry no
 * choice and fall back to the app's pinned event.
 */
class PinnedEventWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = widgetEntryPoint(context)
        val eventId = readEventId(context, id)
        val event =
            if (eventId != null) {
                entry.observeEvent().invoke(eventId).first()
            } else {
                entry.getPinnedEvent().invoke().first()
            }
        val today = LocalDate.now(entry.clock())
        val background = event?.backgroundImagePath?.let { loadEventBackground(it) }
        provideContent { EventWidgetContent(context, event, today, background) }
    }
}

/** Receiver registering [PinnedEventWidget] with the framework. */
class PinnedEventWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PinnedEventWidget()
}

@Composable
private fun EventWidgetContent(context: Context, event: PersonEvent?, today: LocalDate, background: Bitmap?) {
    GlanceTheme {
        if (event == null) {
            EmptyEventWidget(context)
        } else {
            Box(
                modifier =
                    GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(16.dp)
                        .clickable(deepLinkAction(context, DeepLinks.event(event.id))),
            ) {
                if (background == null) {
                    Box(modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.surface)) {}
                } else {
                    Image(
                        provider = ImageProvider(background),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = GlanceModifier.fillMaxSize(),
                    )
                }
                EventDetails(context, event, today, overPhoto = background != null)
            }
        }
    }
}

/** Category, title and day counter, stacked at the bottom of the card as they are in the app. */
@Composable
private fun EventDetails(context: Context, event: PersonEvent, today: LocalDate, overPhoto: Boolean) {
    // Over a photo the baked-in scrim guarantees contrast, so white-on-dark holds whatever the
    // launcher's theme is; without one we defer to the theme's own foreground colour.
    val primary = if (overPhoto) WidgetWhite else GlanceTheme.colors.onSurface
    val signedDays = DateCalculations.signedDaysFromToday(event.dateTime.toLocalDate(), today)

    Column(
        modifier = GlanceModifier.fillMaxSize().padding(14.dp),
        verticalAlignment = Alignment.Vertical.Bottom,
    ) {
        event.category?.let { category ->
            Text(
                text = category.uppercase(),
                style = TextStyle(color = WidgetGold, fontWeight = FontWeight.Bold, fontSize = 10.sp),
            )
        }
        Text(
            text = event.title,
            maxLines = 2,
            style = TextStyle(color = primary, fontWeight = FontWeight.Bold, fontSize = 18.sp),
        )
        DayCount(context, signedDays, primary)
    }
}

/** The big gold number plus its unit, mirroring the app's `DayCountDisplay`. */
@Composable
private fun DayCount(context: Context, signedDays: Long, primary: ColorProvider) {
    if (signedDays == 0L) {
        Text(
            text = context.getString(R.string.widget_today),
            style = TextStyle(color = WidgetGold, fontWeight = FontWeight.Bold, fontSize = 22.sp),
        )
        return
    }
    val future = signedDays > 0L
    // Future events are the ones you're counting down to, so they get the gold emphasis.
    val numberColor = if (future) WidgetGold else primary

    Row(verticalAlignment = Alignment.Vertical.Bottom) {
        if (future) {
            Text(
                text = context.getString(R.string.widget_event_in),
                style = TextStyle(color = primary, fontSize = 12.sp),
                modifier = GlanceModifier.padding(end = 4.dp, bottom = 3.dp),
            )
        }
        Text(
            text = signedDays.absoluteValue.toString(),
            style = TextStyle(color = numberColor, fontWeight = FontWeight.Bold, fontSize = 28.sp),
        )
        Text(
            text = context.getString(if (future) R.string.widget_event_days else R.string.widget_event_days_ago),
            style = TextStyle(color = primary, fontSize = 12.sp),
            modifier = GlanceModifier.padding(start = 4.dp, bottom = 3.dp),
        )
    }
}

/** Shown when the widget has no event yet, or the event it pointed at was deleted. */
@Composable
private fun EmptyEventWidget(context: Context) {
    Box(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = context.getString(R.string.widget_event_empty),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
        )
    }
}

/** Text colour used on top of a scrimmed photo, where the launcher's theme must not win. */
private val WidgetWhite = ColorProvider(Color.White)
