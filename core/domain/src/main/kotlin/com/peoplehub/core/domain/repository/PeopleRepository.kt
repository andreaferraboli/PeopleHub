package com.peoplehub.core.domain.repository

import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.domain.model.PeopleFilter
import com.peoplehub.core.domain.model.Person
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to people. Implementations expose only domain types and reactive [Flow]s for
 * reads plus `suspend` functions for writes — never Room types.
 */
interface PeopleRepository {
    /** Observes the filtered, sorted directory of people. */
    fun observePeople(filter: PeopleFilter): Flow<List<Person>>

    /** Observes a single person by [id], emitting `null` once it is deleted. */
    fun observePerson(id: Long): Flow<Person?>

    /** Observes the distinct set of tags used across all people, for the filter chips. */
    fun observeAllTags(): Flow<List<String>>

    /** One-shot read of a person, or `null` if absent. */
    suspend fun getPerson(id: Long): Person?

    /** One-shot read of every person, used by backup/export. */
    suspend fun getAllPeople(): List<Person>

    /** Inserts or updates [person] (including its tags and interests) and returns its id. */
    suspend fun upsertPerson(person: Person): Long

    /** Records that the person was seen, updating the denormalised last-check-in timestamp. */
    suspend fun updateLastCheckIn(personId: Long, lastCheckInEpochMillis: Long)

    /** Enables or disables notifications for every person in [personIds] in a single statement. */
    suspend fun bulkSetNotificationsEnabled(personIds: List<Long>, enabled: Boolean)

    /** Sets the "birthday only" flag for every person in [personIds]. */
    suspend fun bulkSetBirthdayOnly(personIds: List<Long>, birthdayOnly: Boolean)

    /**
     * Applies a check-in cadence to every person in [personIds]. A `null` [threshold] clears the
     * per-person override (falling back to the global default); a non-null value re-enables check-in
     * tracking for the affected people.
     */
    suspend fun bulkSetCheckInThreshold(personIds: List<Long>, threshold: CheckInThreshold?)

    /** Enables or disables ("never") check-in tracking for every person in [personIds]. */
    suspend fun bulkSetCheckInDisabled(personIds: List<Long>, disabled: Boolean)

    /** Deletes a person and all of their dependent rows (tags, interests, check-ins). */
    suspend fun deletePerson(id: Long)

    /** Deletes every person — used by the "replace all" import strategy. */
    suspend fun deleteAll()
}
