package com.peoplehub.core.data.repository

import com.peoplehub.core.data.cloud.Cloud
import com.peoplehub.core.data.cloud.CloudDoc
import com.peoplehub.core.data.cloud.CloudStore
import com.peoplehub.core.data.cloud.CloudWrite
import com.peoplehub.core.data.mapping.DocumentMapper
import com.peoplehub.core.data.mapping.string
import com.peoplehub.core.domain.model.DueReminder
import com.peoplehub.core.domain.model.Reminder
import com.peoplehub.core.domain.model.ReminderCompletion
import com.peoplehub.core.domain.model.ReminderDoneEntry
import com.peoplehub.core.domain.model.ReminderFilter
import com.peoplehub.core.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Firestore-backed [ReminderRepository]. Reminders of people that no longer exist are hidden, lists are
 * soonest-due first, and deleting a reminder tombstones its completion history too (as the MCP does).
 */
internal class ReminderRepositoryImpl
    @Inject
    constructor(
        private val store: CloudStore,
    ) : ReminderRepository {
        /** Live reminders with their person's display name, soonest-due first. */
        private val reminders: Flow<List<Pair<Reminder, String>>> =
            combine(store.observe(Cloud.REMINDERS), store.observe(Cloud.PEOPLE)) { docs, people ->
                val names = people.associate { it.id to it.displayName() }
                docs
                    .mapNotNull(DocumentMapper::reminderFromDoc)
                    .mapNotNull { reminder -> names[reminder.personId]?.let { reminder to it } }
                    .sortedBy { (reminder, _) -> reminder.nextFireAt }
            }

        override fun observeRemindersForPerson(personId: String): Flow<List<Reminder>> =
            reminders.map { all -> all.map { it.first }.filter { it.personId == personId } }

        override fun observeReminders(filter: ReminderFilter): Flow<List<Reminder>> =
            reminders.map { all ->
                all
                    .map { it.first }
                    .filter { filter.personId == null || it.personId == filter.personId }
                    .filter { filter.category == null || it.category == filter.category }
                    .filter { !filter.onlyEnabled || it.enabled }
            }

        override suspend fun getReminder(id: String): Reminder? =
            store
                .observe(Cloud.REMINDERS)
                .first()
                .firstOrNull { it.id == id }
                ?.let(DocumentMapper::reminderFromDoc)

        override suspend fun getDueReminders(upTo: Instant): List<DueReminder> =
            reminders
                .first()
                .filter { (reminder, _) -> reminder.enabled && !reminder.nextFireAt.isAfter(upTo) }
                .map { (reminder, name) ->
                    DueReminder(
                        reminderId = reminder.id,
                        personId = reminder.personId,
                        personName = name,
                        title = reminder.title,
                        note = reminder.note,
                    )
                }

        override suspend fun upsertReminder(reminder: Reminder): String {
            val id = reminder.id.ifEmpty { newDocumentId() }
            store.commit(listOf(CloudWrite.Put(Cloud.REMINDERS, id, DocumentMapper.reminderToDoc(reminder.copy(id = id)))))
            return id
        }

        override suspend fun deleteReminder(id: String) {
            val history = store.observe(Cloud.REMINDER_COMPLETIONS).first().filter { it.data.string("reminderId") == id }
            store.commit(
                history.map { CloudWrite.Tombstone(Cloud.REMINDER_COMPLETIONS, it.id) } +
                    CloudWrite.Tombstone(Cloud.REMINDERS, id),
            )
        }

        override suspend fun setEnabled(id: String, enabled: Boolean) = updateLive(id, mapOf("enabled" to enabled))

        override suspend fun markFired(id: String, firedAt: Instant, nextFireAt: Instant) =
            updateLive(id, mapOf("lastFiredAt" to firedAt.toEpochMilli(), "nextFireAt" to nextFireAt.toEpochMilli()))

        override suspend fun recordCompletion(id: String, at: Instant, day: LocalDate, note: String?) =
            store.commit(
                listOf(CloudWrite.Put(Cloud.REMINDER_COMPLETIONS, newDocumentId(), DocumentMapper.completionToDoc(id, at, day, note))),
            )

        override suspend fun getCompletions(reminderId: String): List<ReminderDoneEntry> =
            observeCompletions().first()[reminderId]?.history.orEmpty()

        override suspend fun updateCompletion(completionId: String, at: Instant, day: LocalDate, note: String?) {
            val doc = liveCompletion(completionId) ?: return
            val reminderId = doc.data.string("reminderId") ?: return
            val fields = DocumentMapper.completionToDoc(reminderId, at, day, note, withNote = true)
            store.commit(listOf(CloudWrite.Put(Cloud.REMINDER_COMPLETIONS, completionId, fields)))
        }

        override suspend fun deleteCompletion(completionId: String) {
            // An unknown id would otherwise become a stub tombstone document.
            if (liveCompletion(completionId) == null) return
            store.commit(listOf(CloudWrite.Tombstone(Cloud.REMINDER_COMPLETIONS, completionId)))
        }

        /** The live completion document [completionId], or `null` when it is missing or tombstoned. */
        private suspend fun liveCompletion(completionId: String): CloudDoc? =
            store.observe(Cloud.REMINDER_COMPLETIONS).first().firstOrNull { it.id == completionId }

        override suspend fun reschedule(id: String, nextFireAt: Instant, clearLastFired: Boolean) {
            val fields = mutableMapOf<String, Any?>("nextFireAt" to nextFireAt.toEpochMilli())
            if (clearLastFired) fields["lastFiredAt"] = null
            updateLive(id, fields)
        }

        override fun observeCompletions(): Flow<Map<String, ReminderCompletion>> =
            store.observe(Cloud.REMINDER_COMPLETIONS).map { docs ->
                docs
                    .mapNotNull(DocumentMapper::completionFromDoc)
                    .groupBy { it.reminderId }
                    .mapValues { (reminderId, log) ->
                        val history =
                            log
                                .map { it.entry }
                                .sortedWith(
                                    compareByDescending<ReminderDoneEntry> { it.day }.thenByDescending(nullsFirst<Instant>()) { it.at },
                                )
                        ReminderCompletion(reminderId, history)
                    }
            }

        override suspend fun getAllReminders(): List<Reminder> = reminders.first().map { it.first }

        override suspend fun deleteAll() {
            val completions = store.observe(Cloud.REMINDER_COMPLETIONS).first()
            val all = store.observe(Cloud.REMINDERS).first()
            store.commit(
                completions.map { CloudWrite.Tombstone(Cloud.REMINDER_COMPLETIONS, it.id) } +
                    all.map { CloudWrite.Tombstone(Cloud.REMINDERS, it.id) },
            )
        }

        /** Merges [fields] into reminder [id] only if it still exists (a merge would otherwise revive it). */
        private suspend fun updateLive(id: String, fields: Map<String, Any?>) {
            if (store.observe(Cloud.REMINDERS).first().none { it.id == id }) return
            store.commit(listOf(CloudWrite.Put(Cloud.REMINDERS, id, fields)))
        }

        private fun CloudDoc.displayName(): String = "${data.string("firstName").orEmpty()} ${data.string("lastName").orEmpty()}".trim()
    }
