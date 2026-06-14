package com.peoplehub.core.domain.model

/** Ordering options for the people directory. */
enum class PeopleSort {
    /** Alphabetical by full name. */
    NAME_ASC,

    /** Least-recently checked in first (most urgent at the top). */
    LAST_CHECK_IN,

    /** Closest upcoming birthday first. */
    UPCOMING_BIRTHDAY,
}

/**
 * Combined query for the people list: full-text [query], the set of [tags] to filter by, and the
 * desired [sort] order.
 */
data class PeopleFilter(
    val query: String = "",
    val tags: Set<String> = emptySet(),
    val sort: PeopleSort = PeopleSort.NAME_ASC,
    val includeBirthdayOnly: Boolean = true,
)

/** Time-based filter for the events screen. */
enum class EventTimeFilter {
    ALL,
    UPCOMING,
    PAST,
}

/**
 * Combined query for the events screen.
 *
 * @property category restrict to a single category, or `null` for all categories.
 * @property personId restrict to events linked to a person, or `null` for all.
 */
data class EventFilter(
    val timeFilter: EventTimeFilter = EventTimeFilter.ALL,
    val category: String? = null,
    val personId: Long? = null,
)
