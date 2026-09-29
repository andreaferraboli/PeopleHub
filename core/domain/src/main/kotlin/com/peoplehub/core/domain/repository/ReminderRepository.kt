package com.peoplehub.core.domain.repository

import com.peoplehub.core.domain.model.DueReminder
import com.peoplehub.core.domain.model.Reminder
import com.peoplehub.core.domain.model.ReminderCompletion
import com.peoplehub.core.domain.model.ReminderFilter
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * Read/write access to per-person relationship reminders.
 */
interface ReminderRepository {
    /** Observes a single person's reminders, soonest-due first. */
    fun observeRemindersForPerson(personId: Long): Flow<List<Reminder>>

    /** Observes the filtered list of reminders across all people, soonest-due first. */
    fun observeReminders(filter: ReminderFilter): Flow<List<Reminder>>

    /** One-shot read of a single reminder by [id], or `null` if it no longer exists. */
    suspend fun getReminder(id: Long): Reminder?

    /**
     * The enabled reminders whose `nextFireAt` is at or before [upTo], denormalised with the person's
     * name for the notification.
     *
     * Deliberately not gated on the person's `notificationsEnabled` opt-in: that toggle governs
     * check-in reminders and defaults to off, so gating here silenced reminders the user had created on
     * purpose. [upTo] is the end of the current day rather than the current instant — see
     * [com.peoplehub.core.domain.usecase.GetDueRemindersUseCase].
     */
    suspend fun getDueReminders(upTo: Instant): List<DueReminder>

    /** Inserts or updates a reminder and returns its id. */
    suspend fun upsertReminder(reminder: Reminder): Long

    /** Deletes a reminder by id. */
    suspend fun deleteReminder(id: Long)

    /** Pauses or resumes a reminder without changing its schedule. */
    suspend fun setEnabled(id: Long, enabled: Boolean)

    /** Records that a reminder fired at [firedAt] and schedules its (freshly jittered) [nextFireAt]. */
    suspend fun markFired(id: Long, firedAt: Instant, nextFireAt: Instant)

    /**
     * Appends one entry to reminder [id]'s completion history: it was done at [at], on local [day].
     * Every call adds an entry, so ticking a card twice in a day keeps both taps.
     */
    suspend fun recordCompletion(id: Long, at: Instant, day: LocalDate)

    /** Observes the completion history of every reminder, keyed by reminder id. */
    fun observeCompletions(): Flow<Map<Long, ReminderCompletion>>

    /** One-shot read of every reminder, used by backup/export. */
    suspend fun getAllReminders(): List<Reminder>

    /** Deletes every reminder — used by the "replace all" import strategy. */
    suspend fun deleteAll()
}
