package com.peoplehub.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.peoplehub.feature.widget.WidgetUpdater
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Periodic worker that refreshes every Glance widget from the latest data. */
@HiltWorker
class WidgetUpdateWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        WidgetUpdater.updateAll(appContext)
        return Result.success()
    }
}
