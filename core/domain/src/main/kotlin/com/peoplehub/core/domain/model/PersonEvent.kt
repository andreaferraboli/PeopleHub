package com.peoplehub.core.domain.model

import java.time.LocalDateTime

/**
 * A significant personal event with an elapsed/remaining day counter.
 *
 * Events may sit in the past or the future; the UI renders "X days ago" or "in X days" relative to
 * the current day. An event can optionally be [pinnedToWidget] to surface it on the home-screen
 * widget, and optionally linked to a [personId].
 *
 * @property category free-form tag (e.g. "Gala", "Anniversary") rendered as a coloured chip.
 */
data class PersonEvent(
    val id: Long = 0L,
    val title: String,
    val dateTime: LocalDateTime,
    val description: String? = null,
    val category: String? = null,
    val personId: Long? = null,
    val pinnedToWidget: Boolean = false,
)
