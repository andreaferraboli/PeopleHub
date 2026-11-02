package com.peoplehub.core.database.sync

/**
 * Names of the Firestore collections under `users/{uid}/`, as written in `docs/firestore-schema.md`.
 * They are also the `collection` values of `sync_outbox`, so the triggers and the sync engine agree
 * on them by construction.
 */
object SyncCollections {
    const val PEOPLE: String = "people"
    const val REMINDERS: String = "reminders"
    const val EVENTS: String = "events"
    const val CHECK_INS: String = "checkIns"
    const val REMINDER_COMPLETIONS: String = "reminderCompletions"
    const val SETTINGS: String = "settings"

    /**
     * The order changes are applied and pushed in: parents before the children that reference them,
     * so a child normally finds its parent already in place.
     */
    val ORDERED: List<String> = listOf(PEOPLE, REMINDERS, EVENTS, CHECK_INS, REMINDER_COMPLETIONS)
}
