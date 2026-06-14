package com.peoplehub.core.dataio

import com.peoplehub.core.dataio.mapper.toDto
import com.peoplehub.core.domain.model.Person
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Serializes a single [Person] profile to the app's JSON person schema — the exact same shape that
 * [PersonJsonImporter] accepts, so an exported file can be re-imported losslessly (including tags,
 * interests, the per-person check-in threshold and reminder flags).
 *
 * Output is pretty-printed and includes default values so every field round-trips.
 */
class PersonJsonExporter
    @Inject
    constructor() {
        private val json: Json =
            Json {
                prettyPrint = true
                encodeDefaults = true
            }

        /** Encodes [person] to a pretty-printed JSON document. */
        fun encode(person: Person): String = json.encodeToString(person.toDto())
    }
