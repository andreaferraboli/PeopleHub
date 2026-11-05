package com.peoplehub.core.domain.usecase

import com.peoplehub.core.domain.model.Reminder
import com.peoplehub.core.domain.model.ReminderDoneEntry
import com.peoplehub.core.domain.repository.ReminderRepository
import com.peoplehub.core.domain.util.ReminderScheduling
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

class ReminderUseCasesTest {
    private val repository = mockk<ReminderRepository>(relaxed = true)
    private val now = Instant.parse("2026-07-20T09:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `add reminder rejects a blank title`() =
        runTest {
            val useCase = AddReminderUseCase(repository, clock, Random(1))

            val result = useCase(Reminder(personId = "1", title = "   ", targetIntervalDays = 14, jitterPercent = 20))

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { repository.upsertReminder(any()) }
        }

    @Test
    fun `add reminder rejects a reminder with no person`() =
        runTest {
            val useCase = AddReminderUseCase(repository, clock, Random(1))

            val result = useCase(Reminder(personId = "", title = "Bring flowers", targetIntervalDays = 14, jitterPercent = 20))

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { repository.upsertReminder(any()) }
        }

    @Test
    fun `add reminder trims the title and schedules the first fire inside the jitter window`() =
        runTest {
            coEvery { repository.upsertReminder(any()) } returns "9"
            val useCase = AddReminderUseCase(repository, clock, Random(3))
            val saved = slot<Reminder>()

            val result =
                useCase(Reminder(personId = "1", title = "  Bring flowers  ", targetIntervalDays = 14, jitterPercent = 20))

            assertEquals("9", result.getOrNull())
            coVerify { repository.upsertReminder(capture(saved)) }
            assertEquals("Bring flowers", saved.captured.title)
            val window = ReminderScheduling.window(14, 20)
            val scheduled = Duration.between(now, saved.captured.nextFireAt)
            val scheduledDays = scheduled.toDays().toInt()
            assertTrue(scheduledDays in window, "first fire $scheduledDays d must be inside $window")
        }

    @Test
    fun `mark fired stamps now and reschedules a future fire`() =
        runTest {
            val reminder = Reminder(id = "5", personId = "1", title = "Call", targetIntervalDays = 7, jitterPercent = 20)
            coEvery { repository.getReminder("5") } returns reminder
            val firedAt = slot<Instant>()
            val nextFire = slot<Instant>()
            val useCase = MarkReminderFiredUseCase(repository, clock, Random(1))

            useCase("5")

            coVerify { repository.markFired(eq("5"), capture(firedAt), capture(nextFire)) }
            assertEquals(now, firedAt.captured)
            assertTrue(nextFire.captured.isAfter(now))
        }

    @Test
    fun `mark fired is a no-op when the reminder no longer exists`() =
        runTest {
            coEvery { repository.getReminder("404") } returns null
            val useCase = MarkReminderFiredUseCase(repository, clock, Random(1))

            useCase("404")

            coVerify(exactly = 0) { repository.markFired(any(), any(), any()) }
        }

    @Test
    fun `due reminders are asked for up to the end of today, not the current instant`() =
        runTest {
            // The sweep runs early in the morning; a reminder whose fire time lands at 20:00 today is
            // due today, not tomorrow.
            val cutOff = slot<Instant>()
            coEvery { repository.getDueReminders(capture(cutOff)) } returns emptyList()
            val useCase = GetDueRemindersUseCase(repository, clock)

            useCase()

            assertTrue(cutOff.captured.isAfter(now), "cut-off ${cutOff.captured} must be after $now")
            assertEquals(LocalDate.of(2026, 7, 20), cutOff.captured.atZone(ZoneOffset.UTC).toLocalDate())
            assertTrue(cutOff.captured.isBefore(Instant.parse("2026-07-21T00:00:00Z")))
        }

    @Test
    fun `mark done logs this moment and restarts the cadence inside the jitter window`() =
        runTest {
            val reminder = Reminder(id = "5", personId = "1", title = "Call", targetIntervalDays = 7, jitterPercent = 20)
            coEvery { repository.getReminder("5") } returns reminder
            val nextFire = slot<Instant>()
            val useCase = MarkReminderDoneUseCase(repository, clock, Random(1))

            val result = useCase("5")

            assertTrue(result.isSuccess)
            coVerify { repository.recordCompletion("5", now, LocalDate.of(2026, 7, 20)) }
            coVerify { repository.markFired(eq("5"), eq(now), capture(nextFire)) }
            val window = ReminderScheduling.window(7, 20)
            val scheduledDays = Duration.between(now, nextFire.captured).toDays().toInt()
            assertTrue(scheduledDays in window, "next fire $scheduledDays d must be inside $window")
        }

    @Test
    fun `marking done twice on one day logs both taps and redraws the next fire each time`() =
        runTest {
            val reminder = Reminder(id = "5", personId = "1", title = "Call", targetIntervalDays = 7, jitterPercent = 20)
            coEvery { repository.getReminder("5") } returns reminder
            val useCase = MarkReminderDoneUseCase(repository, clock, Random(1))

            useCase("5")
            useCase("5")

            // Every tap is part of the history, and each one legitimately restarts the cadence from then.
            coVerify(exactly = 2) { repository.recordCompletion("5", now, LocalDate.of(2026, 7, 20)) }
            coVerify(exactly = 2) { repository.markFired(eq("5"), eq(now), any()) }
        }

    @Test
    fun `mark done leaves nothing behind when the reminder no longer exists`() =
        runTest {
            coEvery { repository.getReminder("404") } returns null
            val useCase = MarkReminderDoneUseCase(repository, clock, Random(1))

            val result = useCase("404")

            assertTrue(result.isSuccess)
            coVerify(exactly = 0) { repository.recordCompletion(any(), any(), any()) }
            coVerify(exactly = 0) { repository.markFired(any(), any(), any()) }
        }

    // --- editing the completion history ------------------------------------------------------------

    private val weekly =
        Reminder(
            id = "5",
            personId = "1",
            title = "Call",
            targetIntervalDays = 7,
            jitterPercent = 0,
            nextFireAt = Instant.parse("2026-07-25T09:00:00Z"),
            createdAt = Instant.parse("2026-01-01T08:00:00Z"),
        )
    private val july1 = ReminderDoneEntry(LocalDate.of(2026, 7, 1), Instant.parse("2026-07-01T18:00:00Z"), id = "c1")
    private val july18 = ReminderDoneEntry(LocalDate.of(2026, 7, 18), Instant.parse("2026-07-18T09:00:00Z"), id = "c2")

    @Test
    fun `a back-dated completion newer than the latest one reschedules from it`() =
        runTest {
            coEvery { repository.getReminder("5") } returns weekly
            coEvery { repository.getCompletions("5") } returns listOf(july1)
            val at = Instant.parse("2026-07-15T20:30:00Z")
            val useCase = AddReminderCompletionUseCase(repository, clock, Random(1))

            val result = useCase("5", at)

            val expected = at.plus(Duration.ofDays(7))
            assertEquals(expected, result.getOrNull())
            coVerify { repository.recordCompletion("5", at, LocalDate.of(2026, 7, 15)) }
            coVerify { repository.reschedule("5", expected) }
            coVerify(exactly = 0) { repository.markFired(any(), any(), any()) }
        }

    @Test
    fun `a back-dated completion older than the latest one keeps the schedule`() =
        runTest {
            coEvery { repository.getReminder("5") } returns weekly
            coEvery { repository.getCompletions("5") } returns listOf(july18)
            val useCase = AddReminderCompletionUseCase(repository, clock, Random(1))

            val result = useCase("5", Instant.parse("2026-07-10T10:00:00Z"))

            assertEquals(weekly.nextFireAt, result.getOrNull())
            coVerify { repository.recordCompletion("5", any(), LocalDate.of(2026, 7, 10)) }
            coVerify(exactly = 0) { repository.reschedule(any(), any()) }
        }

    @Test
    fun `a completion in the future is refused`() =
        runTest {
            coEvery { repository.getReminder("5") } returns weekly
            val useCase = AddReminderCompletionUseCase(repository, clock, Random(1))

            val result = useCase("5", now.plusSeconds(3_600))

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { repository.recordCompletion(any(), any(), any()) }
            coVerify(exactly = 0) { repository.reschedule(any(), any()) }
        }

    @Test
    fun `moving the latest completion reschedules from its new moment`() =
        runTest {
            coEvery { repository.getReminder("5") } returns weekly
            coEvery { repository.getCompletions("5") } returns listOf(july18, july1)
            val at = Instant.parse("2026-07-12T11:00:00Z")
            val useCase = UpdateReminderCompletionUseCase(repository, clock, Random(1))

            val result = useCase("5", "c2", at, note = "  after the dinner  ")

            val expected = at.plus(Duration.ofDays(7))
            assertEquals(expected, result.getOrNull())
            coVerify { repository.updateCompletion("c2", at, LocalDate.of(2026, 7, 12), "after the dinner") }
            coVerify { repository.reschedule("5", expected) }
        }

    @Test
    fun `editing a completion that is gone fails without writing`() =
        runTest {
            coEvery { repository.getReminder("5") } returns weekly
            coEvery { repository.getCompletions("5") } returns listOf(july1)
            val useCase = UpdateReminderCompletionUseCase(repository, clock, Random(1))

            val result = useCase("5", "missing", Instant.parse("2026-07-12T11:00:00Z"), note = null)

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { repository.updateCompletion(any(), any(), any(), any()) }
            coVerify(exactly = 0) { repository.reschedule(any(), any()) }
        }

    @Test
    fun `deleting the latest completion puts the cadence back on the previous one`() =
        runTest {
            coEvery { repository.getReminder("5") } returns weekly
            coEvery { repository.getCompletions("5") } returns listOf(july18, july1)
            val useCase = DeleteReminderCompletionUseCase(repository, clock, Random(1))

            val result = useCase("5", "c2")

            val expected = july1.at!!.plus(Duration.ofDays(7))
            assertEquals(expected, result.getOrNull())
            coVerify { repository.deleteCompletion("c2") }
            coVerify { repository.reschedule("5", expected) }
        }

    @Test
    fun `deleting an older completion keeps the schedule`() =
        runTest {
            coEvery { repository.getReminder("5") } returns weekly
            coEvery { repository.getCompletions("5") } returns listOf(july18, july1)
            val useCase = DeleteReminderCompletionUseCase(repository, clock, Random(1))

            val result = useCase("5", "c1")

            assertEquals(weekly.nextFireAt, result.getOrNull())
            coVerify { repository.deleteCompletion("c1") }
            coVerify(exactly = 0) { repository.reschedule(any(), any()) }
        }

    @Test
    fun `deleting the only completion clears the last fire and draws from now`() =
        runTest {
            coEvery { repository.getReminder("5") } returns weekly
            coEvery { repository.getCompletions("5") } returns listOf(july18)
            val useCase = DeleteReminderCompletionUseCase(repository, clock, Random(1))

            val result = useCase("5", "c2")

            val expected = now.plus(Duration.ofDays(7))
            assertEquals(expected, result.getOrNull())
            coVerify { repository.reschedule("5", expected, clearLastFired = true) }
        }

    @Test
    fun `a back-dated completion keeps its note, trimmed`() =
        runTest {
            coEvery { repository.getReminder("5") } returns weekly
            coEvery { repository.getCompletions("5") } returns emptyList()
            val at = Instant.parse("2026-07-19T19:00:00Z")
            val useCase = AddReminderCompletionUseCase(repository, clock, Random(1))

            useCase("5", at, note = " brought tulips ")

            coVerify { repository.recordCompletion("5", at, LocalDate.of(2026, 7, 19), "brought tulips") }
            // A first completion is a change of the latest one, but the history is not empty.
            coVerify { repository.reschedule("5", at.plus(Duration.ofDays(7)), clearLastFired = false) }
        }

    @Test
    fun `a next occurrence set by hand wins over the cadence`() =
        runTest {
            coEvery { repository.getReminder("5") } returns weekly
            coEvery { repository.upsertReminder(any()) } returns "5"
            val saved = slot<Reminder>()
            val chosen = Instant.parse("2026-08-03T00:00:00Z")
            val useCase = UpdateReminderUseCase(repository, clock, Random(1))

            // The cadence changes too, but the date picked by hand is what the user asked for.
            val result = useCase(weekly.copy(targetIntervalDays = 30, enabled = false, personId = "2"), nextFireOverride = chosen)

            assertTrue(result.isSuccess)
            coVerify { repository.upsertReminder(capture(saved)) }
            assertEquals(chosen, saved.captured.nextFireAt)
            assertEquals(false, saved.captured.enabled)
            assertEquals("2", saved.captured.personId)
        }

    @Test
    fun `saving the form never brings back a last fire cleared by a history edit`() =
        runTest {
            // The editor loaded the reminder while it still had a last fire; deleting the only completion
            // then cleared it in the store. Saving a cadence change must count from now, not the stale copy.
            val stale = weekly.copy(lastFiredAt = Instant.parse("2026-07-18T09:00:00Z"))
            coEvery { repository.getReminder("5") } returns weekly.copy(lastFiredAt = null)
            coEvery { repository.upsertReminder(any()) } returns "5"
            val saved = slot<Reminder>()
            val useCase = UpdateReminderUseCase(repository, clock, Random(1))

            useCase(stale.copy(targetIntervalDays = 10))

            coVerify { repository.upsertReminder(capture(saved)) }
            assertEquals(null, saved.captured.lastFiredAt)
            assertEquals(now.plus(Duration.ofDays(10)), saved.captured.nextFireAt)
        }

    @Test
    fun `editing only the title keeps the stored next occurrence`() =
        runTest {
            coEvery { repository.getReminder("5") } returns weekly
            coEvery { repository.upsertReminder(any()) } returns "5"
            val saved = slot<Reminder>()
            val useCase = UpdateReminderUseCase(repository, clock, Random(1))

            useCase(weekly.copy(title = "Call mum", nextFireAt = Instant.EPOCH))

            coVerify { repository.upsertReminder(capture(saved)) }
            assertEquals(weekly.nextFireAt, saved.captured.nextFireAt)
        }

    @Test
    fun `a new reminder can start on a chosen day`() =
        runTest {
            coEvery { repository.upsertReminder(any()) } returns "9"
            val saved = slot<Reminder>()
            val chosen = Instant.parse("2026-07-22T00:00:00Z")
            val useCase = AddReminderUseCase(repository, clock, Random(1))

            useCase(Reminder(personId = "1", title = "Flowers", targetIntervalDays = 14, jitterPercent = 20), nextFireOverride = chosen)

            coVerify { repository.upsertReminder(capture(saved)) }
            assertEquals(chosen, saved.captured.nextFireAt)
        }
}
