package com.peoplehub.core.domain.usecase

import com.peoplehub.core.domain.model.DueReminder
import com.peoplehub.core.domain.model.Reminder
import com.peoplehub.core.domain.model.ReminderFilter
import com.peoplehub.core.domain.repository.ReminderRepository
import com.peoplehub.core.domain.util.ReminderScheduling
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlin.random.Random

/** Observes the reminders belonging to a single person, soonest-due first. */
class GetPersonRemindersUseCase
    @Inject
    constructor(
        private val repository: ReminderRepository,
    ) {
        operator fun invoke(personId: Long): Flow<List<Reminder>> = repository.observeRemindersForPerson(personId)
    }

/** Observes the filtered list of reminders across all people, soonest-due first. */
class GetRemindersUseCase
    @Inject
    constructor(
        private val repository: ReminderRepository,
    ) {
        operator fun invoke(filter: ReminderFilter = ReminderFilter()): Flow<List<Reminder>> =
            repository.observeReminders(filter)
    }

/** One-shot read of a single reminder, used to seed the edit form. */
class GetReminderUseCase
    @Inject
    constructor(
        private val repository: ReminderRepository,
    ) {
        suspend operator fun invoke(id: Long): Reminder? = repository.getReminder(id)
    }

/**
 * Validates and persists a new reminder, computing its first (jittered) fire time from now. Title
 * is mandatory and the reminder must belong to a person; the interval and jitter are clamped to
 * sane bounds.
 */
class AddReminderUseCase
    @Inject
    constructor(
        private val repository: ReminderRepository,
        private val clock: Clock,
        private val random: Random,
    ) {
        suspend operator fun invoke(reminder: Reminder): Result<Long> =
            runCatching {
                require(reminder.title.isNotBlank()) { "Reminder title is required" }
                require(reminder.personId > 0L) { "Reminder must belong to a person" }
                val target = reminder.targetIntervalDays.coerceAtLeast(ReminderScheduling.MIN_INTERVAL_DAYS)
                val jitter = reminder.jitterPercent.coerceIn(0, ReminderScheduling.MAX_JITTER_PERCENT)
                val now = Instant.now(clock)
                repository.upsertReminder(
                    reminder.copy(
                        title = reminder.title.trim(),
                        note = reminder.note?.trim()?.takeIf(String::isNotBlank),
                        targetIntervalDays = target,
                        jitterPercent = jitter,
                        createdAt = if (reminder.createdAt == Instant.EPOCH) now else reminder.createdAt,
                        nextFireAt = ReminderScheduling.nextFireAt(now, target, jitter, random),
                    ),
                )
            }
    }

/**
 * Validates and persists edits to an existing reminder. The next fire time is only recomputed when
 * the cadence (target interval or jitter) actually changed — editing just the title or note leaves
 * the schedule untouched — and is then based on the last fire (or now, if it never fired).
 */
class UpdateReminderUseCase
    @Inject
    constructor(
        private val repository: ReminderRepository,
        private val clock: Clock,
        private val random: Random,
    ) {
        suspend operator fun invoke(reminder: Reminder): Result<Long> =
            runCatching {
                require(reminder.title.isNotBlank()) { "Reminder title is required" }
                val existing = repository.getReminder(reminder.id)
                val target = reminder.targetIntervalDays.coerceAtLeast(ReminderScheduling.MIN_INTERVAL_DAYS)
                val jitter = reminder.jitterPercent.coerceIn(0, ReminderScheduling.MAX_JITTER_PERCENT)
                val cadenceChanged =
                    existing == null ||
                        existing.targetIntervalDays != target ||
                        existing.jitterPercent != jitter
                val nextFireAt =
                    if (cadenceChanged) {
                        val base = reminder.lastFiredAt ?: Instant.now(clock)
                        ReminderScheduling.nextFireAt(base, target, jitter, random)
                    } else {
                        existing.nextFireAt
                    }
                repository.upsertReminder(
                    reminder.copy(
                        title = reminder.title.trim(),
                        note = reminder.note?.trim()?.takeIf(String::isNotBlank),
                        targetIntervalDays = target,
                        jitterPercent = jitter,
                        nextFireAt = nextFireAt,
                    ),
                )
            }
    }

/** Deletes a reminder by id. */
class DeleteReminderUseCase
    @Inject
    constructor(
        private val repository: ReminderRepository,
    ) {
        suspend operator fun invoke(id: Long) = repository.deleteReminder(id)
    }

/** Pauses or resumes a reminder. */
class SetReminderEnabledUseCase
    @Inject
    constructor(
        private val repository: ReminderRepository,
    ) {
        suspend operator fun invoke(id: Long, enabled: Boolean) = repository.setEnabled(id, enabled)
    }

/** The reminders due right now, for the reminder worker to notify about. */
class GetDueRemindersUseCase
    @Inject
    constructor(
        private val repository: ReminderRepository,
        private val clock: Clock,
    ) {
        suspend operator fun invoke(): List<DueReminder> = repository.getDueReminders(Instant.now(clock))
    }

/**
 * Records that a reminder fired and reschedules its next occurrence from now with fresh jitter, so
 * it does not fire again until a new randomised interval has elapsed. Called both by the worker
 * after posting a notification and by the "Done" notification action.
 */
class MarkReminderFiredUseCase
    @Inject
    constructor(
        private val repository: ReminderRepository,
        private val clock: Clock,
        private val random: Random,
    ) {
        suspend operator fun invoke(reminderId: Long) {
            val reminder = repository.getReminder(reminderId) ?: return
            val now = Instant.now(clock)
            val next =
                ReminderScheduling.nextFireAt(now, reminder.targetIntervalDays, reminder.jitterPercent, random)
            repository.markFired(reminderId, now, next)
        }
    }
