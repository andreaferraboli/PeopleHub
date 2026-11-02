package com.peoplehub.core.sync.mapping

import com.peoplehub.core.domain.model.AppSettings
import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.domain.model.ReminderOffset

/** The synced subset of [AppSettings], as stored in `settings/main`. */
internal data class SyncedSettings(
    val warningDays: Int,
    val criticalDays: Int,
    val birthdayOffsets: List<Int>,
    val dailyReminderHour: Int,
    val timeZone: String,
) {
    /** A stable string form, to tell whether anything changed since the last sync. */
    val canonical: String
        get() = "$warningDays|$criticalDays|${birthdayOffsets.joinToString(",")}|$dailyReminderHour|$timeZone"

    val threshold: CheckInThreshold get() = CheckInThreshold(warningDays, criticalDays)

    /** The offsets as the app's enum; day counts the app does not offer are ignored. */
    val offsets: Set<ReminderOffset>
        get() = ReminderOffset.entries.filter { it.daysBefore in birthdayOffsets }.toSet()

    fun toDoc(): Map<String, Any?> =
        mapOf(
            "warningDays" to warningDays,
            "criticalDays" to criticalDays,
            "birthdayOffsets" to birthdayOffsets,
            "dailyReminderHour" to dailyReminderHour,
            "timeZone" to timeZone,
        )
}

/**
 * Maps `settings/main` to and from [AppSettings]. Only the thresholds, the birthday offsets (as day
 * counts) and the reminder hour travel, plus the phone's time zone for the MCP's day maths; the rest
 * of the settings stay on the phone. Values from the cloud are clamped to what the app accepts.
 */
internal object SettingsMapper {
    fun fromApp(settings: AppSettings, timeZone: String): SyncedSettings =
        SyncedSettings(
            warningDays = settings.defaultCheckInThreshold.warningDays,
            criticalDays = settings.defaultCheckInThreshold.criticalDays,
            birthdayOffsets = settings.birthdayReminderOffsets.map { it.daysBefore }.sorted(),
            dailyReminderHour = settings.dailyReminderHour,
            timeZone = timeZone,
        )

    /** Reads a cloud document, filling gaps from [fallback] (the current local settings). */
    fun fromDoc(doc: Map<String, Any?>, fallback: SyncedSettings): SyncedSettings {
        val warning = (doc.int("warningDays") ?: fallback.warningDays).coerceAtLeast(1)
        val critical = (doc.int("criticalDays") ?: fallback.criticalDays).coerceAtLeast(warning + 1)
        val offsets =
            if (doc.containsKey("birthdayOffsets")) {
                doc
                    .list("birthdayOffsets")
                    .mapNotNull { (it as? Number)?.toInt() }
                    .distinct()
                    .sorted()
            } else {
                fallback.birthdayOffsets
            }
        return SyncedSettings(
            warningDays = warning,
            criticalDays = critical,
            birthdayOffsets = offsets,
            dailyReminderHour = (doc.int("dailyReminderHour") ?: fallback.dailyReminderHour).coerceIn(0, MAX_HOUR),
            timeZone = doc.string("timeZone") ?: fallback.timeZone,
        )
    }

    private const val MAX_HOUR = 23
}
