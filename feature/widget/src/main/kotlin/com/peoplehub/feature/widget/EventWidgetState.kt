package com.peoplehub.feature.widget

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition

/**
 * The event a single placed widget shows. Stored per widget instance (not globally) so the home
 * screen can hold several event widgets at once, each pinned to a different event.
 */
internal val EventIdKey = longPreferencesKey("event_id")

/** Reads the event chosen for [id], or `null` when the widget has never been configured. */
internal suspend fun readEventId(context: Context, id: GlanceId): Long? =
    getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[EventIdKey]

/** Binds [eventId] to the widget [id]. */
internal suspend fun writeEventId(context: Context, id: GlanceId, eventId: Long) {
    updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
        prefs.toMutablePreferences().apply { this[EventIdKey] = eventId }
    }
}
