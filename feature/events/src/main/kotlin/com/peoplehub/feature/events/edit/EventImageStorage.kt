package com.peoplehub.feature.events.edit

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Persists an event's card background into the app's private internal storage so it survives offline
 * and independently of the source content provider, returning the absolute file path. Work runs on
 * [Dispatchers.IO] so it never blocks the main thread.
 *
 * Two files are kept per background: the cropped image that the cards render, and the untouched
 * original it was cropped from. Keeping the original is what makes "reposition" possible — a crop is
 * lossy, so re-framing a cropped file could only ever zoom further in.
 */
internal object EventImageStorage {
    private const val IMAGE_DIR = "event_backgrounds"
    private const val SOURCE_DIR = "event_backgrounds/sources"
    private const val JPEG_QUALITY = 90

    /** Persists an already-cropped [bitmap] as a JPEG and returns its absolute path. */
    suspend fun saveBitmap(context: Context, bitmap: Bitmap): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = File(context.filesDir, IMAGE_DIR).apply { mkdirs() }.newJpeg()
                target.outputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                }
                target.absolutePath
            }.getOrNull()
        }

    /**
     * Copies the picked image at [uri] verbatim into internal storage as the crop source, returning
     * its absolute path. The bytes are copied rather than re-encoded so no quality is lost before the
     * user has framed anything, and so the app keeps read access after the picker's grant expires.
     */
    suspend fun saveSource(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = File(context.filesDir, SOURCE_DIR).apply { mkdirs() }.newJpeg()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use(input::copyTo)
                } ?: return@runCatching null
                target.absolutePath
            }.getOrNull()
        }

    private fun File.newJpeg(): File = File(this, "${UUID.randomUUID()}.jpg")
}
