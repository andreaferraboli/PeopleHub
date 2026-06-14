package com.peoplehub.core.domain.repository

import com.peoplehub.core.domain.model.EventFilter
import com.peoplehub.core.domain.model.PersonEvent
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to personal events.
 */
interface EventRepository {
    /** Observes the filtered list of events (ordering applied by the implementation). */
    fun observeEvents(filter: EventFilter): Flow<List<PersonEvent>>

    /** Observes a single event by [id]. */
    fun observeEvent(id: Long): Flow<PersonEvent?>

    /** Observes the single event currently pinned to the widget, if any. */
    fun observePinnedEvent(): Flow<PersonEvent?>

    /** Observes the distinct set of categories in use, for the filter chips. */
    fun observeCategories(): Flow<List<String>>

    /** Observes the distinct set of card background images already used, for reuse when editing. */
    fun observeBackgroundImages(): Flow<List<String>>

    /** One-shot read of every event, used by backup/export. */
    suspend fun getAllEvents(): List<PersonEvent>

    /** Inserts or updates an event and returns its id. */
    suspend fun upsertEvent(event: PersonEvent): Long

    /** Deletes an event by id. */
    suspend fun deleteEvent(id: Long)

    /**
     * Pins or unpins [eventId] for the widget. Pinning is exclusive: pinning a new event unpins any
     * previously pinned one.
     */
    suspend fun setPinned(eventId: Long, pinned: Boolean)

    /** Deletes every event — used by the "replace all" import strategy. */
    suspend fun deleteAll()
}
