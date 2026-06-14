package com.peoplehub.core.domain.model

import java.time.Instant

/**
 * A single recorded interaction with a [Person] ("Ho visto [Nome] oggi").
 *
 * @property timestamp the instant the check-in was recorded.
 * @property note optional free-text note attached to the interaction.
 */
data class CheckIn(
    val id: Long = 0L,
    val personId: Long,
    val timestamp: Instant,
    val note: String? = null,
)

/**
 * Per-person (or global default) thresholds, expressed in days, that drive the semantic colouring
 * of the frequency tracker.
 *
 * A person is [CheckInStatus.FRESH] while the days since the last check-in stay below [warningDays],
 * [CheckInStatus.DUE] up to [criticalDays], and [CheckInStatus.OVERDUE] beyond it.
 */
data class CheckInThreshold(
    val warningDays: Int,
    val criticalDays: Int,
) {
    companion object {
        /** Sensible global default: warn after two weeks, escalate after a month. */
        val Default: CheckInThreshold = CheckInThreshold(warningDays = 14, criticalDays = 30)
    }
}

/**
 * Semantic status of a person's check-in recency, mapped to Material 3 colour roles in the UI layer.
 */
enum class CheckInStatus {
    /** Recently seen — within the warning window. */
    FRESH,

    /** Approaching the critical window — show a warning colour. */
    DUE,

    /** Beyond the critical window — show an error colour. */
    OVERDUE,

    /** Never checked in. */
    NEVER,

    ;

    companion object {
        /**
         * Resolves the status from the number of [daysSince] the last check-in (or `null` if there
         * has never been one) against the supplied [threshold].
         */
        fun of(daysSince: Long?, threshold: CheckInThreshold): CheckInStatus =
            when {
                daysSince == null -> NEVER
                daysSince < threshold.warningDays -> FRESH
                daysSince < threshold.criticalDays -> DUE
                else -> OVERDUE
            }
    }
}

/**
 * A [Person] enriched with their computed check-in urgency, used by the home "urgent check-ins"
 * section and the check-in widget.
 */
data class CheckInUrgency(
    val person: Person,
    val daysSince: Long?,
    val status: CheckInStatus,
)
