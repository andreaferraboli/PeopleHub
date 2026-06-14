package com.peoplehub.core.dataio

import com.peoplehub.core.dataio.dto.BackupDto
import com.peoplehub.core.dataio.mapper.toDomain
import com.peoplehub.core.dataio.mapper.toDto
import com.peoplehub.core.domain.model.BackupData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Serializes and deserializes the full [BackupData] snapshot to and from the app's pretty-printed
 * JSON backup format.
 *
 * Decoding is tolerant of unknown keys (forward compatibility) and rejects files whose
 * `schemaVersion` is newer than [BackupData.CURRENT_SCHEMA_VERSION].
 */
class BackupSerializer
    @Inject
    constructor() {
        private val json: Json =
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        /** Maps [data] to its DTO form and serializes it to a pretty-printed JSON string. */
        fun encode(data: BackupData): String = json.encodeToString(data.toDto())

        /**
         * Parses [json] into a [BackupData], validating that its schema version is supported.
         *
         * @return a successful [Result] with the decoded snapshot, or a failure carrying the parse or
         * validation error (e.g. an [IllegalArgumentException] for an unsupported, newer schema).
         */
        fun decode(json: String): Result<BackupData> =
            runCatching {
                val dto = this.json.decodeFromString<BackupDto>(json)
                require(dto.schemaVersion <= BackupData.CURRENT_SCHEMA_VERSION) {
                    "Unsupported backup schema version ${dto.schemaVersion}; " +
                        "this build supports up to ${BackupData.CURRENT_SCHEMA_VERSION}"
                }
                dto.toDomain()
            }
    }
