package com.peoplehub.core.domain.usecase

import com.peoplehub.core.domain.model.Reminder
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

            val result = useCase(Reminder(personId = 1, title = "   ", targetIntervalDays = 14, jitterPercent = 20))

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { repository.upsertReminder(any()) }
        }

    @Test
    fun `add reminder rejects a reminder with no person`() =
        runTest {
            val useCase = AddReminderUseCase(repository, clock, Random(1))

            val result = useCase(Reminder(personId = 0, title = "Bring flowers", targetIntervalDays = 14, jitterPercent = 20))

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { repository.upsertReminder(any()) }
        }

    @Test
    fun `add reminder trims the title and schedules the first fire inside the jitter window`() =
        runTest {
            coEvery { repository.upsertReminder(any()) } returns 9L
            val useCase = AddReminderUseCase(repository, clock, Random(3))
            val saved = slot<Reminder>()

            val result =
                useCase(Reminder(personId = 1, title = "  Bring flowers  ", targetIntervalDays = 14, jitterPercent = 20))

            assertEquals(9L, result.getOrNull())
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
            val reminder = Reminder(id = 5, personId = 1, title = "Call", targetIntervalDays = 7, jitterPercent = 20)
            coEvery { repository.getReminder(5) } returns reminder
            val firedAt = slot<Instant>()
            val nextFire = slot<Instant>()
            val useCase = MarkReminderFiredUseCase(repository, clock, Random(1))

            useCase(5)

            coVerify { repository.markFired(eq(5), capture(firedAt), capture(nextFire)) }
            assertEquals(now, firedAt.captured)
            assertTrue(nextFire.captured.isAfter(now))
        }

    @Test
    fun `mark fired is a no-op when the reminder no longer exists`() =
        runTest {
            coEvery { repository.getReminder(404) } returns null
            val useCase = MarkReminderFiredUseCase(repository, clock, Random(1))

            useCase(404)

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
            val reminder = Reminder(id = 5, personId = 1, title = "Call", targetIntervalDays = 7, jitterPercent = 20)
            coEvery { repository.getReminder(5) } returns reminder
            val nextFire = slot<Instant>()
            val useCase = MarkReminderDoneUseCase(repository, clock, Random(1))

            val result = useCase(5)

            assertTrue(result.isSuccess)
            coVerify { repository.recordCompletion(5, now, LocalDate.of(2026, 7, 20)) }
            coVerify { repository.markFired(eq(5), eq(now), capture(nextFire)) }
            val window = ReminderScheduling.window(7, 20)
            val scheduledDays = Duration.between(now, nextFire.captured).toDays().toInt()
            assertTrue(scheduledDays in window, "next fire $scheduledDays d must be inside $window")
        }

    @Test
    fun `marking done twice on one day logs both taps and redraws the next fire each time`() =
        runTest {
            val reminder = Reminder(id = 5, personId = 1, title = "Call", targetIntervalDays = 7, jitterPercent = 20)
            coEvery { repository.getReminder(5) } returns reminder
            val useCase = MarkReminderDoneUseCase(repository, clock, Random(1))

            useCase(5)
            useCase(5)

            // Every tap is part of the history, and each one legitimately restarts the cadence from then.
            coVerify(exactly = 2) { repository.recordCompletion(5, now, LocalDate.of(2026, 7, 20)) }
            coVerify(exactly = 2) { repository.markFired(eq(5), eq(now), any()) }
        }

    @Test
    fun `mark done leaves nothing behind when the reminder no longer exists`() =
        runTest {
            coEvery { repository.getReminder(404) } returns null
            val useCase = MarkReminderDoneUseCase(repository, clock, Random(1))

            val result = useCase(404)

            assertTrue(result.isSuccess)
            coVerify(exactly = 0) { repository.recordCompletion(any(), any(), any()) }
            coVerify(exactly = 0) { repository.markFired(any(), any(), any()) }
        }
}
