package com.peoplehub.core.domain.model

import java.time.LocalDateTime

/**
 * A significant personal event with an elapsed/remaining day counter.
 *
 * Events may sit in the past or the future; the UI renders "X days ago" or "in X days" relative to
 * the current day. An event can optionally be [pinnedToWidget] to surface it on the home-screen
 * widget, and optionally linked to a [personId].
 *
 * @property category free-form tag (e.g. "Gala", "Anniversary") rendered as a coloured chip.
 * @property backgroundImagePath optional absolute path to the cropped image (in internal storage)
 * rendered behind the event card; `null` falls back to the plain glass panel.
 * @property backgroundSourcePath absolute path to the uncropped original the crop was taken from,
 * kept so the framing can be adjusted later without re-picking the photo. `null` for backgrounds
 * saved before this was tracked, and for backgrounds reused from another event.
 * @property backgroundCrop the pan/zoom framing that produced [backgroundImagePath] from
 * [backgroundSourcePath], so reopening the crop editor restores what the user last chose.
 */
data class PersonEvent(
    val id: Long = 0L,
    val title: String,
    val dateTime: LocalDateTime,
    val description: String? = null,
    val category: String? = null,
    val backgroundImagePath: String? = null,
    val backgroundSourcePath: String? = null,
    val backgroundCrop: CropTransform = CropTransform.Default,
    val personId: Long? = null,
    val pinnedToWidget: Boolean = false,
)

/**
 * The pan/zoom framing applied to a source image to produce a cropped one.
 *
 * [zoom] is relative to a cover-fit baseline (`1f` = the image exactly fills the frame), and [panX]
 * / [panY] are offsets expressed as a fraction of the frame's width / height, so the transform is
 * independent of the pixel size it is replayed at.
 */
data class CropTransform(
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
) {
    companion object {
        /** Centred, un-zoomed cover fit. */
        val Default: CropTransform = CropTransform()
    }
}
