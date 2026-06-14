package com.peoplehub.feature.events.edit

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Copies a picked image into the app's private internal storage so an event's card background
 * survives offline and independently of the source content provider, returning the absolute file
 * path. The copy runs on [Dispatchers.IO] so it never blocks the main thread.
 */
internal object EventImageStorage {
    private const val IMAGE_DIR = "event_backgrounds"

    suspend fun saveImage(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, IMAGE_DIR).apply { mkdirs() }
                val target = File(dir, "${UUID.randomUUID()}.jpg")
                val copied =
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                        true
                    } ?: false
                if (copied) target.absolutePath else null
            }.getOrNull()
        }
}
