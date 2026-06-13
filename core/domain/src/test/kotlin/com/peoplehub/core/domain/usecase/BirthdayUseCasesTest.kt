package com.peoplehub.core.domain.usecase

import app.cash.turbine.test
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.repository.PeopleRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class BirthdayUseCasesTest {

    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-10T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `upcoming birthdays keeps only those within the window and sorts by soonest`() = runTest {
        val peopleRepository = mockk<PeopleRepository>()
        val soon = Person(id = 1, firstName = "Soon", lastName = "A", birthday = LocalDate.of(1990, 6, 20))
        val sooner = Person(id = 2, firstName = "Sooner", lastName = "B", birthday = LocalDate.of(1985, 6, 14))
        val far = Person(id = 3, firstName = "Far", lastName = "C", birthday = LocalDate.of(1990, 12, 1))
        val noBirthday = Person(id = 4, firstName = "None", lastName = "D", birthday = null)
        every { peopleRepository.observePeople(any()) } returns flowOf(listOf(soon, sooner, far, noBirthday))

        val getAll = GetAllBirthdaysUseCase(peopleRepository, clock)
        val useCase = GetUpcomingBirthdaysUseCase(getAll)

        useCase(withinDays = 30).test {
            val upcoming = awaitItem()
            assertEquals(listOf(2L, 1L), upcoming.map { it.personId })
            assertEquals(4, upcoming.first().daysUntil)
            awaitComplete()
        }
    }
}
