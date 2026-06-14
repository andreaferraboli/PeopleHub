package com.peoplehub

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.peoplehub.core.notifications.PeopleHubNotifier
import com.peoplehub.work.PeopleHubWorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Wires Hilt, supplies the [HiltWorkerFactory] to WorkManager, creates the
 * notification channels, and schedules the recurring background work on first launch.
 */
@HiltAndroidApp
class PeopleHubApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var notifier: PeopleHubNotifier

    @Inject
    lateinit var workScheduler: PeopleHubWorkScheduler

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannels()
        workScheduler.scheduleRecurringWork()
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()
}
