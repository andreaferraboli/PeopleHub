package com.peoplehub.feature.widget

import com.peoplehub.core.domain.usecase.GetPinnedEventUseCase
import com.peoplehub.core.domain.usecase.GetUpcomingBirthdaysUseCase
import com.peoplehub.core.domain.usecase.GetUrgentCheckInsUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/**
 * Glance widgets are instantiated by the framework, not by Hilt, so they reach the dependency graph
 * through this entry point via `EntryPointAccessors.fromApplication(...)`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun getUpcomingBirthdays(): GetUpcomingBirthdaysUseCase

    fun getUrgentCheckIns(): GetUrgentCheckInsUseCase

    fun getPinnedEvent(): GetPinnedEventUseCase

    fun clock(): Clock
}
