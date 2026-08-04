package com.peoplehub.core.domain.usecase

import com.peoplehub.core.domain.model.CheckIn
import com.peoplehub.core.domain.model.Outing
import com.peoplehub.core.domain.model.OutingAttendee
import com.peoplehub.core.domain.repository.CheckInRepository
import com.peoplehub.core.domain.repository.PeopleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class OutingUseCasesTest {
    private val now: Instant = Instant.parse("2026-06-10T09:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val day: LocalDate = LocalDate.of(2026, 6, 10)
    private val noon: Instant = day.atTime(NOON_HOUR, 0).toInstant(ZoneOffset.UTC)

    private fun attendee(personId: Long, checkInId: Long) =
        OutingAttendee(
            checkInId = checkInId,
            personId = personId,
            fullName = "Person $personId",
            initials = "P$personId",
            photoPath = null,
        )

    private fun outing(vararg attendees: OutingAttendee) =
        Outing(
            id = OUTING_ID,
            date = day,
            timestamp = noon,
            note = "Dinner",
            attendees = attendees.toList(),
            checkInIds = attendees.map { it.checkInId },
        )

    @Test
    fun `a meetup gives every attendee of a day the same outing id, one id per day`() =
        runTest {
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            coEvery { checkInRepository.newOutingId() } returns 50L
            val recorded = slot<List<CheckIn>>()
            coEvery { checkInRepository.recordCheckIns(capture(recorded)) } returns Unit
            val useCase = RecordMeetupUseCase(checkInRepository, peopleRepository, clock)

            useCase(personIds = listOf(1L, 2L), days = listOf(day.plusDays(1), day), note = "Weekend")

            val byOuting = recorded.captured.groupBy { it.outingId }
            assertEquals(setOf(50L, 51L), byOuting.keys)
            // Day-ordered ids: the earlier day gets the first reserved id.
            assertEquals(setOf(day), byOuting.getValue(50L).map { it.timestamp.atZone(ZoneOffset.UTC).toLocalDate() }.toSet())
            assertEquals(setOf(1L, 2L), byOuting.getValue(50L).map { it.personId }.toSet())
            assertEquals(setOf(1L, 2L), byOuting.getValue(51L).map { it.personId }.toSet())
        }

    @Test
    fun `updating an outing moves the people who stayed, drops the ones removed and adds the new ones`() =
        runTest {
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            coEvery { checkInRepository.getOuting(OUTING_ID) } returns
                outing(attendee(personId = 1L, checkInId = 10L), attendee(personId = 2L, checkInId = 11L))
            coEvery { checkInRepository.recordCheckIn(any()) } returns 12L
            val moved = day.plusDays(2)
            val useCase = UpdateOutingUseCase(checkInRepository, peopleRepository, clock)

            val result = useCase(outingId = OUTING_ID, day = moved, personIds = listOf(1L, 3L), note = " Brunch ")

            assertTrue(result.isSuccess)
            val movedNoon = moved.atTime(NOON_HOUR, 0).toInstant(ZoneOffset.UTC)
            coVerify { checkInRepository.deleteCheckIns(listOf(11L)) }
            coVerify {
                checkInRepository.updateCheckIn(
                    match { it.id == 10L && it.personId == 1L && it.timestamp == movedNoon && it.note == "Brunch" },
                )
            }
            coVerify {
                checkInRepository.recordCheckIn(
                    match { it.personId == 3L && it.outingId == OUTING_ID && it.timestamp == movedNoon },
                )
            }
        }

    @Test
    fun `updating an outing re-derives the last-seen of everyone touched on either side`() =
        runTest {
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            coEvery { checkInRepository.getOuting(OUTING_ID) } returns
                outing(attendee(personId = 1L, checkInId = 10L), attendee(personId = 2L, checkInId = 11L))
            coEvery { checkInRepository.recordCheckIn(any()) } returns 12L
            coEvery { checkInRepository.latestTimestamp(any()) } returns null
            val useCase = UpdateOutingUseCase(checkInRepository, peopleRepository, clock)

            useCase(outingId = OUTING_ID, day = day, personIds = listOf(1L, 3L))

            // 1 stayed, 2 was removed, 3 was added — all three need their tracker re-derived.
            coVerify { peopleRepository.updateLastCheckIn(1L, null) }
            coVerify { peopleRepository.updateLastCheckIn(2L, null) }
            coVerify { peopleRepository.updateLastCheckIn(3L, null) }
        }

    @Test
    fun `an outing cannot be emptied of its attendees`() =
        runTest {
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            val useCase = UpdateOutingUseCase(checkInRepository, peopleRepository, clock)

            val result = useCase(outingId = OUTING_ID, day = day, personIds = emptyList())

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { checkInRepository.deleteCheckIns(any()) }
            coVerify(exactly = 0) { checkInRepository.updateCheckIn(any()) }
        }

    @Test
    fun `updating an outing that no longer exists fails without writing`() =
        runTest {
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            coEvery { checkInRepository.getOuting(OUTING_ID) } returns null
            val useCase = UpdateOutingUseCase(checkInRepository, peopleRepository, clock)

            val result = useCase(outingId = OUTING_ID, day = day, personIds = listOf(1L))

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { checkInRepository.updateCheckIn(any()) }
            coVerify(exactly = 0) { checkInRepository.recordCheckIn(any()) }
        }

    @Test
    fun `deleting an outing removes every attendee's row and re-derives their last-seen`() =
        runTest {
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            val survivor = now.minusSeconds(1)
            coEvery { checkInRepository.getOuting(OUTING_ID) } returns
                outing(attendee(personId = 1L, checkInId = 10L), attendee(personId = 2L, checkInId = 11L))
            coEvery { checkInRepository.latestTimestamp(any()) } returns survivor
            val useCase = DeleteOutingUseCase(checkInRepository, peopleRepository)

            useCase(OUTING_ID)

            coVerify { checkInRepository.deleteCheckIns(listOf(10L, 11L)) }
            coVerify { peopleRepository.updateLastCheckIn(1L, survivor.toEpochMilli()) }
            coVerify { peopleRepository.updateLastCheckIn(2L, survivor.toEpochMilli()) }
        }

    @Test
    fun `deleting an outing that is already gone is a no-op`() =
        runTest {
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            coEvery { checkInRepository.getOuting(OUTING_ID) } returns null
            val useCase = DeleteOutingUseCase(checkInRepository, peopleRepository)

            useCase(OUTING_ID)

            coVerify(exactly = 0) { checkInRepository.deleteCheckIns(any()) }
            coVerify(exactly = 0) { peopleRepository.updateLastCheckIn(any(), any()) }
        }

    @Test
    fun `editing one person's row detaches it from an outing shared with others`() =
        runTest {
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            coEvery { checkInRepository.getOuting(OUTING_ID) } returns
                outing(attendee(personId = 1L, checkInId = 10L), attendee(personId = 2L, checkInId = 11L))
            coEvery { checkInRepository.newOutingId() } returns 99L
            val useCase = UpdateCheckInUseCase(checkInRepository, peopleRepository)

            useCase(CheckIn(id = 10L, personId = 1L, timestamp = noon, note = "Just me", outingId = OUTING_ID))

            coVerify { checkInRepository.updateCheckIn(match { it.id == 10L && it.outingId == 99L }) }
        }

    @Test
    fun `editing a solo row keeps its outing id`() =
        runTest {
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            coEvery { checkInRepository.getOuting(OUTING_ID) } returns outing(attendee(personId = 1L, checkInId = 10L))
            val useCase = UpdateCheckInUseCase(checkInRepository, peopleRepository)

            useCase(CheckIn(id = 10L, personId = 1L, timestamp = noon, note = "Coffee", outingId = OUTING_ID))

            coVerify { checkInRepository.updateCheckIn(match { it.outingId == OUTING_ID }) }
            coVerify(exactly = 0) { checkInRepository.newOutingId() }
        }

    private companion object {
        const val OUTING_ID = 42L
        const val NOON_HOUR = 12
    }
}
