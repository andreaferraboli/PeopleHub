package com.peoplehub.feature.people

import com.peoplehub.core.dataio.PersonJsonImporter
import com.peoplehub.core.domain.model.CheckIn
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.repository.CheckInRepository
import com.peoplehub.core.domain.usecase.UpsertPersonUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class ImportPersonUseCaseTest {
    private val importer = mockk<PersonJsonImporter>()
    private val upsertPerson = mockk<UpsertPersonUseCase>()
    private val checkInRepository = mockk<CheckInRepository>(relaxed = true)
    private val useCase = ImportPersonUseCase(importer, upsertPerson, checkInRepository)

    private val person = Person(id = 0L, firstName = "Marco", lastName = "Rossi")

    private fun instantOn(day: String): Instant {
        val noon = Instant.parse(day).atZone(ZoneId.systemDefault()).withHour(12)
        return noon.toInstant()
    }

    private suspend fun importHistory(history: List<CheckIn>): List<CheckIn> {
        coEvery { upsertPerson(any()) } returns Result.success(7L)
        coEvery { checkInRepository.newOutingId() } returns 100L
        every { importer.parseCheckIns(any()) } returns history
        val written = slot<List<CheckIn>>()

        val result = useCase.confirm(person, sourceJson = "{}")

        assertTrue(result.isSuccess)
        coVerify { checkInRepository.recordCheckIns(capture(written)) }
        return written.captured
    }

    @Test
    fun `imported history is re-keyed onto outing ids this database has never used`() =
        runTest {
            val history =
                listOf(
                    CheckIn(personId = 1L, timestamp = instantOn("2026-05-01T00:00:00Z"), note = "Dinner", outingId = 3L),
                    CheckIn(personId = 1L, timestamp = instantOn("2026-05-08T00:00:00Z"), note = "Coffee", outingId = 4L),
                )

            val written = importHistory(history)

            // Never the source file's ids (3, 4): those belong to whichever install exported it, and
            // reusing them would fuse the import into unrelated local outings.
            assertEquals(listOf(100L, 101L), written.map { it.outingId })
            assertTrue(written.all { it.personId == 7L })
        }

    @Test
    fun `rows that shared an outing in the file still share one after the import`() =
        runTest {
            val sameDay = instantOn("2026-05-01T00:00:00Z")
            val history =
                listOf(
                    CheckIn(personId = 1L, timestamp = sameDay, note = "Trip", outingId = 3L),
                    CheckIn(personId = 1L, timestamp = sameDay.plusSeconds(60), note = "Trip", outingId = 3L),
                    CheckIn(personId = 1L, timestamp = instantOn("2026-06-01T00:00:00Z"), note = "Lunch", outingId = 9L),
                )

            val written = importHistory(history)

            assertEquals(listOf(100L, 100L, 101L), written.map { it.outingId })
        }

    @Test
    fun `a legacy history with no outing ids is regrouped by day and description, not collapsed`() =
        runTest {
            // Files written before outings existed carry 0 everywhere. Keeping that would fold the whole
            // history into a single bogus outing spanning every date in it.
            val history =
                listOf(
                    CheckIn(personId = 1L, timestamp = instantOn("2026-05-01T00:00:00Z"), note = "Dinner"),
                    CheckIn(personId = 1L, timestamp = instantOn("2026-05-01T00:00:00Z"), note = "Dinner"),
                    CheckIn(personId = 1L, timestamp = instantOn("2026-05-01T00:00:00Z"), note = "Cinema"),
                    CheckIn(personId = 1L, timestamp = instantOn("2026-07-14T00:00:00Z"), note = "Dinner"),
                )

            val written = importHistory(history)

            assertEquals(listOf(100L, 100L, 101L, 102L), written.map { it.outingId })
            assertTrue(written.none { it.outingId == 0L })
        }
}
