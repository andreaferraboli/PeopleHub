package com.peoplehub.core.dataio

import com.peoplehub.core.domain.model.Person

/**
 * Outcome of parsing a birthday CSV.
 *
 * @property people the rows that parsed successfully, as new [Person] records (id `0`).
 * @property errors one human-readable message per malformed row; a non-empty list does not abort
 * the import — valid rows are still returned in [people].
 */
data class CsvParseResult(
    val people: List<Person>,
    val errors: List<String>,
)
