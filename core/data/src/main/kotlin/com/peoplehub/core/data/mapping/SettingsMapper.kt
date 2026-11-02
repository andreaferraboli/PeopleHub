package com.peoplehub.core.data.mapping

import com.peoplehub.core.domain.model.AppSettings
import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.domain.model.ReminderOffset

/** The part of [AppSettings] stored in `settings/main`. */
internal data class CloudSettings(
    val threshold: CheckInThreshold,
    val offsets: Set<ReminderOffset>,
    val dailyReminderHour: Int,
)

/**
 * Maps `settings/main`: the thresholds, the birthday offsets (as day counts, ones the app does not offer
 * are ignored) and the reminder hour, plus the phone's time zone on every write for the MCP's day maths.
 * Values are clamped to what the app accepts; a missing document or field reads as the default.
 */
internal object SettingsMapper {
    fun fromDoc(doc: Map<String, Any?>?): CloudSettings {
        val defaults = AppSettings()
        val data = doc.orEmpty()
        val warning = (data.int("warningDays") ?: defaults.defaultCheckInThreshold.warningDays).coerceAtLeast(1)
        val critical = (data.int("criticalDays") ?: defaults.defaultCheckInThreshold.criticalDays).coerceAtLeast(warning + 1)
        val offsets =
            if (data.containsKey("birthdayOffsets")) {
                val days = data.list("birthdayOffsets").mapNotNull { (it as? Number)?.toInt() }.toSet()
                ReminderOffset.entries.filter { it.daysBefore in days }.toSet()
            } else {
                defaults.birthdayReminderOffsets
            }
        return CloudSettings(
            threshold = CheckInThreshold(warning, critical),
            offsets = offsets,
            dailyReminderHour = (data.int("dailyReminderHour") ?: defaults.dailyReminderHour).coerceIn(0, MAX_HOUR),
        )
    }

    fun thresholdDoc(threshold: CheckInThreshold): Map<String, Any?> =
        mapOf("warningDays" to threshold.warningDays, "criticalDays" to threshold.criticalDays)

    fun offsetsDoc(offsets: Set<ReminderOffset>): Map<String, Any?> = mapOf("birthdayOffsets" to offsets.map { it.daysBefore }.sorted())

    fun hourDoc(hour: Int): Map<String, Any?> = mapOf("dailyReminderHour" to hour.coerceIn(0, MAX_HOUR))

    fun timeZoneDoc(zoneId: String): Map<String, Any?> = mapOf("timeZone" to zoneId)

    private const val MAX_HOUR = 23
}
