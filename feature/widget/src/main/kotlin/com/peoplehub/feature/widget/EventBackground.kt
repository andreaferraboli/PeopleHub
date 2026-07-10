package com.peoplehub.feature.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Widget bitmaps travel to the launcher inside a `RemoteViews` transaction, which is size-limited, so
 * backgrounds are down-sampled well below the stored 1024 px crop before being handed over.
 */
private const val MAX_WIDTH = 512

/** Scrim opacity at the top and bottom of the image, matching the event card's gradient in the app. */
private const val SCRIM_TOP_ALPHA = 0.35f
private const val SCRIM_BOTTOM_ALPHA = 0.65f

/**
 * Loads an event's background photo for the widget, down-sampled and with the card's dark gradient
 * already painted on.
 *
 * Glance has no gradient brush, so the scrim is baked into the bitmap rather than layered as a
 * composable — which also keeps the gold/white text legible over bright photos exactly as it is on
 * the in-app card. Returns `null` when the file is missing or unreadable, and the widget then falls
 * back to its plain surface.
 */
internal suspend fun loadEventBackground(path: String): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            if (!file.exists()) return@runCatching null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0) return@runCatching null

            var sample = 1
            while (bounds.outWidth / sample > MAX_WIDTH) sample *= 2

            val decoded =
                BitmapFactory.decodeFile(
                    file.path,
                    BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    },
                ) ?: return@runCatching null

            decoded.withScrim()
        }.getOrNull()
    }

/** Paints the vertical darkening gradient over a copy of this bitmap. */
private fun Bitmap.withScrim(): Bitmap {
    val scrimmed = copy(Bitmap.Config.ARGB_8888, true) ?: return this
    val paint =
        Paint().apply {
            shader =
                LinearGradient(
                    0f,
                    0f,
                    0f,
                    scrimmed.height.toFloat(),
                    blackWithAlpha(SCRIM_TOP_ALPHA),
                    blackWithAlpha(SCRIM_BOTTOM_ALPHA),
                    Shader.TileMode.CLAMP,
                )
        }
    Canvas(scrimmed).drawRect(0f, 0f, scrimmed.width.toFloat(), scrimmed.height.toFloat(), paint)
    if (scrimmed !== this) recycle()
    return scrimmed
}

private fun blackWithAlpha(alpha: Float): Int = ((alpha * 0xFF).toInt() shl 24)
