package com.peoplehub.feature.people.edit

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Persists a person's photo into the app's private internal storage so it survives offline and
 * independently of the source content provider, returning the absolute file path. Work runs on
 * [Dispatchers.IO] so it never blocks the main thread.
 */
internal object PhotoStorage {
    private const val PHOTO_DIR = "person_photos"
    private const val JPEG_QUALITY = 90

    /** Persists an already-cropped [bitmap] as a JPEG and returns its absolute path. */
    suspend fun saveBitmap(context: Context, bitmap: Bitmap): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = newPhotoFile(context)
                target.outputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                }
                target.absolutePath
            }.getOrNull()
        }

    private fun newPhotoFile(context: Context): File {
        val dir = File(context.filesDir, PHOTO_DIR).apply { mkdirs() }
        return File(dir, "${UUID.randomUUID()}.jpg")
    }
}
