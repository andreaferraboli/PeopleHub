package com.peoplehub.core.domain.repository

import com.peoplehub.core.domain.model.BackupData
import com.peoplehub.core.domain.model.MergeReport

/**
 * Orchestrates full-database snapshots for the backup/restore feature. Serialisation to JSON/CSV is
 * a separate concern handled by the data-io layer; this interface deals only in domain aggregates.
 */
interface BackupRepository {

    /** Builds an in-memory snapshot of all people, check-ins and events. */
    suspend fun exportAll(): BackupData

    /** Wipes existing data and replaces it wholesale with [data]. */
    suspend fun importReplace(data: BackupData)

    /**
     * Merges [data] into the existing database, deduplicating people by the composite key
     * `firstName + lastName + birthday` and returning a [MergeReport] of what changed.
     */
    suspend fun importMerge(data: BackupData): MergeReport
}
