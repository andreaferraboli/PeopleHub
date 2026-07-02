package com.peoplehub.core.dataio

import com.peoplehub.core.dataio.mapper.toDto
import com.peoplehub.core.domain.model.CheckIn
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

        /**
         * Encodes [person] together with their full meetup [history] to a pretty-printed JSON
         * document, so every recorded date round-trips and not just the last one.
         */
        fun encode(person: Person, history: List<CheckIn> = emptyList()): String =
            json.encodeToString(person.toDto().copy(checkIns = history.map { it.toDto() }))
    }
