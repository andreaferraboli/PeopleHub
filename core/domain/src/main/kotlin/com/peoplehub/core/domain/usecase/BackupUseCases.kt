package com.peoplehub.core.domain.usecase

import com.peoplehub.core.domain.model.BackupData
import com.peoplehub.core.domain.model.ImportStrategy
import com.peoplehub.core.domain.model.MergeReport
import com.peoplehub.core.domain.repository.BackupRepository
import javax.inject.Inject

/** Builds a full snapshot of the user's data for export. */
class ExportBackupUseCase @Inject constructor(
    private val repository: BackupRepository,
) {
    suspend operator fun invoke(): BackupData = repository.exportAll()
}

/**
 * Imports a [BackupData] snapshot using the chosen [ImportStrategy], returning a [MergeReport]
 * describing what was added or skipped (empty for a wholesale replace).
 */
class ImportBackupUseCase @Inject constructor(
    private val repository: BackupRepository,
) {
    suspend operator fun invoke(data: BackupData, strategy: ImportStrategy): MergeReport =
        when (strategy) {
            ImportStrategy.REPLACE -> {
                repository.importReplace(data)
                MergeReport.Empty
            }
            ImportStrategy.MERGE -> repository.importMerge(data)
        }
}
