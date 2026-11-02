package com.peoplehub.core.domain.sync

/**
 * Asks for a cloud sync to run soon, in the background, collapsing repeated requests. The app binds it
 * to a unique WorkManager job (network required), so it can be called after every local change.
 */
fun interface CloudSyncScheduler {
    fun requestSync()
}

/**
 * Re-schedules the daily sweeps after settings arrived from the cloud (the reminder hour may have
 * changed). The app binds it to its work scheduler.
 */
fun interface RecurringWorkRescheduler {
    suspend fun reschedule()
}
