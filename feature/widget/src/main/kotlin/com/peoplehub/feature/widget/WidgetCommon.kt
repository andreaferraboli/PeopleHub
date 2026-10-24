package com.peoplehub.feature.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.peoplehub.core.ui.theme.LabelCaps
import com.peoplehub.core.ui.theme.VellutoAccent
import com.peoplehub.core.ui.theme.VellutoRadii
import com.peoplehub.core.ui.theme.VellutoSpacing
import com.peoplehub.core.ui.theme.VellutoType
import com.peoplehub.core.ui.theme.vellutoColors
import dagger.hilt.android.EntryPointAccessors
import androidx.glance.color.ColorProvider as DayNightColor

/*
 * Glance does not run inside the Compose theme (it renders RemoteViews), so the widgets read the
 * Velluto tokens directly: the light and dark colour sets are built once from `vellutoColors()` and
 * paired as day/night providers, which the launcher switches with the system theme. Glance cannot
 * load font resources either, so widgets use the system sans (and the system serif for the event
 * title, standing in for Instrument Serif).
 */
private val Day = vellutoColors(dark = false)
private val Night = vellutoColors(dark = true)

/** Velluto radii and spacing, read from the theme's data classes (no composition needed). */
internal val WidgetRadii = VellutoRadii()
internal val WidgetSpacing = VellutoSpacing()
internal val WidgetType = VellutoType()

/** The accent as a fill or as text on dark media (photos, the always-dark hero card). */
internal val WidgetGold = ColorProvider(VellutoAccent)

/** The accent as readable text on the widget surfaces (deepened gold in light, AA). */
internal val WidgetAccentText = DayNightColor(day = Day.accentText, night = Night.accentText)

/** Widget card background (`surface`). */
internal val WidgetSurface = DayNightColor(day = Day.surface, night = Night.surface)

/** Inner rows (`surface2`), like the grouped rows inside Velluto cards. */
internal val WidgetSurface2 = DayNightColor(day = Day.surface2, night = Night.surface2)

/** Primary and secondary text on the widget surfaces. */
internal val WidgetText = DayNightColor(day = Day.text, night = Night.text)
internal val WidgetText2 = DayNightColor(day = Day.text2, night = Night.text2)

/** The always-dark hero card behind an event without a photo, and the text drawn on it or on photos. */
internal val WidgetHeroBg = DayNightColor(day = Day.heroBg, night = Night.heroBg)
internal val WidgetOnMedia = ColorProvider(Night.onMedia)
internal val WidgetOnMediaMuted = ColorProvider(Night.onMediaMuted)

/**
 * Text sizes mirroring the Velluto roles the widgets use (titleSmall 15, bodySmall 13, the event
 * title between headlineSmall and titleMedium). The Material typography is internal to the theme and
 * Glance cannot read it, so the sizes are restated here.
 */
internal object WidgetTextSize {
    val caps = LabelCaps.fontSize
    val name = 15.sp
    val label = 13.sp
    val eventTitle = 20.sp
}

/** Resolves the Hilt [WidgetEntryPoint] for use inside `provideGlance`. */
internal fun widgetEntryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

/** Builds a package-scoped `ACTION_VIEW` deep-link action for a widget row tap. */
internal fun deepLinkAction(context: Context, uri: String) =
    actionStartActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            `package` = context.packageName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        },
    )

/**
 * Common widget shell: a Velluto card (`surface`, large radius on Android 12+) with the accent caps
 * header and the supplied [content]. Colours are day/night pairs, so it follows the system dark/light
 * mode automatically.
 */
@Composable
internal fun WidgetScaffold(
    title: String,
    headerAction: androidx.glance.action.Action? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(WidgetSurface)
                .cornerRadius(WidgetRadii.cardLarge)
                .padding(WidgetSpacing.ml)
                .let { if (headerAction != null) it.clickable(headerAction) else it },
    ) {
        Text(
            text = title.uppercase(),
            maxLines = 1,
            style = TextStyle(color = WidgetAccentText, fontWeight = FontWeight.Bold, fontSize = WidgetTextSize.caps),
            modifier = GlanceModifier.padding(horizontal = WidgetSpacing.xxs),
        )
        Spacer(GlanceModifier.height(WidgetSpacing.s))
        content()
    }
}

/**
 * One widget list row: an inner `surface2` tile (radius on Android 12+) with the [primary] text on
 * the left and the accent [trailing] label on the right, opening [action] when tapped.
 */
@Composable
internal fun WidgetRow(
    primary: String,
    trailing: String,
    action: androidx.glance.action.Action,
) {
    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .background(WidgetSurface2)
                .cornerRadius(WidgetRadii.tile)
                .padding(horizontal = WidgetSpacing.m, vertical = WidgetSpacing.s)
                .clickable(action),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = primary,
            maxLines = 1,
            style = TextStyle(color = WidgetText, fontWeight = FontWeight.Medium, fontSize = WidgetTextSize.name),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            text = trailing,
            maxLines = 1,
            style = TextStyle(color = WidgetAccentText, fontWeight = FontWeight.Medium, fontSize = WidgetTextSize.label),
            modifier = GlanceModifier.padding(start = WidgetSpacing.s),
        )
    }
}

/** The muted message shown when a list widget has nothing to list. */
@Composable
internal fun WidgetEmptyText(text: String) {
    Text(
        text = text,
        style = TextStyle(color = WidgetText2, fontSize = WidgetTextSize.label),
        modifier = GlanceModifier.padding(horizontal = WidgetSpacing.xxs),
    )
}

/** The gap between two widget rows (Glance has no spaced arrangement). */
@Composable
internal fun WidgetRowGap() {
    Spacer(GlanceModifier.height(WidgetSpacing.xs))
}
