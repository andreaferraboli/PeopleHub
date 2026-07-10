package com.peoplehub.core.domain.widget

/**
 * Requests a redraw of the home-screen widgets after data they display has changed.
 *
 * Feature modules can't reach the widget module (they sit side by side), and the domain layer must
 * stay free of Android types — so the app module owns the implementation and injects it here.
 */
fun interface WidgetRefresher {
    /** Schedules an immediate refresh of every PeopleHub widget. */
    fun refresh()
}
