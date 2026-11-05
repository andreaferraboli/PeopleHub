package com.peoplehub.core.domain.usecase

import com.peoplehub.core.domain.model.DueReminder
import com.peoplehub.core.domain.model.Reminder
import com.peoplehub.core.domain.model.ReminderCompletion
import com.peoplehub.core.domain.model.ReminderDoneEntry
import com.peoplehub.core.domain.model.ReminderFilter
import com.peoplehub.core.domain.repository.ReminderRepository
import com.peoplehub.core.domain.util.ReminderScheduling
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.random.Random

/** Observes the reminders belonging to a single person, soonest-due first. */
class GetPersonRemindersUseCase
    @Inject
    constructor(
        private val repository: ReminderRepository,
    ) {
        operator fun invoke(personId: String): Flow<List<Reminder>> = repository.observeRemindersForPerson(personId)
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
        suspend operator fun invoke(id: String): Reminder? = repository.getReminder(id)
    }

/**
 * Validates and persists a new reminder, computing its first (jittered) fire time from now, unless the
 * user picked the first occurrence by hand ([nextFireOverride]). Title is mandatory and the reminder
 * must belong to a person; the interval and jitter are clamped to sane bounds.
 */
class AddReminderUseCase
    @Inject
    constructor(
        private val repository: ReminderRepository,
        private val clock: Clock,
        private val random: Random,
    ) {
        suspend operator fun invoke(
            reminder: Reminder,
            nextFireOverride: Instant? = null,
        ): Result<String> =
            runCatching {
                require(reminder.title.isNotBlank()) { "Reminder title is required" }
                require(reminder.personId.isNotBlank()) { "Reminder must belong to a person" }
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
                        nextFireAt = nextFireOverride ?: ReminderScheduling.nextFireAt(now, target, jitter, random),
                    ),
                )
            }
    }

/**
 * Validates and persists edits to an existing reminder: every field of the given reminder is written
 * as is (person, title, note, category, cadence, enabled, ...). The next fire time is, in order: the
 * date the user set by hand (`nextFireOverride`); recomputed when the cadence (target interval or
 * jitter) actually changed, based on the last fire (or now, if it never fired); otherwise left
 * untouched, so editing just the title or note does not move the schedule.
 */
