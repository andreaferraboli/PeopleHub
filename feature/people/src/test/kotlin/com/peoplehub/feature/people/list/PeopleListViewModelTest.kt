package com.peoplehub.feature.people.list

import app.cash.turbine.test
import com.peoplehub.core.domain.model.AppSettings
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.usecase.GetPeopleUseCase
import com.peoplehub.core.domain.usecase.GetSettingsUseCase
import com.peoplehub.core.domain.usecase.ObserveAllTagsUseCase
import com.peoplehub.core.ui.state.UiState
import com.peoplehub.feature.people.ImportPersonUseCase
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class PeopleListViewModelTest {

    private val clock = Clock.fixed(Instant.parse("2026-06-10T00:00:00Z"), ZoneOffset.UTC)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state surfaces people as a Success list`() = runTest {
        val getPeople = mockk<GetPeopleUseCase>()
        val observeAllTags = mockk<ObserveAllTagsUseCase>()
        val getSettings = mockk<GetSettingsUseCase>()
        val importPerson = mockk<ImportPersonUseCase>(relaxed = true)
        every { getPeople(any()) } returns flowOf(listOf(Person(id = 1, firstName = "Eleanor", lastName = "Vance")))
        every { observeAllTags() } returns flowOf(listOf("Family"))
        every { getSettings() } returns flowOf(AppSettings())

        val viewModel = PeopleListViewModel(getPeople, observeAllTags, getSettings, importPerson, clock)

        viewModel.state.test {
            var state = awaitItem()
            while (state.listState !is UiState.Success) {
                state = awaitItem()
            }
            val data = (state.listState as UiState.Success).data
            assertEquals(1, data.size)
            assertEquals("Eleanor Vance", data.first().fullName)
            assertEquals(listOf("Family"), state.allTags)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty repository yields the Empty state`() = runTest {
        val getPeople = mockk<GetPeopleUseCase>()
        val observeAllTags = mockk<ObserveAllTagsUseCase>()
        val getSettings = mockk<GetSettingsUseCase>()
        val importPerson = mockk<ImportPersonUseCase>(relaxed = true)
        every { getPeople(any()) } returns flowOf(emptyList())
        every { observeAllTags() } returns flowOf(emptyList())
        every { getSettings() } returns flowOf(AppSettings())

        val viewModel = PeopleListViewModel(getPeople, observeAllTags, getSettings, importPerson, clock)

        viewModel.state.test {
            var state = awaitItem()
            while (state.listState is UiState.Loading) {
                state = awaitItem()
            }
            assertTrue(state.listState is UiState.Empty)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
