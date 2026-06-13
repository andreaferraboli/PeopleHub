package com.peoplehub.core.domain.model

/**
 * The full, schema-versioned snapshot of the user's data used by the global backup/restore feature.
 *
 * [schemaVersion] is incremented on every breaking change to the serialised shape so that an
 * importer can detect and reject (or migrate) incompatible files.
 */
data class BackupData(
    val schemaVersion: Int,
    val people: List<Person>,
    val checkIns: List<CheckIn>,
    val events: List<PersonEvent>,
) {
    companion object {
        /** Current backup schema version produced by this build. */
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

/** Strategy applied when importing a [BackupData] over existing data. */
enum class ImportStrategy {
    /** Wipe all existing data and replace it with the imported snapshot. */
    REPLACE,

    /** Keep existing data and add only non-duplicate records. */
    MERGE,
}

/**
 * Outcome of an import, surfaced to the user once the operation completes.
 */
data class MergeReport(
    val peopleAdded: Int,
    val peopleSkipped: Int,
    val eventsAdded: Int,
    val checkInsAdded: Int,
) {
    companion object {
        val Empty: MergeReport = MergeReport(0, 0, 0, 0)
    }
}
