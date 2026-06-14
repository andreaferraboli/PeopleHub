package com.peoplehub.core.dataio

import com.peoplehub.core.dataio.dto.InterestDto
import com.peoplehub.core.dataio.dto.PersonDto
import com.peoplehub.core.dataio.mapper.toDomain
import com.peoplehub.core.domain.model.CheckInThreshold
import com.peoplehub.core.domain.model.Interest
import com.peoplehub.core.domain.model.Person
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Imports a single [Person] profile from the app's JSON person schema (the same shape as a
 * [PersonDto] inside a backup file).
 *
 * Two modes are supported:
 * - [parse] treats the document as a brand-new person (id forced to `0`).
 * - [merge] updates an existing person in place: every field **present** in the JSON overwrites the
 *   stored value, while keys that are absent keep the value already on the profile. The id is never
 *   changed.
 */
class PersonJsonImporter
    @Inject
    constructor() {
        private val json: Json =
            Json {
                ignoreUnknownKeys = true
            }

        /**
         * Parses [json] into a new [Person].
         *
         * @return a successful [Result] with the parsed person (id reset to `0`), or a failure if the
         * JSON is malformed or the first/last name is blank.
         */
        fun parse(json: String): Result<Person> =
            runCatching {
                val dto = this.json.decodeFromString<PersonDto>(json)
                require(dto.firstName.isNotBlank()) { "Person firstName must not be blank" }
                require(dto.lastName.isNotBlank()) { "Person lastName must not be blank" }
                dto.toDomain().copy(id = 0L)
            }

        /**
         * Merges [json] onto an [existing] person. Only keys actually present in the document are
         * applied; everything else is preserved, and the [Person.id] is always kept.
         *
         * @return the merged person, or a failure if the JSON is malformed (e.g. an invalid birthday).
         */
        fun merge(json: String, existing: Person): Result<Person> =
            runCatching {
                val obj = this.json.parseToJsonElement(json).jsonObject

                val warningDays = if ("warningDays" in obj) obj.intOrNull("warningDays") else existing.checkInThreshold?.warningDays
                val criticalDays = if ("criticalDays" in obj) obj.intOrNull("criticalDays") else existing.checkInThreshold?.criticalDays

                existing.copy(
                    firstName = obj.stringOr("firstName", existing.firstName),
                    lastName = obj.stringOr("lastName", existing.lastName),
                    photoPath = if ("photoPath" in obj) obj.stringOrNull("photoPath") else existing.photoPath,
                    birthday = if ("birthday" in obj) obj.localDateOrNull("birthday") else existing.birthday,
                    tags = if ("tags" in obj) obj.stringList("tags") else existing.tags,
                    interests = if ("interests" in obj) obj.interests() else existing.interests,
                    notes = obj.stringOr("notes", existing.notes),
                    lastCheckInAt =
                        if ("lastCheckInEpochMillis" in obj) {
                            obj.longOrNull("lastCheckInEpochMillis")?.let(Instant::ofEpochMilli)
                        } else {
                            existing.lastCheckInAt
                        },
                    checkInThreshold = thresholdFrom(warningDays, criticalDays),
                    notificationsEnabled =
                        if ("notificationsEnabled" in obj) {
                            obj.booleanOr("notificationsEnabled", existing.notificationsEnabled)
                        } else {
                            existing.notificationsEnabled
                        },
                    birthdayOnly =
                        if ("birthdayOnly" in obj) {
                            obj.booleanOr("birthdayOnly", existing.birthdayOnly)
                        } else {
                            existing.birthdayOnly
                        },
                )
            }

        private fun JsonObject.stringOr(key: String, fallback: String): String =
            this[key]?.jsonPrimitive?.contentOrNull ?: fallback

        private fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

        private fun JsonObject.booleanOr(key: String, fallback: Boolean): Boolean =
            this[key]?.jsonPrimitive?.booleanOrNull ?: fallback

        private fun JsonObject.intOrNull(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

        private fun JsonObject.longOrNull(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull

        private fun JsonObject.localDateOrNull(key: String): LocalDate? =
            this[key]?.jsonPrimitive?.contentOrNull?.let { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }

        private fun JsonObject.stringList(key: String): List<String> =
            this[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

        private fun JsonObject.interests(): List<Interest> =
            this["interests"]
                ?.jsonArray
                ?.map { json.decodeFromJsonElement<InterestDto>(it) }
                ?.map { Interest(key = it.key, value = it.value, id = it.id) }
                ?: emptyList()

        private fun thresholdFrom(warningDays: Int?, criticalDays: Int?): CheckInThreshold? =
            if (warningDays != null && criticalDays != null) {
                CheckInThreshold(warningDays = warningDays, criticalDays = criticalDays)
            } else {
                null
            }
    }
