package com.peoplehub.core.data

import com.peoplehub.core.data.query.PeopleQuery
import com.peoplehub.core.domain.model.Interest
import com.peoplehub.core.domain.model.PeopleFilter
import com.peoplehub.core.domain.model.PeopleSort
import com.peoplehub.core.domain.model.Person
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class PeopleQueryTest {
    private val today = LocalDate.of(2026, 6, 10)
    private val giovanni =
        Person(id = "1", firstName = "Giovanni", lastName = "Rossi", notes = "Lavora in banca", tags = listOf("Calcetto"))
    private val nicolo = Person(id = "2", firstName = "Nicolò", lastName = "Bianchi", interests = listOf(Interest("Cibo", "Sushi")))
    private val anna = Person(id = "3", firstName = "Anna", lastName = "Verdi", birthdayOnly = true, birthday = LocalDate.of(1990, 6, 12))
    private val people = listOf(giovanni, nicolo, anna)

    private fun search(query: String) = PeopleQuery.apply(people, PeopleFilter(query = query), today).map { it.id }

    @Test
    fun `search matches name, notes, tags and interests`() {
        assertEquals(listOf("1"), search("rossi"))
        assertEquals(listOf("1"), search("banca"))
        assertEquals(listOf("1"), search("calcetto"))
        assertEquals(listOf("2"), search("sushi"))
        assertEquals(listOf("2"), search("cibo"))
    }

    @Test
    fun `every word must match, inside words, ignoring case and accents`() {
        assertEquals(listOf("3", "1"), search("ann"))
        assertEquals(listOf("2"), search("NICOLO"))
        assertEquals(listOf("1"), search("gio, ross"))
        assertEquals(emptyList<String>(), search("giovanni sushi"))
        assertEquals(listOf("3", "1", "2"), search("  "))
    }

    @Test
    fun `birthday-only entries and tags filter, then the sort applies`() {
        val tracked = PeopleQuery.apply(people, PeopleFilter(includeBirthdayOnly = false), today).map { it.id }
        assertEquals(listOf("1", "2"), tracked)

        val tagged = PeopleQuery.apply(people, PeopleFilter(tags = setOf("Calcetto")), today).map { it.id }
        assertEquals(listOf("1"), tagged)

        val byBirthday = PeopleQuery.apply(people, PeopleFilter(sort = PeopleSort.UPCOMING_BIRTHDAY), today).map { it.id }
        assertEquals("3", byBirthday.first())

        val seen =
            listOf(giovanni.copy(lastCheckInAt = Instant.ofEpochMilli(5)), nicolo.copy(lastCheckInAt = Instant.ofEpochMilli(1)), anna)
        val byLastSeen = PeopleQuery.apply(seen, PeopleFilter(sort = PeopleSort.LAST_CHECK_IN), today).map { it.id }
        assertEquals(listOf("3", "2", "1"), byLastSeen)
    }
}
