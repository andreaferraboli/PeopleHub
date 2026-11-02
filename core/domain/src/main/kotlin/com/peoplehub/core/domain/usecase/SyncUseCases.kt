package com.peoplehub.core.domain.usecase

import com.peoplehub.core.domain.model.SyncStatus
import com.peoplehub.core.domain.repository.CloudSyncRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observes the cloud sync state. */
class ObserveSyncStatusUseCase
    @Inject
    constructor(
        private val repository: CloudSyncRepository,
    ) {
        operator fun invoke(): Flow<SyncStatus> = repository.status
    }

/** Signs in to the cloud with a Google ID token. */
class SignInToCloudUseCase
    @Inject
    constructor(
        private val repository: CloudSyncRepository,
    ) {
        suspend operator fun invoke(idToken: String): Result<Unit> = repository.signInWithGoogle(idToken)
    }

/** Signs out of the cloud, keeping local data. */
class SignOutOfCloudUseCase
    @Inject
    constructor(
        private val repository: CloudSyncRepository,
    ) {
        suspend operator fun invoke() = repository.signOut()
    }

/** Uploads every local row to an empty cloud; afterwards sync is automatic. */
class UploadToCloudUseCase
    @Inject
    constructor(
        private val repository: CloudSyncRepository,
    ) {
        suspend operator fun invoke(): Result<Unit> = repository.uploadAll()
    }

/** Replaces the local data with the cloud's; afterwards sync is automatic. */
class DownloadFromCloudUseCase
    @Inject
    constructor(
        private val repository: CloudSyncRepository,
    ) {
        suspend operator fun invoke(): Result<Unit> = repository.downloadAll()
    }

/** Runs a sync right away. */
class SyncNowUseCase
    @Inject
    constructor(
        private val repository: CloudSyncRepository,
    ) {
        suspend operator fun invoke(): Result<Unit> = repository.syncNow()
    }

/** Looks again whether the cloud already has data for the signed-in account. */
class RefreshCloudCheckUseCase
    @Inject
    constructor(
        private val repository: CloudSyncRepository,
    ) {
        suspend operator fun invoke() = repository.refreshCloudCheck()
    }
