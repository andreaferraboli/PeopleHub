package com.peoplehub.core.domain.usecase

import com.peoplehub.core.domain.model.EventFilter
import com.peoplehub.core.domain.model.PersonEvent
import com.peoplehub.core.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observes the filtered, ordered list of events. */
class GetEventsUseCase
    @Inject
    constructor(
        private val repository: EventRepository,
    ) {
        operator fun invoke(filter: EventFilter): Flow<List<PersonEvent>> = repository.observeEvents(filter)
    }

/** Observes a single event by id. */
class ObserveEventUseCase
    @Inject
    constructor(
        private val repository: EventRepository,
    ) {
        operator fun invoke(id: Long): Flow<PersonEvent?> = repository.observeEvent(id)
    }

/** Observes the event currently pinned to the widget, if any. */
class GetPinnedEventUseCase
    @Inject
    constructor(
        private val repository: EventRepository,
    ) {
        operator fun invoke(): Flow<PersonEvent?> = repository.observePinnedEvent()
    }

/** Observes the distinct set of event categories for the filter chips. */
class ObserveEventCategoriesUseCase
    @Inject
    constructor(
        private val repository: EventRepository,
    ) {
        operator fun invoke(): Flow<List<String>> = repository.observeCategories()
    }

/** Validates and persists an event. The title is mandatory. */
class AddEventUseCase
    @Inject
    constructor(
        private val repository: EventRepository,
    ) {
        suspend operator fun invoke(event: PersonEvent): Result<Long> =
            runCatching {
                require(event.title.isNotBlank()) { "Event title is required" }
                repository.upsertEvent(
                    event.copy(
                        title = event.title.trim(),
                        category = event.category?.trim()?.takeIf(String::isNotBlank),
                        description = event.description?.takeIf(String::isNotBlank),
                    ),
                )
            }
    }

/** Deletes an event by id. */
class DeleteEventUseCase
    @Inject
    constructor(
        private val repository: EventRepository,
    ) {
        suspend operator fun invoke(id: Long) = repository.deleteEvent(id)
    }

/** Pins or unpins an event for the widget (pinning is exclusive). */
class SetEventPinnedUseCase
    @Inject
    constructor(
        private val repository: EventRepository,
    ) {
        suspend operator fun invoke(eventId: Long, pinned: Boolean) = repository.setPinned(eventId, pinned)
    }
