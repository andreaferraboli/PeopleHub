package com.peoplehub

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.peoplehub.core.data.legacy.LegacyDataCleanup
import com.peoplehub.core.domain.usecase.GetSettingsUseCase
import com.peoplehub.core.domain.usecase.ObserveAccountUseCase
import com.peoplehub.core.notifications.PeopleHubNotifier
import com.peoplehub.locale.AppLocale
import com.peoplehub.work.PeopleHubWorkScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point. Wires Hilt, supplies the [HiltWorkerFactory] to WorkManager and creates the
 * notification channels. It also (re)schedules the recurring background work whenever the reminder
 * hour or the exact-alarm preference changes — on launch, after sign-in, and when the hour is changed
 * in the cloud (by the MCP server) — and, once an account is signed in, removes the local database
 * left by versions that kept the data on the phone ([LegacyDataCleanup]).
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
    lateinit var getSettings: GetSettingsUseCase

    @Inject
    lateinit var observeAccount: ObserveAccountUseCase

    @Inject
    lateinit var legacyCleanup: LegacyDataCleanup

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannels()
        applicationScope.launch {
            getSettings()
                .map { it.dailyReminderHour to it.useExactAlarms }
                .distinctUntilChanged()
                .collect { workScheduler.scheduleRecurringWork() }
        }
        applicationScope.launch {
            observeAccount()
                .filterNotNull()
                .map { it.uid }
                .distinctUntilChanged()
                .collect { legacyCleanup.runIfNeeded() }
        }
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()
}
