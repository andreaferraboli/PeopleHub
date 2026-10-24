package com.peoplehub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.peoplehub.core.ui.R
import com.peoplehub.core.ui.modifier.ShadowSpec
import com.peoplehub.core.ui.modifier.softShadow
import com.peoplehub.core.ui.theme.PeopleHubFonts
import com.peoplehub.core.ui.theme.PeopleHubTheme
import java.io.File

/**
 * The warm two-stop monogram gradients of the mockups (light-top-left to deep-bottom-right). A person
 * always gets the same one, picked from their initials.
 */
private val MonogramGradients: List<Pair<Color, Color>> =
    listOf(
        Color(0xFFA0623A) to Color(0xFF3B200F),
        Color(0xFF5A6480) to Color(0xFF1C2030),
        Color(0xFF6E6A3A) to Color(0xFF24220F),
        Color(0xFF8A5A44) to Color(0xFF2E1A12),
        Color(0xFF5E6B3A) to Color(0xFF1F2612),
        Color(0xFF9A3F5C) to Color(0xFF3A1424),
        Color(0xFF7A5C2E) to Color(0xFF2A1D0E),
        Color(0xFFB0563A) to Color(0xFF4A1C14),
        Color(0xFF7E4B6A) to Color(0xFF2B1626),
        Color(0xFF6A4A7A) to Color(0xFF22142C),
        Color(0xFF8C4A2A) to Color(0xFF2E160C),
        Color(0xFF8C6A3E) to Color(0xFF2E2212),
    )

/** Monogram text colour: warm white, readable on every gradient above. */
private val MonogramText = Color(0xFFFFF6EE)

/** The monogram gradient for [initials] (stable across launches). */
fun monogramBrush(initials: String): Brush {
    val (from, to) = MonogramGradients[Math.floorMod(initials.hashCode(), MonogramGradients.size)]
    return Brush.linearGradient(listOf(from, to))
}

/**
 * A person's avatar: their photo (via Coil, from internal storage) or their [initials] on a warm
 * gradient. With [ringColor] it gets the Velluto status ring: a 3dp coloured ring separated from the
 * avatar by a 2dp gap, optionally glowing ([ringGlow]).
 *
 * @param shape circular by default; pass a rounded square where a "portrait" reads better.
 */
@Composable
fun PersonAvatar(
    initials: String,
    photoPath: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    shape: RoundedCornerShape = CircleShape,
    ringColor: Color? = null,
    ringGlow: Color = Color.Transparent,
) {
    val colors = PeopleHubTheme.colors
    val ringWidth = if (ringColor != null) RING_WIDTH else 0.dp
    Box(
        modifier =
            modifier
                .size(size)
                .then(
                    if (ringColor != null) {
                        Modifier
                            .softShadow(ringGlow, shape, ShadowSpec(18.dp))
                            .background(ringColor, shape)
                            .padding(ringWidth)
                            .border(RING_GAP, colors.ringGap, shape)
                    } else {
                        Modifier
                    },
                ).clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        val inner = size - ringWidth * 2
        if (photoPath.isNullOrBlank()) {
            Monogram(initials = initials, size = inner, shape = shape)
        } else {
            AsyncImage(
                model = File(photoPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(inner).clip(shape),
            )
        }
    }
}

private val RING_WIDTH = 3.dp
private val RING_GAP = 2.dp

@Composable
private fun Monogram(
    initials: String,
    size: Dp,
    shape: RoundedCornerShape,
) {
    val brush = remember(initials) { monogramBrush(initials) }
    Box(
        modifier = Modifier.size(size).clip(shape).background(brush),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style =
                TextStyle(
                    fontFamily = PeopleHubFonts.Body,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (size.value * MONOGRAM_SCALE).sp,
                    letterSpacing = 0.03.em,
                ),
            color = MonogramText,
            maxLines = 1,
        )
    }
}

/** Monogram font size relative to the avatar (17sp in a 52dp avatar). */
private const val MONOGRAM_SCALE = 0.33f

/** One face in an [AvatarStack]. */
data class StackedFace(
    val initials: String,
    val photoPath: String?,
)

/**
 * Overlapping round avatars ("MC LM TB +2"): each outlined by the card colour so they read as cut
 * out, with a neutral "+N" bubble when more than [max] people are present.
 */
@Composable
fun AvatarStack(
    faces: List<StackedFace>,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    max: Int = 3,
    outline: Color = PeopleHubTheme.colors.surface,
) {
    val colors = PeopleHubTheme.colors
    val shown = faces.take(max)
    val hidden = faces.size - shown.size
    Layout(
        modifier = modifier,
        content = {
            shown.forEach { face ->
                PersonAvatar(
                    initials = face.initials,
                    photoPath = face.photoPath,
                    size = size,
                    modifier = Modifier.border(2.dp, outline, CircleShape),
                )
            }
            if (hidden > 0) {
                Box(
                    modifier =
                        Modifier
                            .size(size)
                            .border(2.dp, outline, CircleShape)
                            .background(colors.surface2, CircleShape)
                            .border(1.dp, colors.line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.outing_more_people, hidden),
                        style = TextStyle(fontFamily = PeopleHubFonts.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                        color = colors.text,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val step = (size - STACK_OVERLAP).roundToPx()
        val width = if (placeables.isEmpty()) 0 else step * (placeables.size - 1) + placeables.last().width
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { index, placeable -> placeable.place(step * index, 0) }
        }
    }
}

private val STACK_OVERLAP = 9.dp
