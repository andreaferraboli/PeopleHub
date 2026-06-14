package com.peoplehub.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.peoplehub.di.ReceiverEntryPoint
import dagger.hilt.android.EntryPointAccessors

/** Re-creates notification channels and re-schedules all background work after a device reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, ReceiverEntryPoint::class.java)
        entryPoint.notifier().ensureChannels()
        entryPoint.workScheduler().scheduleRecurringWork()
    }
}