class UpdateReminderUseCase
    @Inject
    constructor(
        private val repository: ReminderRepository,
        private val clock: Clock,
        private val random: Random,
    ) {
        suspend operator fun invoke(
            reminder: Reminder,
            nextFireOverride: Instant? = null,
        ): Result<String> =
            runCatching {
                require(reminder.title.isNotBlank()) { "Reminder title is required" }
                require(reminder.personId.isNotBlank()) { "Reminder must belong to a person" }
                val existing = repository.getReminder(reminder.id)
                val target = reminder.targetIntervalDays.coerceAtLeast(ReminderScheduling.MIN_INTERVAL_DAYS)
                val jitter = reminder.jitterPercent.coerceIn(0, ReminderScheduling.MAX_JITTER_PERCENT)
                val cadenceChanged =
                    existing == null ||
                        existing.targetIntervalDays != target ||
                        existing.jitterPercent != jitter
                val nextFireAt =
                    nextFireOverride ?: if (cadenceChanged) {
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

/** Deletes a reminder and its completion history. */
class DeleteReminderUseCase
    @Inject
    constructor(
        private val repository: ReminderRepository,
    ) {
        suspend operator fun invoke(id: String) = repository.deleteReminder(id)
    }

/** Pauses or resumes a reminder. */
class SetReminderEnabledUseCase
    @Inject
    constructor(
        private val repository: ReminderRepository,
    ) {
        suspend operator fun invoke(id: String, enabled: Boolean) = repository.setEnabled(id, enabled)
    }

/**
 * The reminders due **today or earlier**, for the reminder worker to notify about.
 *
 * The cadence is measured in whole days but a fire time carries the time of day it was drawn from
 * (the moment the reminder was created, or last done). Comparing that against "now" would make the
 * daily sweep — which runs once, early in the morning — miss every reminder whose fire time falls
 * later in the day, and deliver it a full day late instead. Anchoring the cut-off to the end of today
 * restores day granularity without risking a double fire: the next occurrence is always at least a day
 * away, so it cannot also fall inside today's window.
 */
class GetDueRemindersUseCase
    @Inject
    constructor(
        private val repository: ReminderRepository,
        private val clock: Clock,
    ) {
        suspend operator fun invoke(): List<DueReminder> {
            val startOfTomorrow = LocalDate.now(clock).plusDays(1).atStartOfDay(clock.zone)
            return repository.getDueReminders(startOfTomorrow.toInstant().minusMillis(1))
        }
    }

/**
 * Records that a reminder fired and reschedules its next occurrence from now with fresh jitter, so
 * it does not fire again until a new randomised interval has elapsed. Called by the worker after
 * posting a notification; ticking a reminder off as *done* goes through [MarkReminderDoneUseCase]
 * instead, because that also has to be remembered.
 */
class MarkReminderFiredUseCase
    @Inject
    constructor(
        private val repository: ReminderRepository,
        private val clock: Clock,
        private val random: Random,
    ) {
        suspend operator fun invoke(reminderId: String) {
            val reminder = repository.getReminder(reminderId) ?: return
            val now = Instant.now(clock)
            val next =
                ReminderScheduling.nextFireAt(now, reminder.targetIntervalDays, reminder.jitterPercent, random)
            repository.markFired(reminderId, now, next)
        }
    }

/**
 * Ticks a reminder off as done: appends this moment to its completion history and restarts the cadence
 * from now with a **freshly drawn** interval, so the next occurrence lands at a new random distance
 * rather than resuming the old schedule.
 *
 * The log is what makes the gesture durable — the reminder row only carries its next occurrence, so
 * rescheduling alone would leave no trace that anything happened. Logging before rescheduling also
 * means a failure to draw the next date cannot lose the record of the tap.
 *
 * Every tap is kept, including a second one on the same day, and each redraws the next occurrence —
 * the user is saying "I did it just now".
 *
 * Called both by the "done" button on the reminder cards and by the "Done" action on the notification.
 */
class MarkReminderDoneUseCase
    @Inject
    constructor(
        private val repository: ReminderRepository,
        private val clock: Clock,
        private val random: Random,
    ) {
        suspend operator fun invoke(reminderId: String): Result<Unit> =
            runCatching {
                val reminder = repository.getReminder(reminderId) ?: return@runCatching
                val now = Instant.now(clock)
                repository.recordCompletion(reminderId, now, now.atZone(clock.zone).toLocalDate())
                val next =
                    ReminderScheduling.nextFireAt(now, reminder.targetIntervalDays, reminder.jitterPercent, random)
                repository.markFired(reminderId, now, next)
            }
    }

/** Observes every time each reminder has been ticked off as done, keyed by reminder id. */
class ObserveReminderCompletionsUseCase
    @Inject
    constructor(
        private val repository: ReminderRepository,
    ) {
        operator fun invoke(): Flow<Map<String, ReminderCompletion>> = repository.observeCompletions()
    }

/**
 * Adds a completion to a reminder's history at any past moment ([at]), e.g. "I did it last Tuesday but
 * forgot to tick it". If it becomes the latest completion, the cadence restarts from it with a freshly
 * drawn interval (see [ReminderScheduling.nextFireAfterHistoryChange]); an entry older than the latest
 * one leaves the schedule alone. Fails for a moment in the future or a reminder that no longer exists.
 *
 * @return the reminder's next occurrence after the change.
 */
class AddReminderCompletionUseCase
    @Inject
    constructor(
        private val repository: ReminderRepository,
        private val clock: Clock,
        private val random: Random,
    ) {
        suspend operator fun invoke(
            reminderId: String,
            at: Instant,
            note: String? = null,
        ): Result<Instant> =
            runCatching {
                val reminder = requireNotNull(repository.getReminder(reminderId)) { "Reminder $reminderId no longer exists" }
                requireNotInFuture(at, clock)
                val before = repository.getCompletions(reminderId)
                val day = at.atZone(clock.zone).toLocalDate()
                val cleanNote = note.cleanNote()
                repository.recordCompletion(reminderId, at, day, cleanNote)
                val after = before + ReminderDoneEntry(day = day, at = at, note = cleanNote)
                rescheduleAfterHistoryChange(repository, reminder, before, after, clock, random)
            }
    }

/**
 * Moves one completion of a reminder's history to another moment ([at]). When that changes the latest
 * completion, the cadence restarts from the new latest one; otherwise the schedule is left alone.
 * Fails for a moment in the future or an entry that no longer exists.
 *
 * @return the reminder's next occurrence after the change.
 */
class UpdateReminderCompletionUseCase
    @Inject
    constructor(
        private val repository: ReminderRepository,
        private val clock: Clock,
        private val random: Random,
    ) {
        suspend operator fun invoke(
            reminderId: String,
            completionId: String,
            at: Instant,
            note: String?,
        ): Result<Instant> =
            runCatching {
                val reminder = requireNotNull(repository.getReminder(reminderId)) { "Reminder $reminderId no longer exists" }
                requireNotInFuture(at, clock)
                val before = repository.getCompletions(reminderId)
                require(before.any { it.id == completionId }) { "Completion $completionId no longer exists" }
                val day = at.atZone(clock.zone).toLocalDate()
                val cleanNote = note.cleanNote()
                repository.updateCompletion(completionId, at, day, cleanNote)
                val after = before.map { if (it.id == completionId) it.copy(day = day, at = at, note = cleanNote) else it }
                rescheduleAfterHistoryChange(repository, reminder, before, after, clock, random)
            }
    }

/**
 * Deletes one completion from a reminder's history (a tombstone, like every deletion). Removing the
 * latest one, such as a mistaken tap on "done", puts the cadence back on the previous completion, so the
 * reminder may well be due again right away. Removing the last one left clears `lastFiredAt` and draws
 * the next occurrence from now, as the MCP server does.
 *
 * @return the reminder's next occurrence after the change.
 */
class DeleteReminderCompletionUseCase
    @Inject
    constructor(
        private val repository: ReminderRepository,
        private val clock: Clock,
        private val random: Random,
    ) {
        suspend operator fun invoke(reminderId: String, completionId: String): Result<Instant> =
            runCatching {
                val reminder = requireNotNull(repository.getReminder(reminderId)) { "Reminder $reminderId no longer exists" }
                val before = repository.getCompletions(reminderId)
                repository.deleteCompletion(completionId)
                val after = before.filterNot { it.id == completionId }
                rescheduleAfterHistoryChange(repository, reminder, before, after, clock, random)
            }
    }

private fun requireNotInFuture(at: Instant, clock: Clock) {
    require(!at.isAfter(Instant.now(clock))) { "A completion cannot be in the future" }
}

private fun String?.cleanNote(): String? = this?.trim()?.takeIf(String::isNotBlank)

/**
 * Applies [ReminderScheduling.nextFireAfterHistoryChange] to [reminder] and writes the new next
 * occurrence when it moved, clearing `lastFiredAt` when the history became empty (the MCP's rule).
 * Returns the next occurrence in force afterwards.
 */
private suspend fun rescheduleAfterHistoryChange(
    repository: ReminderRepository,
    reminder: Reminder,
    before: List<ReminderDoneEntry>,
    after: List<ReminderDoneEntry>,
    clock: Clock,
    random: Random,
): Instant {
    val next =
        ReminderScheduling.nextFireAfterHistoryChange(
            before = before,
            after = after,
            fallback = Instant.now(clock),
            targetIntervalDays = reminder.targetIntervalDays,
            jitterPercent = reminder.jitterPercent,
            zone = clock.zone,
            random = random,
        ) ?: return reminder.nextFireAt
    repository.reschedule(reminder.id, next, clearLastFired = after.isEmpty())
    return next
}
