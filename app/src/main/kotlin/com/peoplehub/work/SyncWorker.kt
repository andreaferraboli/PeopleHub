package com.peoplehub.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.peoplehub.core.domain.usecase.SyncNowUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs one cloud sync (pull, then push). Enqueued as unique work by
 * [PeopleHubWorkScheduler.enqueueSync] after local changes and remote notifications, and every
 * 6 hours as a backstop. A failure (offline, timeout) is retried with WorkManager's backoff a few
 * times; after that the next request or the periodic pass picks the pending changes up.
 */
@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val syncNow: SyncNowUseCase,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result =
            when {
                syncNow().isSuccess -> Result.success()
                runAttemptCount < MAX_ATTEMPTS -> Result.retry()
                else -> Result.failure()
            }

        private companion object {
            const val MAX_ATTEMPTS = 3
        }
    }
