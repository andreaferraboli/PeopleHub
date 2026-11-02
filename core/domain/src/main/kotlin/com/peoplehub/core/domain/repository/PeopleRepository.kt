package com.peoplehub.core.domain.repository

import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.domain.model.PeopleFilter
import com.peoplehub.core.domain.model.Person
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to people. Implementations expose only domain types and reactive [Flow]s for
 * reads plus `suspend` functions for writes — never Firestore types.
 */
interface PeopleRepository {
    /** Observes the filtered, sorted directory of people. */
    fun observePeople(filter: PeopleFilter): Flow<List<Person>>

    /** Observes a single person by [id], emitting `null` once it is deleted. */
    fun observePerson(id: String): Flow<Person?>

    /** Observes the distinct set of tags used across all people, for the filter chips. */
    fun observeAllTags(): Flow<List<String>>

    /** One-shot read of a person, or `null` if absent. */
    suspend fun getPerson(id: String): Person?

    /** One-shot read of every person, used by backup/export. */
    suspend fun getAllPeople(): List<Person>

    /** Inserts or updates [person] (including its tags and interests) and returns its id. */
    suspend fun upsertPerson(person: Person): String

    /**
     * Sets the denormalised last-check-in timestamp. A non-null value records that the person was
     * seen; a `null` value clears it (used when their most recent check-in has been deleted).
     */
    suspend fun updateLastCheckIn(personId: String, lastCheckInEpochMillis: Long?)

    /** Enables or disables notifications for every person in [personIds] at once. */
    suspend fun bulkSetNotificationsEnabled(personIds: List<String>, enabled: Boolean)

    /** Sets the "birthday only" flag for every person in [personIds]. */
    suspend fun bulkSetBirthdayOnly(personIds: List<String>, birthdayOnly: Boolean)

    /**
     * Applies a check-in cadence to every person in [personIds]. A `null` [threshold] clears the
     * per-person override (falling back to the global default); a non-null value re-enables check-in
     * tracking for the affected people.
     */
    suspend fun bulkSetCheckInThreshold(personIds: List<String>, threshold: CheckInThreshold?)

    /** Enables or disables ("never") check-in tracking for every person in [personIds]. */
    suspend fun bulkSetCheckInDisabled(personIds: List<String>, disabled: Boolean)

    /** Deletes a person and everything that depends on them (check-ins, reminders and their history); events
     * linked to them are kept but unlinked. */
    suspend fun deletePerson(id: String)

    /** Deletes every person, with the same cascade as [deletePerson]. */
    suspend fun deleteAll()
}
