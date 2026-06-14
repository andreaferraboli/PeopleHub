package com.peoplehub.core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.peoplehub.core.ui.R

/**
 * Composable label helpers that turn raw day counts into localised, brand-styled strings, so no
 * feature has to hardcode user-facing text.
 */
object RelativeTime {
    /** "Seen today" / "Seen X days ago" / "Never checked in". */
    @Composable
    fun seenLabel(daysSince: Long?): String =
        when {
            daysSince == null -> stringResource(R.string.ui_never_seen)
            daysSince <= 0L -> stringResource(R.string.ui_seen_today)
            else -> stringResource(R.string.ui_seen_days_ago, daysSince.toInt())
        }
}
