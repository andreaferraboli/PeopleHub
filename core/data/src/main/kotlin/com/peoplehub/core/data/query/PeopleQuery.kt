package com.peoplehub.core.data.query

import com.peoplehub.core.domain.model.PeopleFilter
import com.peoplehub.core.domain.model.PeopleSort
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.util.DateCalculations
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate

/**
 * The people directory's search, filter and sort, in memory over the whole list (a personal address
 * book is a few hundred entries at most).
 *
 * Search replaces the old full-text index: the query is split into words and **every** word must
 * appear somewhere in the person's name, notes, tags or interests (keys and values). Matching ignores
 * case and accents, and a word matches inside a longer one, so "ann" finds "Giovanni".
 */
internal object PeopleQuery {
    fun apply(
        people: List<Person>,
        filter: PeopleFilter,
        today: LocalDate,
    ): List<Person> {
        val tokens = tokens(filter.query)
        return people
            .asSequence()
            .filter { filter.includeBirthdayOnly || !it.birthdayOnly }
            .filter { person -> filter.tags.isEmpty() || person.tags.any { it in filter.tags } }
            .filter { person -> matches(person, tokens) }
            .sortedWith(comparatorFor(filter.sort, today))
            .toList()
    }

    /** Whether every token of the query appears in the person's searchable text. */
    fun matches(person: Person, tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return true
        val haystack = normalize(searchableText(person))
        return tokens.all { it in haystack }
    }

    fun tokens(query: String): List<String> =
        normalize(query)
            .split(NON_ALPHANUMERIC)
            .filter { it.isNotBlank() }

    private fun searchableText(person: Person): String =
        buildString {
            append(person.firstName).append(' ')
            append(person.lastName).append(' ')
            append(person.notes).append(' ')
            person.tags.forEach { append(it).append(' ') }
            person.interests.forEach { append(it.key).append(' ').append(it.value).append(' ') }
        }

    private fun normalize(text: String): String =
        Normalizer
            .normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")

    private fun comparatorFor(sort: PeopleSort, today: LocalDate): Comparator<Person> =
        when (sort) {
            PeopleSort.NAME_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.fullName }
            PeopleSort.LAST_CHECK_IN -> compareBy(nullsFirst<Instant>()) { it.lastCheckInAt }
            PeopleSort.UPCOMING_BIRTHDAY ->
                compareBy { person ->
                    person.birthday?.let { DateCalculations.daysUntilBirthday(it, today) } ?: Int.MAX_VALUE
                }
        }

    private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
    private val COMBINING_MARKS = Regex("\\p{Mn}+")
}
