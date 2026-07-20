package com.peoplehub.core.domain.repository

import com.peoplehub.core.domain.model.DueReminder
import com.peoplehub.core.domain.model.Reminder
import com.peoplehub.core.domain.model.ReminderFilter
import kotlinx.coroutines.flow.Flow
import java.time.Instant

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
     * The enabled reminders that are due at [now] (their `nextFireAt` has passed) and whose person
     * currently has notifications enabled, denormalised with the person's name for the notification.
     */
    suspend fun getDueReminders(now: Instant): List<DueReminder>

    /** Inserts or updates a reminder and returns its id. */
    suspend fun upsertReminder(reminder: Reminder): Long

    /** Deletes a reminder by id. */
    suspend fun deleteReminder(id: Long)

    /** Pauses or resumes a reminder without changing its schedule. */
    suspend fun setEnabled(id: Long, enabled: Boolean)

    /** Records that a reminder fired at [firedAt] and schedules its (freshly jittered) [nextFireAt]. */
    suspend fun markFired(id: Long, firedAt: Instant, nextFireAt: Instant)

    /** One-shot read of every reminder, used by backup/export. */
    suspend fun getAllReminders(): List<Reminder>

    /** Deletes every reminder — used by the "replace all" import strategy. */
    suspend fun deleteAll()
}
