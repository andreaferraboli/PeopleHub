package com.peoplehub.core.domain.usecase

import app.cash.turbine.test
import com.peoplehub.core.domain.model.AppSettings
import com.peoplehub.core.domain.model.CheckIn
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.repository.CheckInRepository
import com.peoplehub.core.domain.repository.PeopleRepository
import com.peoplehub.core.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class CheckInUseCasesTest {
    private val now: Instant = Instant.parse("2026-06-10T09:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `check-in records the clock instant and updates the last-seen timestamp`() =
        runTest {
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            coEvery { checkInRepository.recordCheckIn(any()) } returns 1L
            val useCase = CheckInPersonUseCase(checkInRepository, peopleRepository, clock)

            useCase(personId = 7L, note = "Coffee")

            coVerify {
                checkInRepository.recordCheckIn(
                    match { it.personId == 7L && it.timestamp == now && it.note == "Coffee" },
                )
            }
            coVerify { peopleRepository.updateLastCheckIn(7L, now.toEpochMilli()) }
        }

    @Test
    fun `check-in records a supplied back-dated instant`() =
        runTest {
            val backDated = now.minus(3, ChronoUnit.DAYS)
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            coEvery { checkInRepository.recordCheckIn(any()) } returns 1L
            val useCase = CheckInPersonUseCase(checkInRepository, peopleRepository, clock)

            useCase(personId = 7L, note = "Dinner last week", at = backDated)

            coVerify {
                checkInRepository.recordCheckIn(
                    match { it.personId == 7L && it.timestamp == backDated },
                )
            }
            coVerify { peopleRepository.updateLastCheckIn(7L, backDated.toEpochMilli()) }
        }

    @Test
    fun `back-dated check-in does not regress a more recent last-seen`() =
        runTest {
            val backDated = now.minus(10, ChronoUnit.DAYS)
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            coEvery { checkInRepository.recordCheckIn(any()) } returns 1L
            coEvery { peopleRepository.getPerson(7L) } returns
                Person(id = 7, firstName = "Recent", lastName = "Seen", lastCheckInAt = now)
            val useCase = CheckInPersonUseCase(checkInRepository, peopleRepository, clock)

            useCase(personId = 7L, at = backDated)

            coVerify { checkInRepository.recordCheckIn(match { it.timestamp == backDated }) }
            coVerify(exactly = 0) { peopleRepository.updateLastCheckIn(any(), any()) }
        }

    @Test
    fun `deleting check-ins re-derives the last-seen from the most recent survivor`() =
        runTest {
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            val survivor = now.minus(5, ChronoUnit.DAYS)
            coEvery { checkInRepository.latestTimestamp(7L) } returns survivor
            val useCase = DeleteCheckInsUseCase(checkInRepository, peopleRepository)

            useCase(personId = 7L, ids = listOf(1L, 2L))

            coVerify { checkInRepository.deleteCheckIns(listOf(1L, 2L)) }
            coVerify { peopleRepository.updateLastCheckIn(7L, survivor.toEpochMilli()) }
        }

    @Test
    fun `deleting the last check-in clears the last-seen timestamp`() =
        runTest {
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            coEvery { checkInRepository.latestTimestamp(7L) } returns null
            val useCase = DeleteCheckInsUseCase(checkInRepository, peopleRepository)

            useCase(personId = 7L, ids = listOf(1L))

            coVerify { peopleRepository.updateLastCheckIn(7L, null) }
        }

    @Test
    fun `deleting an empty selection is a no-op`() =
        runTest {
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            val useCase = DeleteCheckInsUseCase(checkInRepository, peopleRepository)

            useCase(personId = 7L, ids = emptyList())

            coVerify(exactly = 0) { checkInRepository.deleteCheckIns(any()) }
            coVerify(exactly = 0) { peopleRepository.updateLastCheckIn(any(), any()) }
        }

    @Test
    fun `editing a check-in persists it and re-derives the last-seen`() =
        runTest {
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            val edited = CheckIn(id = 3L, personId = 7L, timestamp = now.minus(1, ChronoUnit.DAYS), note = "  Lunch  ")
            coEvery { checkInRepository.latestTimestamp(7L) } returns edited.timestamp
            val useCase = UpdateCheckInUseCase(checkInRepository, peopleRepository)

            useCase(edited)

            coVerify {
                checkInRepository.updateCheckIn(
                    match { it.id == 3L && it.timestamp == edited.timestamp && it.note == "  Lunch  " },
                )
            }
            coVerify { peopleRepository.updateLastCheckIn(7L, edited.timestamp.toEpochMilli()) }
        }

    @Test
    fun `urgent check-ins drop fresh people and order most urgent first`() =
        runTest {
            val peopleRepository = mockk<PeopleRepository>()
            val settingsRepository = mockk<SettingsRepository>()
            val fresh = Person(id = 1, firstName = "Fresh", lastName = "One", lastCheckInAt = now.minus(2, ChronoUnit.DAYS))
            val due = Person(id = 2, firstName = "Due", lastName = "Two", lastCheckInAt = now.minus(20, ChronoUnit.DAYS))
            val overdue = Person(id = 3, firstName = "Over", lastName = "Due", lastCheckInAt = now.minus(40, ChronoUnit.DAYS))
            val never = Person(id = 4, firstName = "Never", lastName = "Seen", lastCheckInAt = null)
            every { peopleRepository.observePeople(any()) } returns flowOf(listOf(fresh, due, overdue, never))
            every { settingsRepository.settings } returns flowOf(AppSettings())

            val useCase = GetUrgentCheckInsUseCase(peopleRepository, settingsRepository, clock)

            useCase().test {
                val urgent = awaitItem()
                assertEquals(listOf(4L, 3L, 2L), urgent.map { it.person.id })
                awaitComplete()
            }
        }
}
