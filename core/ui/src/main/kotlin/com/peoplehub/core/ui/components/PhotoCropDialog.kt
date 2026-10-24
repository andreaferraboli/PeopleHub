package com.peoplehub.core.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.peoplehub.core.domain.model.CropTransform
import com.peoplehub.core.ui.R
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.VellutoElevation
import com.peoplehub.core.ui.modifier.cardSurface
import com.peoplehub.core.ui.modifier.safeDialogPadding
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoColors
import com.peoplehub.core.ui.theme.VellutoShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_ZOOM = 8f
private const val OUTPUT_LONGEST = 1024
private const val LOAD_MAX_DIM = 2048

/**
 * A crop dialog letting the user pan and pinch-zoom an image to frame the part they want before it
 * becomes a stored photo. The on-screen preview and the produced bitmap share the same transform, so
 * the result is exactly what the user sees inside the frame.
 *
 * [aspectRatio] is the frame's width / height: `1f` gives a square crop (person avatars), a value
 * like `16f / 9f` gives a wide banner crop (event backgrounds). The output bitmap keeps that ratio
 * with its longest side capped at 1024 px.
 *
 * Pass [initialCrop] to reopen a previously saved framing of the same [sourceUri] — the dialog
 * starts exactly where the user left it, so a background can be repositioned rather than re-framed
 * from scratch. [onCropped] hands back both the rendered bitmap and the transform that produced it,
 * so callers can persist the latter alongside the image.
 */
