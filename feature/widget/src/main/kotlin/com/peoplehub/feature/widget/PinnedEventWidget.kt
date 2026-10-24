package com.peoplehub.feature.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
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
    if (event == null) {
        EmptyEventWidget(context)
    } else {
        Box(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .background(WidgetHeroBg)
                    .cornerRadius(WidgetRadii.hero)
                    .clickable(deepLinkAction(context, DeepLinks.event(event.id))),
        ) {
            if (background != null) {
                Image(
                    provider = ImageProvider(background),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.fillMaxSize(),
                )
            }
            EventDetails(context, event, today)
        }
    }
}

/**
 * Category, title and day counter, stacked at the bottom of the card as they are on the in-app
 * cover. The card is always dark (the baked-in photo scrim, or the velvet hero background when there
 * is no photo), so the media text colours hold whatever the launcher's theme is.
 */
@Composable
private fun EventDetails(context: Context, event: PersonEvent, today: LocalDate) {
    val signedDays = DateCalculations.signedDaysFromToday(event.dateTime.toLocalDate(), today)

    Column(
        modifier = GlanceModifier.fillMaxSize().padding(WidgetSpacing.l),
        verticalAlignment = Alignment.Vertical.Bottom,
    ) {
        event.category?.let { category ->
            Text(
                text = category.uppercase(),
                maxLines = 1,
                style = TextStyle(color = WidgetGold, fontWeight = FontWeight.Bold, fontSize = WidgetType.tileCaps.fontSize),
            )
        }
        Text(
            text = event.title,
            maxLines = 2,
            style =
                TextStyle(
                    color = WidgetOnMedia,
                    fontSize = WidgetTextSize.eventTitle,
                    fontFamily = FontFamily.Serif,
                ),
        )
        DayCount(context, signedDays)
    }
}

/** The big accent number plus its unit, mirroring the app's `DayCountDisplay`. */
@Composable
private fun DayCount(context: Context, signedDays: Long) {
    if (signedDays == 0L) {
        Text(
            text = context.getString(R.string.widget_today),
            style = TextStyle(color = WidgetGold, fontWeight = FontWeight.Medium, fontSize = WidgetType.kpiTile.fontSize),
        )
        return
    }
    val future = signedDays > 0L
    // Future events are the ones you're counting down to, so they get the accent emphasis.
    val numberColor = if (future) WidgetGold else WidgetOnMedia
    val unitStyle = TextStyle(color = WidgetOnMediaMuted, fontWeight = FontWeight.Medium, fontSize = WidgetTextSize.caps)

    Row(verticalAlignment = Alignment.Vertical.Bottom) {
        if (future) {
            Text(
                text = context.getString(R.string.widget_event_in).uppercase(),
                style = unitStyle,
                modifier = GlanceModifier.padding(end = WidgetSpacing.xs, bottom = WidgetSpacing.xs),
            )
        }
        Text(
            text = signedDays.absoluteValue.toString(),
            style = TextStyle(color = numberColor, fontWeight = FontWeight.Medium, fontSize = WidgetType.kpiSmall.fontSize),
        )
        Text(
            text = context.getString(if (future) R.string.widget_event_days else R.string.widget_event_days_ago).uppercase(),
            style = unitStyle,
            modifier = GlanceModifier.padding(start = WidgetSpacing.xs, bottom = WidgetSpacing.xs),
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
                .background(WidgetSurface)
                .cornerRadius(WidgetRadii.hero)
                .padding(WidgetSpacing.l),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = context.getString(R.string.widget_event_empty),
            style = TextStyle(color = WidgetText2, fontSize = WidgetTextSize.label, textAlign = TextAlign.Center),
        )
    }
}
