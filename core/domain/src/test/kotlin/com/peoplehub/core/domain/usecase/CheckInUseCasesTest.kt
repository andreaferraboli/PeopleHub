package com.peoplehub.core.domain.usecase

import app.cash.turbine.test
import com.peoplehub.core.domain.model.AppSettings
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
    fun `check-in records the clock instant and updates the last-seen timestamp`() = runTest {
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
    fun `urgent check-ins drop fresh people and order most urgent first`() = runTest {
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
