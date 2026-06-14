package com.peoplehub.di

import com.peoplehub.core.domain.usecase.CheckInPersonUseCase
import com.peoplehub.core.notifications.PeopleHubNotifier
import com.peoplehub.work.BirthdayAlarmScheduler
import com.peoplehub.work.PeopleHubWorkScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Dependency access for [android.content.BroadcastReceiver]s. Receivers resolve this via
 * `EntryPointAccessors.fromApplication(...)` instead of `@AndroidEntryPoint`, which keeps the
 * `super.onReceive` call out of the compile path entirely.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReceiverEntryPoint {
    fun workScheduler(): PeopleHubWorkScheduler

    fun birthdayAlarmScheduler(): BirthdayAlarmScheduler

    fun notifier(): PeopleHubNotifier

    fun checkInPerson(): CheckInPersonUseCase
}
