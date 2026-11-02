package com.peoplehub.di

import com.peoplehub.core.domain.sync.CloudSyncScheduler
import com.peoplehub.core.domain.sync.RecurringWorkRescheduler
import com.peoplehub.core.domain.widget.WidgetRefresher
import com.peoplehub.work.PeopleHubWorkScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import kotlin.random.Random

/** Application-wide bindings that don't belong to a specific data source. */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    /**
     * Lets feature modules ask for a widget refresh without depending on `:feature:widget`; only the
     * app module sees both sides.
     */
    @Provides
    @Singleton
    fun provideWidgetRefresher(scheduler: PeopleHubWorkScheduler): WidgetRefresher =
        WidgetRefresher(scheduler::updateWidgetsNow)

    /** Lets the sync engine ask for a background sync without knowing about WorkManager. */
    @Provides
    @Singleton
    fun provideCloudSyncScheduler(scheduler: PeopleHubWorkScheduler): CloudSyncScheduler =
        CloudSyncScheduler(scheduler::enqueueSync)

    /** Re-schedules the daily sweeps after settings (the reminder hour) arrive from the cloud. */
    @Provides
    @Singleton
    fun provideRecurringWorkRescheduler(scheduler: PeopleHubWorkScheduler): RecurringWorkRescheduler =
        RecurringWorkRescheduler(scheduler::scheduleRecurringWork)

    /**
     * The system clock used by every "now"-dependent use case. Injecting it (rather than calling
     * [java.time.Instant.now] directly) keeps the domain layer deterministic and testable.
     */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    /**
     * The randomness source behind the deliberately-irregular reminder cadence. Injecting it (rather
     * than calling [kotlin.random.Random] directly) keeps the jitter logic deterministic in tests.
     */
    @Provides
    @Singleton
    fun provideRandom(): Random = Random.Default
}
