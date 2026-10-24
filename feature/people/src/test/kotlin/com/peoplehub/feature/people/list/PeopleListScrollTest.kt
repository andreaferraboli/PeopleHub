package com.peoplehub.feature.people.list

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.peoplehub.core.domain.model.AppSettings
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.usecase.BulkUpdatePeopleUseCase
import com.peoplehub.core.domain.usecase.GetPeopleUseCase
import com.peoplehub.core.domain.usecase.GetSettingsUseCase
import com.peoplehub.core.domain.usecase.ObserveAllTagsUseCase
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.feature.people.ImportPersonUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The Circle must scroll with a real drag. The redesign dropped the always-visible top bar but kept
 * the Scaffold's enterAlways nested-scroll connection, whose offset limit is never set without a bar,
 * so it swallowed every upward drag and the list stood still.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PeopleListScrollTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dragScrollsTheList() {
        showScreen()

        list().performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        assertTrue("the list did not move on a drag", scrollOffset() > 0f)
    }

    @Test
    fun dragScrollsTheListInSelectionMode() {
        showScreen()
        composeRule.onNodeWithText("Person 01").performTouchInput { longClick() }
        composeRule.waitForIdle()

        repeat(2) { list().performTouchInput { swipeUp() } }
        composeRule.waitForIdle()

        assertTrue("the list did not move on a drag", scrollOffset() > 0f)
    }

    private fun showScreen() {
        val viewModel = viewModelWithPeople(PEOPLE)
        composeRule.setContent {
            PeopleHubTheme(darkTheme = true) {
                PeopleListScreen(onPersonClick = {}, onAddPerson = {}, onRecordMeetup = {}, viewModel = viewModel)
            }
        }
        composeRule.onNodeWithText("Person 01").assertExists()
    }

    private fun list(): SemanticsNodeInteraction = composeRule.onNode(hasScrollToIndexAction())

    private fun scrollOffset(): Float =
        list()
            .fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
            .value()

    private fun viewModelWithPeople(count: Int): PeopleListViewModel {
        val getPeople = mockk<GetPeopleUseCase>()
        val observeAllTags = mockk<ObserveAllTagsUseCase>()
        val getSettings = mockk<GetSettingsUseCase>()
        val people =
            (1..count).map { i ->
                Person(id = i.toLong(), firstName = "Person", lastName = i.toString().padStart(2, '0'))
            }
        every { getPeople(any()) } returns flowOf(people)
        every { observeAllTags() } returns flowOf(emptyList())
        every { getSettings() } returns flowOf(AppSettings())
        return PeopleListViewModel(
            getPeople,
            observeAllTags,
            getSettings,
            mockk<ImportPersonUseCase>(relaxed = true),
            mockk<BulkUpdatePeopleUseCase>(relaxed = true),
            Clock.fixed(Instant.parse("2026-06-10T00:00:00Z"), ZoneOffset.UTC),
        )
    }

    private companion object {
        const val PEOPLE = 40
    }
}
