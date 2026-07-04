package com.peoplehub.feature.events.edit

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Persists an event's card background into the app's private internal storage so it survives offline
 * and independently of the source content provider, returning the absolute file path. Work runs on
 * [Dispatchers.IO] so it never blocks the main thread.
 */
internal object EventImageStorage {
    private const val IMAGE_DIR = "event_backgrounds"
    private const val JPEG_QUALITY = 90

    /** Persists an already-cropped [bitmap] as a JPEG and returns its absolute path. */
    suspend fun saveBitmap(context: Context, bitmap: Bitmap): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, IMAGE_DIR).apply { mkdirs() }
                val target = File(dir, "${UUID.randomUUID()}.jpg")
                target.outputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                }
                target.absolutePath
            }.getOrNull()
        }
}
