package com.peoplehub.locale

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import java.util.Locale

/**
 * Self-contained per-app language support that works on every supported API level without AppCompat.
 *
 * The chosen language tag is stored in a tiny [android.content.SharedPreferences] file so it can be
 * read synchronously from `attachBaseContext` (which runs before Hilt/DataStore are available). Both
 * the [com.peoplehub.PeopleHubApplication] and [com.peoplehub.MainActivity] wrap their base context
 * with [wrap], so the entire app — including notifications built from the application context — uses
 * the selected language.
 *
 * [SYSTEM] means "follow the device language", which lets the `values-it` resources apply
 * automatically on Italian devices while still allowing a manual override.
 */
object AppLocale {
    /** Sentinel meaning "follow the device language". */
    const val SYSTEM: String = "system"

    /** Supported manual overrides, in display order, alongside [SYSTEM]. */
    val SUPPORTED_TAGS: List<String> = listOf(SYSTEM, "it", "en")

    private const val PREFS_NAME = "peoplehub_locale"
    private const val KEY_TAG = "language_tag"

    /** The persisted language tag, or [SYSTEM] when no override has been chosen. */
    fun currentTag(context: Context): String =
        context.prefs().getString(KEY_TAG, SYSTEM) ?: SYSTEM

    /** Persists [tag] and applies it to the JVM default locale so new formatting honours it too. */
    fun setTag(context: Context, tag: String) {
        context
            .prefs()
            .edit()
            .putString(KEY_TAG, tag)
            .apply()
        if (tag != SYSTEM) Locale.setDefault(Locale.forLanguageTag(tag))
    }

    /**
     * Returns a context configured for the persisted language, or [base] unchanged when following
     * the system language.
     */
    fun wrap(base: Context): Context {
        val tag = currentTag(base)
        if (tag == SYSTEM) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    private fun Context.prefs() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

/** Walks the [ContextWrapper] chain to find the hosting [Activity], or `null` if there is none. */
fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
