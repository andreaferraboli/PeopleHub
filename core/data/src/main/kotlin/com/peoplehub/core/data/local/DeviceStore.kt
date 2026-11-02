package com.peoplehub.core.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.peoplehub.core.domain.model.CropTransform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** Qualifies the device-only DataStore (`peoplehub_device`): photo map, widget pin, install id. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class DeviceDataStore

/** An event's local background: the cropped image, its uncropped original and the framing. */
internal data class EventMedia(
    val imagePath: String?,
    val sourcePath: String?,
    val crop: CropTransform,
)

/**
 * What stays on the phone next to the cloud data: which local photo belongs to which person, each
 * event's background and crop, and which event is pinned to the widget. Keyed by document id, so it
 * survives any change made in the cloud; photo files themselves live in internal storage.
 */
internal interface LocalMedia {
    val personPhotos: Flow<Map<String, String>>
    val eventMedia: Flow<Map<String, EventMedia>>
    val pinnedEventId: Flow<String?>

    suspend fun setPersonPhoto(personId: String, path: String?)

    suspend fun setEventMedia(eventId: String, media: EventMedia?)

    suspend fun setPinnedEvent(eventId: String?)

    /** Drops the entries of people and events that no longer exist (the files stay, they may be shared). */
    suspend fun forget(personIds: Collection<String>, eventIds: Collection<String>)

    /**
     * Adds many entries at once (a backup import, the old local database); entries already present
     * win, and [pinnedEventId] is taken only if no event is pinned yet.
     */
    suspend fun putAll(photos: Map<String, String>, events: Map<String, EventMedia>, pinnedEventId: String? = null)
}

/** [LocalMedia] plus the install id, in the `peoplehub_device` DataStore. */
@Singleton
internal class DeviceStore
    @Inject
    constructor(
        @DeviceDataStore private val dataStore: DataStore<Preferences>,
    ) : LocalMedia {
        override val personPhotos: Flow<Map<String, String>> =
            dataStore.data
                .map { prefs -> prefs.entriesWith(PHOTO_PREFIX) }
                .distinctUntilChanged()

        override val eventMedia: Flow<Map<String, EventMedia>> =
            dataStore.data
                .map { prefs -> prefs.entriesWith(EVENT_PREFIX).mapValues { (_, encoded) -> decode(encoded) } }
                .distinctUntilChanged()

        override val pinnedEventId: Flow<String?> = dataStore.data.map { it[PINNED_EVENT] }.distinctUntilChanged()

        /** A random id for this install, drawn once; documents it writes carry `android:<id>`. */
        suspend fun installId(): String {
            dataStore.data.first()[INSTALL_ID]?.let { return it }
            val fresh = UUID.randomUUID().toString()
            dataStore.edit { prefs -> if (prefs[INSTALL_ID] == null) prefs[INSTALL_ID] = fresh }
            return dataStore.data.first()[INSTALL_ID] ?: fresh
        }

        override suspend fun setPersonPhoto(personId: String, path: String?) {
            dataStore.edit { prefs ->
                val key = stringPreferencesKey(PHOTO_PREFIX + personId)
                if (path.isNullOrBlank()) prefs.remove(key) else prefs[key] = path
            }
        }

        override suspend fun setEventMedia(eventId: String, media: EventMedia?) {
            dataStore.edit { prefs ->
                val key = stringPreferencesKey(EVENT_PREFIX + eventId)
                if (media == null || media.imagePath.isNullOrBlank()) prefs.remove(key) else prefs[key] = encode(media)
            }
        }

        override suspend fun setPinnedEvent(eventId: String?) {
            dataStore.edit { prefs -> if (eventId == null) prefs.remove(PINNED_EVENT) else prefs[PINNED_EVENT] = eventId }
        }

        override suspend fun forget(personIds: Collection<String>, eventIds: Collection<String>) {
            if (personIds.isEmpty() && eventIds.isEmpty()) return
            dataStore.edit { prefs ->
                personIds.forEach { prefs.remove(stringPreferencesKey(PHOTO_PREFIX + it)) }
                eventIds.forEach { prefs.remove(stringPreferencesKey(EVENT_PREFIX + it)) }
                if (prefs[PINNED_EVENT] in eventIds) prefs.remove(PINNED_EVENT)
            }
        }

        override suspend fun putAll(
            photos: Map<String, String>,
            events: Map<String, EventMedia>,
            pinnedEventId: String?,
        ) {
            if (photos.isEmpty() && events.isEmpty() && pinnedEventId == null) return
            dataStore.edit { prefs ->
                photos.forEach { (id, path) ->
                    val key = stringPreferencesKey(PHOTO_PREFIX + id)
                    if (prefs[key] == null) prefs[key] = path
                }
                events.forEach { (id, media) ->
                    val key = stringPreferencesKey(EVENT_PREFIX + id)
                    if (prefs[key] == null && !media.imagePath.isNullOrBlank()) prefs[key] = encode(media)
                }
                if (pinnedEventId != null && prefs[PINNED_EVENT] == null) prefs[PINNED_EVENT] = pinnedEventId
            }
        }

        private fun Preferences.entriesWith(prefix: String): Map<String, String> =
            asMap()
                .filterKeys { it.name.startsWith(prefix) }
                .mapNotNull { (key, value) -> (value as? String)?.let { key.name.removePrefix(prefix) to it } }
                .toMap()

        private companion object {
            const val PHOTO_PREFIX = "photo/"
            const val EVENT_PREFIX = "event_bg/"
            const val SEPARATOR = '\u001F'
            const val FIELDS = 5
            val PINNED_EVENT = stringPreferencesKey("pinned_event_id")
            val INSTALL_ID = stringPreferencesKey("install_id")

            fun encode(media: EventMedia): String =
                listOf(
                    media.imagePath.orEmpty(),
                    media.sourcePath.orEmpty(),
                    media.crop.zoom.toString(),
                    media.crop.panX.toString(),
                    media.crop.panY.toString(),
                ).joinToString(SEPARATOR.toString())

            fun decode(encoded: String): EventMedia {
                val parts = encoded.split(SEPARATOR)

                fun part(index: Int) = parts.getOrNull(index).orEmpty()
                val crop =
                    if (parts.size == FIELDS) {
                        CropTransform(
                            zoom = part(2).toFloatOrNull() ?: 1f,
                            panX = part(3).toFloatOrNull() ?: 0f,
                            panY = part(4).toFloatOrNull() ?: 0f,
                        )
                    } else {
                        CropTransform.Default
                    }
                return EventMedia(
                    imagePath = part(0).ifEmpty { null },
                    sourcePath = part(1).ifEmpty { null },
                    crop = crop,
                )
            }
        }
    }
