package com.peoplehub.core.dataio

import com.peoplehub.core.dataio.dto.PersonDto
import com.peoplehub.core.dataio.mapper.toDomain
import com.peoplehub.core.domain.model.Person
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Imports a single [Person] profile from the app's JSON person schema (the same shape as a
 * [PersonDto] inside a backup file).
 *
 * The imported person is always treated as new: its id is forced to `0` so the persistence layer
 * assigns a fresh identifier rather than overwriting an existing record.
 */
class PersonJsonImporter @Inject constructor() {

    private val json: Json = Json {
        ignoreUnknownKeys = true
    }

    /**
     * Parses [json] into a new [Person].
     *
     * @return a successful [Result] with the parsed person (id reset to `0`), or a failure if the
     * JSON is malformed or the first/last name is blank.
     */
    fun parse(json: String): Result<Person> = runCatching {
        val dto = this.json.decodeFromString<PersonDto>(json)
        require(dto.firstName.isNotBlank()) { "Person firstName must not be blank" }
        require(dto.lastName.isNotBlank()) { "Person lastName must not be blank" }
        dto.toDomain().copy(id = 0L)
    }
}
