package com.peoplehub.settings

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * A snapshot of the three system-level switches that can silently stop PeopleHub's reminders.
 *
 * All three fail closed and without any error: the notifier simply drops the notification when
 * [notificationsAllowed] is false, and a denied [exactAlarmsAllowed] or an app left under battery
 * optimisation delays or drops the daily trigger. Surfacing them in Settings turns "the app is
 * broken" into a one-tap fix.
 */
data class NotificationDiagnostics(
    val notificationsAllowed: Boolean,
    val exactAlarmsAllowed: Boolean,
    val batteryUnrestricted: Boolean,
) {
    /** True when nothing is blocking the notification engine. */
    val allClear: Boolean get() = notificationsAllowed && exactAlarmsAllowed && batteryUnrestricted
}

/** Reads the current state of the system switches that gate reminders. */
fun readNotificationDiagnostics(context: Context): NotificationDiagnostics =
    NotificationDiagnostics(
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        exactAlarmsAllowed = exactAlarmsAllowed(context),
        batteryUnrestricted = batteryUnrestricted(context),
    )

/** Opens this app's notification settings so a denied permission can be granted. */
fun openNotificationSettings(context: Context) {
    val intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivitySafely(intent, Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
}

/** Opens the system "Alarms & reminders" screen (Android 12+), where exact alarms are granted. */
fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivitySafely(intent, Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
}

/**
 * Opens the battery-optimisation list. The direct "allow" dialog needs
 * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which this app deliberately does not declare, so the user
 * flips the switch themselves.
 */
fun openBatterySettings(context: Context) {
    val intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivitySafely(intent, Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
}

private fun exactAlarmsAllowed(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
    return alarmManager.canScheduleExactAlarms()
}

private fun batteryUnrestricted(context: Context): Boolean {
    val powerManager = context.getSystemService(PowerManager::class.java) ?: return true
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * Starts [intent], falling back to the given app-settings action when the OEM has no such screen —
 * these system intents are optional and throw on some devices.
 */
private fun Context.startActivitySafely(intent: Intent, fallbackAction: String) {
    val started = runCatching { startActivity(intent) }.isSuccess
    if (started) return
    runCatching {
        startActivity(
            Intent(fallbackAction)
                .setData(Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
