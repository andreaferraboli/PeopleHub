package com.peoplehub.core.domain.usecase

import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.repository.PeopleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PeopleUseCasesTest {
    private val repository = mockk<PeopleRepository>(relaxed = true)

    @Test
    fun `upsert fails when the first name is blank`() =
        runTest {
            val useCase = UpsertPersonUseCase(repository)

            val result = useCase(Person(firstName = "   ", lastName = "Vance"))

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { repository.upsertPerson(any()) }
        }

    @Test
    fun `upsert trims fields and de-duplicates tags before persisting`() =
        runTest {
            coEvery { repository.upsertPerson(any()) } returns 42L
            val useCase = UpsertPersonUseCase(repository)

            val result =
                useCase(
                    Person(firstName = " Eleanor ", lastName = " Vance ", tags = listOf("Family", "Family", "  ")),
                )

            assertEquals(42L, result.getOrNull())
            coVerify {
                repository.upsertPerson(
                    match { it.firstName == "Eleanor" && it.lastName == "Vance" && it.tags == listOf("Family") },
                )
            }
        }
}
