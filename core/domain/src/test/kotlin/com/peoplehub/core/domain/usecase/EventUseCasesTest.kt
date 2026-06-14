package com.peoplehub.core.domain.usecase

import com.peoplehub.core.domain.model.PersonEvent
import com.peoplehub.core.domain.repository.EventRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class EventUseCasesTest {
    private val repository = mockk<EventRepository>(relaxed = true)

    @Test
    fun `add event rejects a blank title`() =
        runTest {
            val useCase = AddEventUseCase(repository)

            val result = useCase(PersonEvent(title = "  ", dateTime = LocalDateTime.of(2026, 11, 15, 19, 0)))

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { repository.upsertEvent(any()) }
        }

    @Test
    fun `add event trims the title and drops a blank category`() =
        runTest {
            coEvery { repository.upsertEvent(any()) } returns 5L
            val useCase = AddEventUseCase(repository)

            val result =
                useCase(
                    PersonEvent(title = " Gala ", dateTime = LocalDateTime.of(2026, 11, 15, 19, 0), category = "   "),
                )

            assertEquals(5L, result.getOrNull())
            coVerify { repository.upsertEvent(match { it.title == "Gala" && it.category == null }) }
        }
}
