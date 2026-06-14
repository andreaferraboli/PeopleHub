package com.peoplehub.feature.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.android.EntryPointAccessors

/** Champagne-gold accent for widget headers and emphasised numbers. */
internal val WidgetGold = ColorProvider(Color(0xFFF2CA50))

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
 * Common widget shell: a rounded surface with the brand header and the supplied [content], wrapped
 * in [GlanceTheme] so it follows the system dark/light mode automatically.
 */
@Composable
internal fun WidgetScaffold(
    title: String,
    headerAction: androidx.glance.action.Action? = null,
    content: @Composable () -> Unit,
) {
    GlanceTheme {
        Column(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.surface)
                    .cornerRadius(16.dp)
                    .padding(12.dp)
                    .let { if (headerAction != null) it.clickable(headerAction) else it },
        ) {
            Text(
                text = title.uppercase(),
                style = TextStyle(color = WidgetGold, fontWeight = FontWeight.Bold, fontSize = 12.sp),
            )
            content()
        }
    }
}