@Composable
fun PhotoCropDialog(
    sourceUri: Uri,
    onCancel: () -> Unit,
    onCropped: (Bitmap, CropTransform) -> Unit,
    aspectRatio: Float = 1f,
    initialCrop: CropTransform = CropTransform.Default,
) {
    val context = LocalContext.current
    var bitmap by remember(sourceUri) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(sourceUri) { mutableStateOf(false) }

    LaunchedEffect(sourceUri) {
        val loaded = loadOrientedBitmap(context, sourceUri)
        if (loaded == null) loadFailed = true else bitmap = loaded
    }

    // Transform state, relative to the cover-fit baseline: zoom == 1 fills the frame.
    // `pan` is stored as a fraction of the frame side (x of width, y of height) so it is independent
    // of the on-screen size and maps identically onto the larger output bitmap.
    var zoom by remember(sourceUri) { mutableFloatStateOf(initialCrop.zoom.coerceIn(1f, MAX_ZOOM)) }
    var pan by remember(sourceUri) { mutableStateOf(Offset(initialCrop.panX, initialCrop.panY)) }

    // The crop stage is always dark, like a photo viewer, whatever the app theme: the image reads
    // truest on a dark surround. The accent is carried over so a re-coloured app stays consistent.
    PeopleHubTheme(darkTheme = true, accent = PeopleHubTheme.colors.accent) {
        val c = PeopleHubTheme.colors
        val spacing = PeopleHubTheme.spacing
        Dialog(
            onDismissRequest = onCancel,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .safeDialogPadding()
                        .padding(spacing.xl)
                        .cardSurface(c, VellutoShapes.Hero, VellutoElevation.Hero)
                        .clip(VellutoShapes.Hero)
                        .padding(spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.l),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.m),
                ) {
                    IconTile(VellutoIcons.Crop)
                    Text(
                        text = stringResource(R.string.crop_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = c.text,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                }
                Text(
                    text = stringResource(R.string.crop_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.text2,
                )

                val current = bitmap
                BoxWithConstraints(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspectRatio)
                            .background(c.bg)
                            .clipToBounds()
                            .cropFrame(c),
                    contentAlignment = Alignment.Center,
                ) {
                    val boxW = constraints.maxWidth.toFloat()
                    val boxH = constraints.maxHeight.toFloat()
                    when {
                        current != null ->
                            Canvas(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(aspectRatio)
                                        .pointerInput(current, boxW, boxH) {
                                            detectTransformGestures { _, panChange, zoomChange, _ ->
                                                zoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
                                                val moved = pan + Offset(panChange.x / boxW, panChange.y / boxH)
                                                pan = clampPan(moved, current, boxW, boxH, zoom)
                                            }
                                        },
                            ) {
                                drawIntoCanvas { canvas ->
                                    canvas.nativeCanvas.drawBitmap(
                                        current,
                                        previewMatrix(current, boxW, boxH, zoom, pan),
                                        FilterPaint,
                                    )
                                }
                            }

                        loadFailed ->
                            Text(
                                text = stringResource(R.string.crop_load_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.err,
                                modifier = Modifier.padding(spacing.l),
                            )

                        else ->
                            CircularProgressIndicator(
                                color = c.accent,
                                trackColor = c.track,
                                strokeWidth = 3.dp,
                                strokeCap = StrokeCap.Round,
                            )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.m),
                ) {
                    VellutoButton(
                        text = stringResource(R.string.crop_cancel),
                        onClick = onCancel,
                        style = VellutoButtonStyle.Ghost,
                        modifier = Modifier.weight(1f),
                    )
                    VellutoButton(
                        text = stringResource(R.string.crop_confirm),
                        onClick = {
                            val src = bitmap ?: return@VellutoButton
                            onCropped(
                                cropBitmap(src, aspectRatio, zoom, pan),
                                CropTransform(zoom = zoom, panX = pan.x, panY = pan.y),
                            )
                        },
                        enabled = bitmap != null,
                        icon = VellutoIcons.Check,
                        height = spacing.touchTarget,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Length of each accent corner bracket of the crop frame. */
private val CornerLength = 22.dp

/** Stroke of the accent corner brackets and of the frame outline. */
private val CornerStroke = 3.dp
private val FrameStroke = 1.dp

/**
 * Draws the crop frame over the image: rule-of-thirds guides, a thin accent outline and thick accent
 * corner brackets. The frame is the exact rectangle that becomes the photo, so it stays square-cornered.
 */
private fun Modifier.cropFrame(c: VellutoColors): Modifier =
    drawWithContent {
        drawContent()
        val w = size.width
        val h = size.height
        val guide = c.hi
        for (i in 1..2) {
            val x = w * i / THIRDS
            val y = h * i / THIRDS
            drawLine(guide, Offset(x, 0f), Offset(x, h), strokeWidth = FrameStroke.toPx())
            drawLine(guide, Offset(0f, y), Offset(w, y), strokeWidth = FrameStroke.toPx())
        }
        val frame = FrameStroke.toPx()
        drawRect(c.accentEdge, topLeft = Offset(frame / 2f, frame / 2f), size = Size(w - frame, h - frame), style = Stroke(frame))
        val len = CornerLength.toPx().coerceAtMost(minOf(w, h) / 2f)
        val stroke = CornerStroke.toPx()
        val half = stroke / 2f
        val corners =
            listOf(
                Offset(half, half) to Offset(1f, 1f),
                Offset(w - half, half) to Offset(-1f, 1f),
                Offset(half, h - half) to Offset(1f, -1f),
                Offset(w - half, h - half) to Offset(-1f, -1f),
            )
        corners.forEach { (corner, dir) ->
            drawLine(c.accent, corner, Offset(corner.x + dir.x * len, corner.y), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(c.accent, corner, Offset(corner.x, corner.y + dir.y * len), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }

private const val THIRDS = 3f

private val FilterPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

/** Cover-fit scale: the smallest factor that makes [bitmap] fully cover a [boxW]x[boxH] frame. */
private fun coverScale(bitmap: Bitmap, boxW: Float, boxH: Float): Float =
    max(boxW / bitmap.width, boxH / bitmap.height)

/**
 * Maps a source pixel onto a [boxW]x[boxH] frame, applying cover-fit, zoom and pan. [pan] is a
 * fraction of the frame side (x of width, y of height), so the same value works for both the preview
 * and the output bitmap.
 */
private fun previewMatrix(bitmap: Bitmap, boxW: Float, boxH: Float, zoom: Float, pan: Offset): Matrix {
    val eff = coverScale(bitmap, boxW, boxH) * zoom
    return Matrix().apply {
        postScale(eff, eff)
        postTranslate(
            -bitmap.width / 2f * eff + boxW / 2f + pan.x * boxW,
            -bitmap.height / 2f * eff + boxH / 2f + pan.y * boxH,
        )
    }
}

/** Keeps the framed image fully covering the frame so no empty edges can be cropped in. */
private fun clampPan(pan: Offset, bitmap: Bitmap, boxW: Float, boxH: Float, zoom: Float): Offset {
    val eff = coverScale(bitmap, boxW, boxH) * zoom
    val maxX = max(0f, (bitmap.width * eff / boxW - 1f) / 2f)
    val maxY = max(0f, (bitmap.height * eff / boxH - 1f) / 2f)
    return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
}

/**
 * Renders the framed region to an output bitmap of the given [aspectRatio] (longest side capped at
 * [OUTPUT_LONGEST]), matching the preview exactly.
 */
private fun cropBitmap(bitmap: Bitmap, aspectRatio: Float, zoom: Float, pan: Offset): Bitmap {
    val outW = if (aspectRatio >= 1f) OUTPUT_LONGEST else (OUTPUT_LONGEST * aspectRatio).roundToInt()
    val outH = if (aspectRatio >= 1f) (OUTPUT_LONGEST / aspectRatio).roundToInt() else OUTPUT_LONGEST
    val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val matrix = previewMatrix(bitmap, outW.toFloat(), outH.toFloat(), zoom, pan)
    android.graphics.Canvas(result).drawBitmap(bitmap, matrix, FilterPaint)
    return result
}

/** Decodes [uri] down-sampled to [LOAD_MAX_DIM] and rotated to its upright EXIF orientation. */
private suspend fun loadOrientedBitmap(context: Context, uri: Uri): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            val longest = max(bounds.outWidth, bounds.outHeight)
            while (longest / sample > LOAD_MAX_DIM) sample *= 2

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded =
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOptions)
                } ?: return@runCatching null

            val orientation =
                context.contentResolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            applyOrientation(decoded, orientation)
        }.getOrNull()
    }

/** Rotates/flips [bitmap] so it displays upright per its EXIF [orientation]. */
private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
