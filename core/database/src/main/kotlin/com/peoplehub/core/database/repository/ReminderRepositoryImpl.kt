package com.peoplehub.core.database.repository

import com.peoplehub.core.database.dao.ReminderDao
import com.peoplehub.core.database.entity.ReminderCompletionEntity
import com.peoplehub.core.database.mapper.toDomain
import com.peoplehub.core.database.mapper.toEntity
import com.peoplehub.core.domain.model.DueReminder
import com.peoplehub.core.domain.model.Reminder
import com.peoplehub.core.domain.model.ReminderCompletion
import com.peoplehub.core.domain.model.ReminderFilter
import com.peoplehub.core.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Room-backed [ReminderRepository]. Category/enabled filtering is applied in memory over the DAO
 * flow (the DAO already orders by soonest-due).
 */
internal class ReminderRepositoryImpl
    @Inject
    constructor(
        private val dao: ReminderDao,
    ) : ReminderRepository {
        override fun observeRemindersForPerson(personId: Long): Flow<List<Reminder>> =
            dao.observeForPerson(personId).map { rows -> rows.map { it.toDomain() } }

        override fun observeReminders(filter: ReminderFilter): Flow<List<Reminder>> =
            dao.observeAll().map { rows ->
                rows
                    .asSequence()
                    .map { it.toDomain() }
                    .filter { filter.personId == null || it.personId == filter.personId }
                    .filter { filter.category == null || it.category == filter.category }
                    .filter { !filter.onlyEnabled || it.enabled }
                    .toList()
            }

        override suspend fun getReminder(id: Long): Reminder? = dao.getById(id)?.toDomain()

        override suspend fun getDueReminders(upTo: Instant): List<DueReminder> =
            dao.getDue(upTo.toEpochMilli()).map { it.toDomain() }

        override suspend fun upsertReminder(reminder: Reminder): Long {
            val entity = reminder.toEntity()
            return if (reminder.id == 0L) {
                dao.insert(entity)
            } else {
                dao.update(entity)
                reminder.id
            }
        }

        override suspend fun deleteReminder(id: Long) = dao.deleteById(id)

        override suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

        override suspend fun markFired(id: Long, firedAt: Instant, nextFireAt: Instant) =
            dao.markFired(id, firedAt.toEpochMilli(), nextFireAt.toEpochMilli())

        override suspend fun recordCompletion(id: Long, day: LocalDate) =
            dao.insertCompletion(ReminderCompletionEntity(reminderId = id, doneEpochDay = day.toEpochDay()))

        override fun observeCompletions(): Flow<Map<Long, ReminderCompletion>> =
            dao.observeCompletions().map { rows -> rows.associate { it.reminderId to it.toDomain() } }

        override suspend fun getAllReminders(): List<Reminder> = dao.getAll().map { it.toDomain() }

        override suspend fun deleteAll() = dao.deleteAll()
    }
