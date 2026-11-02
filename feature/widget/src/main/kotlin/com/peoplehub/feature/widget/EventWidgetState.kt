package com.peoplehub.feature.widget

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The event a single placed widget shows: its document id. Stored per widget instance (not globally)
 * so the home screen can hold several event widgets at once, each pinned to a different event.
 *
 * Instances configured while events had numeric ids kept them under `event_id`; that key is no longer
 * read, so those widgets fall back to the pinned event until they are configured again.
 */
internal val EventIdKey = stringPreferencesKey("event_doc_id")

/** Reads the event chosen for [id], or `null` when the widget has never been configured. */
internal suspend fun readEventId(context: Context, id: GlanceId): String? =
    getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[EventIdKey]

/** Binds [eventId] to the widget [id]. */
internal suspend fun writeEventId(context: Context, id: GlanceId, eventId: String) {
    updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
        prefs.toMutablePreferences().apply { this[EventIdKey] = eventId }
    }
}

/**
 * Reads widget data from the cloud, giving up after [WIDGET_READ_TIMEOUT_MILLIS]: offline (or before
 * the first answer from the server) the widget renders [fallback] instead of hanging its update.
 */
internal suspend fun <T> widgetRead(fallback: T, read: suspend () -> T): T =
    withTimeoutOrNull(WIDGET_READ_TIMEOUT_MILLIS) { read() } ?: fallback

private const val WIDGET_READ_TIMEOUT_MILLIS = 20_000L
