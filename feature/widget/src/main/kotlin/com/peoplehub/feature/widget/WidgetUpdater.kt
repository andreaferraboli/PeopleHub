package com.peoplehub.feature.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/**
 * Refreshes every PeopleHub widget. Called by the periodic widget worker and after any significant
 * data change (check-in, event edit) so the home screen stays current.
 */
object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        BirthdayWidget().updateAll(context)
        CheckInWidget().updateAll(context)
        PinnedEventWidget().updateAll(context)
    }
}
