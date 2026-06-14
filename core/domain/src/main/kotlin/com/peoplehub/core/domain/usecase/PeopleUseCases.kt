package com.peoplehub.core.domain.usecase

import com.peoplehub.core.domain.model.PeopleFilter
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.repository.PeopleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observes the filtered, sorted people directory. */
class GetPeopleUseCase
    @Inject
    constructor(
        private val repository: PeopleRepository,
    ) {
        operator fun invoke(filter: PeopleFilter): Flow<List<Person>> = repository.observePeople(filter)
    }

/** Observes a single person's full detail. */
class ObservePersonUseCase
    @Inject
    constructor(
        private val repository: PeopleRepository,
    ) {
        operator fun invoke(id: Long): Flow<Person?> = repository.observePerson(id)
    }

/** Observes every distinct tag for the filter chips. */
class ObserveAllTagsUseCase
    @Inject
    constructor(
        private val repository: PeopleRepository,
    ) {
        operator fun invoke(): Flow<List<String>> = repository.observeAllTags()
    }

/**
 * Validates and persists a person. Only the name (first name) is mandatory — the last name is
 * optional, since many people are tracked by a single name or nickname. Failures are surfaced as a
 * [Result] rather than thrown, so callers never crash on bad input.
 */
class UpsertPersonUseCase
    @Inject
    constructor(
        private val repository: PeopleRepository,
    ) {
        suspend operator fun invoke(person: Person): Result<Long> =
            runCatching {
                require(person.firstName.isNotBlank()) { "A name is required" }
                repository.upsertPerson(
                    person.copy(
                        firstName = person.firstName.trim(),
                        lastName = person.lastName.trim(),
                        tags =
                            person.tags
                                .map(String::trim)
                                .filter(String::isNotBlank)
                                .distinct(),
                        interests = person.interests.filter { it.key.isNotBlank() || it.value.isNotBlank() },
                    ),
                )
            }
    }

/** Deletes a person and their dependent data. */
class DeletePersonUseCase
    @Inject
    constructor(
        private val repository: PeopleRepository,
    ) {
        suspend operator fun invoke(id: Long) = repository.deletePerson(id)
    }

/**
 * Deletes every person and their dependent rows (tags, interests, check-ins). Events are kept but
 * become unlinked. Irreversible — intended for the Settings "danger zone".
 */
class DeleteAllPeopleUseCase
    @Inject
    constructor(
        private val repository: PeopleRepository,
    ) {
        suspend operator fun invoke() = repository.deleteAll()
    }
