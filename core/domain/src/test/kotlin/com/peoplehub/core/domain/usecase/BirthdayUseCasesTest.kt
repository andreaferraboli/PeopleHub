package com.peoplehub.core.domain.usecase

import app.cash.turbine.test
import com.peoplehub.core.domain.model.AppSettings
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.model.ReminderOffset
import com.peoplehub.core.domain.repository.PeopleRepository
import com.peoplehub.core.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class BirthdayUseCasesTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-10T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `upcoming birthdays keeps only those within the window and sorts by soonest`() =
        runTest {
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

    @Test
    fun `due reminders fire for people who never opted in to notifications`() =
        runTest {
            // Regression: the per-person opt-in defaults to off, so gating birthdays on it muted
            // every profile added or imported without deliberately flipping the switch.
            val optedOut =
                Person(
                    id = 7,
                    firstName = "Daniela",
                    lastName = "Zia",
                    birthday = LocalDate.of(1960, 6, 10),
                    notificationsEnabled = false,
                )
            val useCase = dueRemindersUseCase(listOf(optedOut), setOf(ReminderOffset.SAME_DAY))

            val due = useCase()

            assertEquals(listOf(7L), due.map { it.personId })
            assertTrue(due.single().isToday)
        }

    @Test
    fun `due reminders only include distances matching an enabled offset`() =
        runTest {
            val today = Person(id = 1, firstName = "Today", lastName = "A", birthday = LocalDate.of(1990, 6, 10))
            val inSeven = Person(id = 2, firstName = "Week", lastName = "B", birthday = LocalDate.of(1990, 6, 17))
            val inThree = Person(id = 3, firstName = "Three", lastName = "C", birthday = LocalDate.of(1990, 6, 13))
            val useCase =
                dueRemindersUseCase(
                    listOf(today, inSeven, inThree),
                    setOf(ReminderOffset.SAME_DAY, ReminderOffset.ONE_WEEK),
                )

            val due = useCase()

            assertEquals(listOf(1L, 2L), due.map { it.personId })
            assertEquals(listOf(true, false), due.map { it.isToday })
        }

    @Test
    fun `due reminders are empty when every offset is disabled`() =
        runTest {
            val person = Person(id = 1, firstName = "Today", lastName = "A", birthday = LocalDate.of(1990, 6, 10))

            assertTrue(dueRemindersUseCase(listOf(person), emptySet())().isEmpty())
        }

    private fun dueRemindersUseCase(people: List<Person>, offsets: Set<ReminderOffset>): GetDueBirthdayRemindersUseCase {
        val peopleRepository = mockk<PeopleRepository>()
        every { peopleRepository.observePeople(any()) } returns flowOf(people)
        val settingsRepository = mockk<SettingsRepository>()
        every { settingsRepository.settings } returns flowOf(AppSettings(birthdayReminderOffsets = offsets))
        return GetDueBirthdayRemindersUseCase(GetAllBirthdaysUseCase(peopleRepository, clock), settingsRepository)
    }
}
