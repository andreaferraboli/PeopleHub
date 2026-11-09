package com.peoplehub.core.domain.usecase

import com.peoplehub.core.domain.model.CheckIn
import com.peoplehub.core.domain.model.Outing
import com.peoplehub.core.domain.model.OutingAttendee
import com.peoplehub.core.domain.model.OutingTimes
import com.peoplehub.core.domain.repository.CheckInRepository
import com.peoplehub.core.domain.repository.PeopleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class OutingUseCasesTest {
    private val now: Instant = Instant.parse("2026-06-10T09:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val day: LocalDate = LocalDate.of(2026, 6, 10)
    private val noon: Instant = day.atTime(NOON_HOUR, 0).toInstant(ZoneOffset.UTC)

    private fun attendee(personId: String, checkInId: String) =
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
            every { checkInRepository.newOutingId() } returnsMany listOf("50", "51")
            val recorded = slot<List<CheckIn>>()
            coEvery { checkInRepository.recordCheckIns(capture(recorded)) } returns Unit
            val useCase = RecordMeetupUseCase(checkInRepository, peopleRepository, clock)

            useCase(personIds = listOf("1", "2"), days = listOf(day.plusDays(1), day), note = "Weekend")

            val byOuting = recorded.captured.groupBy { it.outingId }
            assertEquals(setOf("50", "51"), byOuting.keys)
            // Day-ordered ids: the earlier day gets the first reserved id.
            assertEquals(setOf(day), byOuting.getValue("50").map { it.timestamp.atZone(ZoneOffset.UTC).toLocalDate() }.toSet())
            assertEquals(setOf("1", "2"), byOuting.getValue("50").map { it.personId }.toSet())
            assertEquals(setOf("1", "2"), byOuting.getValue("51").map { it.personId }.toSet())
        }

    @Test
    fun `a meetup with a time range starts and ends each day at those times, past midnight on the next day`() =
        runTest {
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            every { checkInRepository.newOutingId() } returns "60"
            val recorded = slot<List<CheckIn>>()
            coEvery { checkInRepository.recordCheckIns(capture(recorded)) } returns Unit
            val useCase = RecordMeetupUseCase(checkInRepository, peopleRepository, clock)

            useCase(
                personIds = listOf("1", "2"),
                days = listOf(day),
                times = OutingTimes(start = LocalTime.of(18, 0), end = LocalTime.of(7, 30)),
            )

            val start = day.atTime(18, 0).toInstant(ZoneOffset.UTC)
            val end = day.plusDays(1).atTime(7, 30).toInstant(ZoneOffset.UTC)
            assertEquals(2, recorded.captured.size)
            assertTrue(recorded.captured.all { it.timestamp == start && it.endAt == end && it.outingId == "60" })
        }

    @Test
    fun `moving an outing keeps the duration of its end`() =
        runTest {
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            val evening = day.atTime(21, 0).toInstant(ZoneOffset.UTC)
            coEvery { checkInRepository.getOuting(OUTING_ID) } returns
                outing(attendee(personId = "1", checkInId = "10"))
                    .copy(timestamp = evening, endAt = evening.plus(Duration.ofHours(3)))
            val moved = day.plusDays(1)
            val useCase = UpdateOutingUseCase(checkInRepository, peopleRepository, clock)

            val result = useCase(outingId = OUTING_ID, day = moved, personIds = listOf("1"))

            assertTrue(result.isSuccess)
            val start = moved.atTime(21, 0).toInstant(ZoneOffset.UTC)
            coVerify {
                checkInRepository.updateCheckIn(match { it.timestamp == start && it.endAt == start.plus(Duration.ofHours(3)) })
            }
        }

    @Test
    fun `an outing's time range can be rewritten or cleared`() =
        runTest {
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            coEvery { checkInRepository.getOuting(OUTING_ID) } returns
                outing(attendee(personId = "1", checkInId = "10")).copy(endAt = noon.plus(Duration.ofHours(2)))
            val useCase = UpdateOutingUseCase(checkInRepository, peopleRepository, clock)

            useCase(
                outingId = OUTING_ID,
                day = day,
                personIds = listOf("1"),
                times = OutingTimes(start = LocalTime.of(9, 0), end = LocalTime.of(11, 30)),
            )
            coVerify {
                checkInRepository.updateCheckIn(
                    match {
                        it.timestamp == day.atTime(9, 0).toInstant(ZoneOffset.UTC) &&
                            it.endAt == day.atTime(11, 30).toInstant(ZoneOffset.UTC)
                    },
                )
            }

            useCase(outingId = OUTING_ID, day = day, personIds = listOf("1"), clearEnd = true)
            coVerify { checkInRepository.updateCheckIn(match { it.timestamp == noon && it.endAt == null }) }
        }

    @Test
    fun `editing one person's row drops an end that is not after its start`() =
        runTest {
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            coEvery { checkInRepository.getOuting(OUTING_ID) } returns outing(attendee(personId = "1", checkInId = "10"))
            val useCase = UpdateCheckInUseCase(checkInRepository, peopleRepository)

            useCase(CheckIn(id = "10", personId = "1", timestamp = noon, outingId = OUTING_ID, endAt = noon))

            coVerify { checkInRepository.updateCheckIn(match { it.id == "10" && it.endAt == null }) }
        }

    @Test
    fun `a time range ending at or before its start ends the next day`() {
        val night = OutingTimes(start = LocalTime.of(18, 0), end = LocalTime.of(7, 30))
        val morning = OutingTimes(start = LocalTime.of(9, 0), end = LocalTime.of(11, 30))

        assertTrue(night.endsNextDay)
        assertEquals(day.plusDays(1).atTime(7, 30).toInstant(ZoneOffset.UTC), night.endOn(day, ZoneOffset.UTC))
        assertEquals(day.atTime(11, 30).toInstant(ZoneOffset.UTC), morning.endOn(day, ZoneOffset.UTC))
        assertEquals(night, OutingTimes.of(night.startOn(day, ZoneOffset.UTC), night.endOn(day, ZoneOffset.UTC), ZoneOffset.UTC))
        assertEquals(null, OutingTimes.of(noon, noon, ZoneOffset.UTC))
    }

    @Test
    fun `updating an outing moves the people who stayed, drops the ones removed and adds the new ones`() =
        runTest {
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            coEvery { checkInRepository.getOuting(OUTING_ID) } returns
                outing(attendee(personId = "1", checkInId = "10"), attendee(personId = "2", checkInId = "11"))
            coEvery { checkInRepository.recordCheckIn(any()) } returns "12"
            val moved = day.plusDays(2)
            val useCase = UpdateOutingUseCase(checkInRepository, peopleRepository, clock)

            val result = useCase(outingId = OUTING_ID, day = moved, personIds = listOf("1", "3"), note = " Brunch ")

            assertTrue(result.isSuccess)
            val movedNoon = moved.atTime(NOON_HOUR, 0).toInstant(ZoneOffset.UTC)
            coVerify { checkInRepository.deleteCheckIns(listOf("11")) }
            coVerify {
                checkInRepository.updateCheckIn(
                    match { it.id == "10" && it.personId == "1" && it.timestamp == movedNoon && it.note == "Brunch" },
                )
            }
            coVerify {
                checkInRepository.recordCheckIn(
                    match { it.personId == "3" && it.outingId == OUTING_ID && it.timestamp == movedNoon },
                )
            }
        }

    @Test
    fun `moving an outing to another day keeps its time of day`() =
        runTest {
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            val evening = day.atTime(21, 15).toInstant(ZoneOffset.UTC)
            coEvery { checkInRepository.getOuting(OUTING_ID) } returns
                outing(attendee(personId = "1", checkInId = "10")).copy(timestamp = evening)
            val moved = day.minusDays(3)
            val useCase = UpdateOutingUseCase(checkInRepository, peopleRepository, clock)

            val result = useCase(outingId = OUTING_ID, day = moved, personIds = listOf("1"))

            assertTrue(result.isSuccess)
            val expected = moved.atTime(21, 15).toInstant(ZoneOffset.UTC)
            coVerify { checkInRepository.updateCheckIn(match { it.id == "10" && it.timestamp == expected }) }
        }

    @Test
    fun `updating an outing re-derives the last-seen of everyone touched on either side`() =
        runTest {
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            coEvery { checkInRepository.getOuting(OUTING_ID) } returns
                outing(attendee(personId = "1", checkInId = "10"), attendee(personId = "2", checkInId = "11"))
            coEvery { checkInRepository.recordCheckIn(any()) } returns "12"
            coEvery { checkInRepository.latestTimestamp(any()) } returns null
            val useCase = UpdateOutingUseCase(checkInRepository, peopleRepository, clock)

            useCase(outingId = OUTING_ID, day = day, personIds = listOf("1", "3"))

            // 1 stayed, 2 was removed, 3 was added — all three need their tracker re-derived.
            coVerify { peopleRepository.updateLastCheckIn("1", null) }
            coVerify { peopleRepository.updateLastCheckIn("2", null) }
            coVerify { peopleRepository.updateLastCheckIn("3", null) }
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

            val result = useCase(outingId = OUTING_ID, day = day, personIds = listOf("1"))

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
                outing(attendee(personId = "1", checkInId = "10"), attendee(personId = "2", checkInId = "11"))
            coEvery { checkInRepository.latestTimestamp(any()) } returns survivor
            val useCase = DeleteOutingUseCase(checkInRepository, peopleRepository)

            useCase(OUTING_ID)

            coVerify { checkInRepository.deleteCheckIns(listOf("10", "11")) }
            coVerify { peopleRepository.updateLastCheckIn("1", survivor.toEpochMilli()) }
            coVerify { peopleRepository.updateLastCheckIn("2", survivor.toEpochMilli()) }
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
                outing(attendee(personId = "1", checkInId = "10"), attendee(personId = "2", checkInId = "11"))
            every { checkInRepository.newOutingId() } returns "99"
            val useCase = UpdateCheckInUseCase(checkInRepository, peopleRepository)

            useCase(CheckIn(id = "10", personId = "1", timestamp = noon, note = "Just me", outingId = OUTING_ID))

            coVerify { checkInRepository.updateCheckIn(match { it.id == "10" && it.outingId == "99" }) }
        }

    @Test
    fun `editing a solo row keeps its outing id`() =
        runTest {
            val checkInRepository = mockk<CheckInRepository>(relaxed = true)
            val peopleRepository = mockk<PeopleRepository>(relaxed = true)
            coEvery { checkInRepository.getOuting(OUTING_ID) } returns outing(attendee(personId = "1", checkInId = "10"))
            val useCase = UpdateCheckInUseCase(checkInRepository, peopleRepository)

            useCase(CheckIn(id = "10", personId = "1", timestamp = noon, note = "Coffee", outingId = OUTING_ID))

            coVerify { checkInRepository.updateCheckIn(match { it.outingId == OUTING_ID }) }
            verify(exactly = 0) { checkInRepository.newOutingId() }
        }

    private companion object {
        const val OUTING_ID = "42"
        const val NOON_HOUR = 12
    }
}
