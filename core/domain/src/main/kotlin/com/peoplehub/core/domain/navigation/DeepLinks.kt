package com.peoplehub.core.domain.navigation

/**
 * Canonical deep-link URIs shared by navigation, notifications and widgets. Kept in the domain layer
 * as plain strings so every layer can build the same links without depending on the UI.
 *
 * Example: `peoplehub://person/42` opens that person's detail screen.
 */
object DeepLinks {
    const val SCHEME: String = "peoplehub"
    const val HOST_PERSON: String = "person"
    const val HOST_EVENT: String = "event"
    const val HOST_BIRTHDAYS: String = "birthdays"
    const val HOST_CHECK_INS: String = "checkins"

    /** Deep link to a person's detail screen. */
    fun person(id: Long): String = "$SCHEME://$HOST_PERSON/$id"

    /** Deep link to an event's detail screen. */
    fun event(id: Long): String = "$SCHEME://$HOST_EVENT/$id"

    /** Deep link to the birthdays screen. */
    fun birthdays(): String = "$SCHEME://$HOST_BIRTHDAYS"
}
