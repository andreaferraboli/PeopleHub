package com.peoplehub.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.VellutoElevation
import com.peoplehub.core.ui.modifier.lightEdge
import com.peoplehub.core.ui.modifier.softShadow
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes
import java.io.File

/** The countdown drawn in the bottom-right of an [EventCover] ("TRA / 14 / GIORNI"). */
@Immutable
data class CoverCountdown(
    val number: Int,
    val unit: String,
    val prefix: String? = null,
)

/** The widget pin toggle of an [EventCover]. [label] is the accessible name of the toggle. */
@Immutable
data class CoverPin(
    val pinned: Boolean,
    val label: String,
    val onToggle: () -> Unit,
    /** Text shown next to the pin when pinned ("Nel widget"); icon only when `null`. */
    val pinnedText: String? = null,
    /** Draws the pin state only, not as a toggle (where the screen offers no pin action). */
    val readOnly: Boolean = false,
)

/**
 * An event as a Velluto cover card: the event's background photo (or, without one, an illustrated
 * [CoverScene]) under a dark scrim, a category chip and the widget pin at the top, title / date /
 * people at the bottom-left and the day countdown at the bottom-right. Text is always light: the
 * scrim guarantees its contrast on any picture.
 *
 * @param onClick opens the event; `null` for a cover that is not tappable (the event detail header).
 * @param past archived events are slightly desaturated, as in the mockup's "Archivio".
 * @param highlighted the next upcoming event: accent hairline and soft accent glow.
 * @param drift a very slow zoom/pan of the picture (ambient motion, off when animations are off).
 */
@Composable
fun EventCover(
    title: String,
    dateLine: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    supportingLine: String? = null,
    category: String? = null,
    categoryDot: Color? = null,
    countdown: CoverCountdown? = null,
    imagePath: String? = null,
    scene: CoverScene = coverSceneFor(category, title),
    pin: CoverPin? = null,
    height: Dp = 210.dp,
    past: Boolean = false,
    highlighted: Boolean = false,
    drift: Boolean = false,
) {
    val c = PeopleHubTheme.colors
    val shape = VellutoShapes.Hero
    val motion = PeopleHubTheme.motion
    val driftPulse = rememberPulse(reduced = !drift || motion.reduced, periodMillis = DRIFT_MILLIS)
    val filter = remember(past) { if (past) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(PAST_SATURATION) }) else null }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .softShadow(if (highlighted) c.glowSoft else c.shadow, shape, VellutoElevation.Hero)
                .clip(shape)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
    ) {
        val pictureModifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = driftPulse?.value ?: 0f
                    val s = if (drift) DRIFT_SCALE_MIN + (DRIFT_SCALE_MAX - DRIFT_SCALE_MIN) * p else 1f
                    scaleX = s
                    scaleY = s
                    translationX = -DRIFT_PAN.toPx() * p
                }
        if (imagePath.isNullOrBlank()) {
            Image(
                painter = painterResource(scene.drawable),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = filter,
                modifier = pictureModifier,
            )
        } else {
            AsyncImage(
                model = File(imagePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = filter,
                modifier = pictureModifier,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to SCRIM.copy(alpha = 0.18f),
                        0.34f to SCRIM.copy(alpha = 0f),
                        1f to SCRIM.copy(alpha = 0.85f),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .lightEdge(shape, Color.Transparent, if (highlighted) c.accentEdge else MEDIA_EDGE),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp).align(Alignment.TopStart),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (category != null) CategoryChip(label = category, onMedia = true, dot = categoryDot) else Box(Modifier)
            if (pin != null) PinToggle(pin)
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = dateLine,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                    color = c.onMediaMuted,
                    maxLines = 1,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = c.onMedia,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supportingLine != null) {
                    Text(
                        text = supportingLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.onMediaMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (countdown != null) {
                DayCountDisplay(
                    number = countdown.number,
                    unitLabel = countdown.unit,
                    prefix = countdown.prefix,
                    onMedia = true,
                    numberStyle = if (past) PeopleHubTheme.type.kpiCover else PeopleHubTheme.type.kpiLarge,
                )
            }
        }
    }
}

@Composable
private fun PinToggle(pin: CoverPin) {
    val c = PeopleHubTheme.colors
    val base =
        Modifier
            .heightIn(min = 48.dp)
            .then(
                if (pin.pinned) {
                    Modifier.background(c.accent, CircleShape)
                } else {
                    Modifier.background(MEDIA_CHIP_FILL, CircleShape).lightEdge(CircleShape, Color.Transparent, MEDIA_PIN_EDGE)
                },
            ).clip(CircleShape)
            .then(
                if (pin.readOnly) {
                    Modifier
                } else {
                    Modifier.toggleable(value = pin.pinned, role = Role.Switch, onValueChange = { pin.onToggle() })
                },
            )
    WithTooltip(description = pin.label) {
        if (pin.pinned && pin.pinnedText != null) {
            Row(
                modifier = base.padding(start = 12.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(VellutoIcons.Pin, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(17.dp))
                Text(text = pin.pinnedText, style = MaterialTheme.typography.labelMedium, color = c.onAccent)
            }
        } else {
            Box(modifier = base.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    VellutoIcons.Pin,
                    contentDescription = pin.label,
                    tint = if (pin.pinned) c.onAccent else c.onMedia,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** The dark scrim colour of covers (`rgba(8,6,5,…)`). */
private val SCRIM = Color(0xFF080605)

/** Hairline around covers (`rgba(255,255,255,.08)`). */
private val MEDIA_EDGE = Color(0x14FFFFFF)

/** Hairline around the idle pin over a cover (`rgba(255,255,255,.18)`). */
private val MEDIA_PIN_EDGE = Color(0x2EFFFFFF)

private const val PAST_SATURATION = 0.72f
private const val DRIFT_MILLIS = 16000
private const val DRIFT_SCALE_MIN = 1.04f
private const val DRIFT_SCALE_MAX = 1.08f
private val DRIFT_PAN = 6.dp
