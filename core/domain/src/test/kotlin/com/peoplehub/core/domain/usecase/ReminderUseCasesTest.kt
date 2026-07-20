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
}
