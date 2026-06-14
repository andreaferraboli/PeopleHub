package com.peoplehub.feature.people

import com.peoplehub.core.dataio.PersonJsonExporter
import com.peoplehub.core.domain.model.Person
import javax.inject.Inject

/**
 * Serializes a single person profile to the app's JSON person schema — the very same shape that the
 * import flow accepts — so a person can be exported and later re-imported losslessly.
 */
class ExportPersonUseCase
    @Inject
    constructor(
        private val exporter: PersonJsonExporter,
    ) {
        /** Encodes [person] to a pretty-printed JSON document. */
        operator fun invoke(person: Person): String = exporter.encode(person)
    }
