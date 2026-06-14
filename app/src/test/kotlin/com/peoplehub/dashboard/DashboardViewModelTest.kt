package com.peoplehub.dashboard

import app.cash.turbine.test
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.usecase.CheckInPersonUseCase
import com.peoplehub.core.domain.usecase.GetPeopleUseCase
import com.peoplehub.core.domain.usecase.GetPinnedEventUseCase
import com.peoplehub.core.domain.usecase.GetUpcomingBirthdaysUseCase
import com.peoplehub.core.domain.usecase.GetUrgentCheckInsUseCase
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.work.PeopleHubWorkScheduler
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `dashboard aggregates counts into a Success state`() =
        runTest {
            val getPeople = mockk<GetPeopleUseCase>()
            val getUrgentCheckIns = mockk<GetUrgentCheckInsUseCase>()
            val getUpcomingBirthdays = mockk<GetUpcomingBirthdaysUseCase>()
            val getPinnedEvent = mockk<GetPinnedEventUseCase>()
            val checkInPerson = mockk<CheckInPersonUseCase>(relaxed = true)
            val workScheduler = mockk<PeopleHubWorkScheduler>(relaxed = true)

            every { getPeople(any()) } returns
                flowOf(
                    listOf(
                        Person(id = 1, firstName = "Eleanor", lastName = "Vance"),
                        Person(id = 2, firstName = "Marcus", lastName = "Thorne"),
                    ),
                )
            every { getUrgentCheckIns() } returns flowOf(emptyList())
            every { getUpcomingBirthdays(any()) } returns flowOf(emptyList())
            every { getPinnedEvent() } returns flowOf(null)

            val viewModel =
                DashboardViewModel(
                    getPeople,
                    getUrgentCheckIns,
                    getUpcomingBirthdays,
                    getPinnedEvent,
                    checkInPerson,
                    workScheduler,
                )

            viewModel.state.test {
                var emitted = awaitItem()
                while (emitted is UiState.Loading) {
                    emitted = awaitItem()
                }
                assertTrue(emitted is UiState.Success)
                assertEquals(2, (emitted as UiState.Success).data.peopleCount)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
