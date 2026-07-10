package com.peoplehub.feature.widget

import com.peoplehub.core.domain.usecase.GetEventsUseCase
import com.peoplehub.core.domain.usecase.GetPinnedEventUseCase
import com.peoplehub.core.domain.usecase.GetUpcomingBirthdaysUseCase
import com.peoplehub.core.domain.usecase.GetUrgentCheckInsUseCase
import com.peoplehub.core.domain.usecase.ObserveEventUseCase
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

    /** Backs the widget configuration screen's event list. */
    fun getEvents(): GetEventsUseCase

    /** Reads the single event a configured widget instance is bound to. */
    fun observeEvent(): ObserveEventUseCase

    fun clock(): Clock
}
