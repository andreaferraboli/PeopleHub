package com.peoplehub

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.peoplehub.core.domain.repository.CloudSyncRepository
import com.peoplehub.core.notifications.PeopleHubNotifier
import com.peoplehub.locale.AppLocale
import com.peoplehub.work.PeopleHubWorkScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point. Wires Hilt, supplies the [HiltWorkerFactory] to WorkManager, creates the
 * notification channels, and schedules the recurring background work on every launch (scheduling
 * reads the configured reminder hour from DataStore, hence the application-scoped coroutine). It also
 * tells the cloud sync engine when the app comes to the foreground and leaves it.
 */
@HiltAndroidApp
class PeopleHubApplication : Application(), Configuration.Provider {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var notifier: PeopleHubNotifier

    @Inject
    lateinit var workScheduler: PeopleHubWorkScheduler

    @Inject
    lateinit var cloudSync: CloudSyncRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannels()
        applicationScope.launch { workScheduler.scheduleRecurringWork() }
        // Cloud listeners run only while some screen is visible; background changes go through work.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = cloudSync.setForeground(true)

                override fun onStop(owner: LifecycleOwner) = cloudSync.setForeground(false)
            },
        )
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()
}
