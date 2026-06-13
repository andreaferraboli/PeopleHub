package com.peoplehub.feature.people

import com.peoplehub.core.dataio.PersonJsonImporter
import com.peoplehub.core.domain.model.Person
import com.peoplehub.core.domain.usecase.UpsertPersonUseCase
import javax.inject.Inject

/**
 * Imports a single person profile from an app-schema JSON document: parse and validate via the
 * data-io layer, then persist. Parsing and persistence failures are surfaced as a [Result].
 */
class ImportPersonUseCase @Inject constructor(
    private val importer: PersonJsonImporter,
    private val upsertPerson: UpsertPersonUseCase,
) {
    /** Parses [json] into a candidate [Person] without persisting it (for a confirmation preview). */
    fun preview(json: String): Result<Person> = importer.parse(json)

    /** Persists an already-previewed [person]. */
    suspend fun confirm(person: Person): Result<Person> =
        upsertPerson(person).map { person }
}
