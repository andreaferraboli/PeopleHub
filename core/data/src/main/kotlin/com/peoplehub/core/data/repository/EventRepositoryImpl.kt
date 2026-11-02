package com.peoplehub.core.data.repository

import com.peoplehub.core.data.cloud.Cloud
import com.peoplehub.core.data.cloud.CloudStore
import com.peoplehub.core.data.cloud.CloudWrite
import com.peoplehub.core.data.local.EventMedia
import com.peoplehub.core.data.local.LocalMedia
import com.peoplehub.core.data.mapping.DocumentMapper
import com.peoplehub.core.domain.model.EventFilter
import com.peoplehub.core.domain.model.EventTimeFilter
import com.peoplehub.core.domain.model.PersonEvent
import com.peoplehub.core.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Firestore-backed [EventRepository]. The cloud holds title, date, description, category and linked
 * person; the background image, its crop and the widget pin come from the phone's [LocalMedia].
 * Time partitioning and the "future ascending, then past descending" ordering are applied in memory.
 */
internal class EventRepositoryImpl
    @Inject
    constructor(
        private val store: CloudStore,
        private val media: LocalMedia,
        private val clock: Clock,
    ) : EventRepository {
        private val events: Flow<List<PersonEvent>> =
            combine(store.observe(Cloud.EVENTS), media.eventMedia, media.pinnedEventId) { docs, local, pinned ->
                docs.map { DocumentMapper.eventFromDoc(it, local[it.id], it.id == pinned, clock.zone) }
            }

        override fun observeEvents(filter: EventFilter): Flow<List<PersonEvent>> =
            events.map { all ->
                val now = LocalDateTime.now(clock)
                val matching =
                    all
                        .filter { filter.category == null || it.category == filter.category }
                        .filter { filter.personId == null || it.personId == filter.personId }
                val (future, past) = matching.partition { !it.dateTime.isBefore(now) }
                val orderedFuture = future.sortedBy { it.dateTime }
                val orderedPast = past.sortedByDescending { it.dateTime }
                when (filter.timeFilter) {
                    EventTimeFilter.ALL -> orderedFuture + orderedPast
                    EventTimeFilter.UPCOMING -> orderedFuture
                    EventTimeFilter.PAST -> orderedPast
                }
            }

        override fun observeEvent(id: String): Flow<PersonEvent?> =
            events.map { all -> all.firstOrNull { it.id == id } }.distinctUntilChanged()

        override fun observePinnedEvent(): Flow<PersonEvent?> =
            events.map { all -> all.firstOrNull { it.pinnedToWidget } }.distinctUntilChanged()

        override fun observeCategories(): Flow<List<String>> =
            events
                .map { all -> all.mapNotNull { it.category }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER) }
                .distinctUntilChanged()

        override fun observeBackgroundImages(): Flow<List<String>> =
            media.eventMedia.map { local ->
                local.values
                    .mapNotNull { it.imagePath }
                    .distinct()
                    .sorted()
            }

        override suspend fun getAllEvents(): List<PersonEvent> = events.first().sortedBy { it.dateTime }

        override suspend fun upsertEvent(event: PersonEvent): String {
            val id = event.id.ifEmpty { newDocumentId() }
            media.setEventMedia(id, EventMedia(event.backgroundImagePath, event.backgroundSourcePath, event.backgroundCrop))
            if (event.pinnedToWidget) {
                media.setPinnedEvent(id)
            } else if (media.pinnedEventId.first() == id) {
                media.setPinnedEvent(null)
            }
            store.commit(listOf(CloudWrite.Put(Cloud.EVENTS, id, DocumentMapper.eventToDoc(event, clock.zone))))
            return id
        }

        override suspend fun deleteEvent(id: String) {
            store.commit(listOf(CloudWrite.Tombstone(Cloud.EVENTS, id)))
            media.forget(emptyList(), listOf(id))
        }

        override suspend fun setPinned(eventId: String, pinned: Boolean) {
            when {
                pinned -> media.setPinnedEvent(eventId)
                media.pinnedEventId.first() == eventId -> media.setPinnedEvent(null)
            }
        }

        override suspend fun deleteAll() {
            val ids = store.observe(Cloud.EVENTS).first().map { it.id }
            store.commit(ids.map { CloudWrite.Tombstone(Cloud.EVENTS, it) })
            media.forget(emptyList(), ids)
        }
    }
