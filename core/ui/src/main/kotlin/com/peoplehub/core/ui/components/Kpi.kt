package com.peoplehub.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.peoplehub.core.ui.theme.PeopleHubTheme

/**
 * A KPI: a big tabular Geist number over a muted caption ("24 / Connessioni attive").
 *
 * @param valueColor defaults to the primary text; pass the accent-as-text colour for the hero KPI.
 */
@Composable
fun KpiBlock(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    style: TextStyle = PeopleHubTheme.type.kpiMedium,
    valueColor: Color = PeopleHubTheme.colors.text,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = value, style = style, color = valueColor, maxLines = 1)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = PeopleHubTheme.colors.text2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A row of [KpiBlock]s separated by hairlines (the "24 | 3" and "24 | 142 | 6" strips). */
@Composable
fun KpiRow(
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    style: TextStyle = PeopleHubTheme.type.kpiMedium,
) {
    val line = PeopleHubTheme.colors.line
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEachIndexed { index, (value, label) ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(48.dp)
                        .background(line)
                        .align(Alignment.CenterVertically),
                )
            }
            KpiBlock(value = value, label = label, style = style, modifier = Modifier.weight(1f))
        }
    }
}

/** One part of a [SegmentBar]: its relative [weight] and [color]. */
@Immutable
data class BarSegment(
    val weight: Float,
    val color: Color,
)

/**
 * A stacked proportion bar (network vitality, check-in thresholds): rounded 10dp segments with a 3dp
 * gap, each filling in from the left in cascade on first composition. Zero-weight segments are
 * skipped. [description] is the accessible summary (the bar itself is an image).
 */
@Composable
fun SegmentBar(
    segments: List<BarSegment>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
) {
    val motion = PeopleHubTheme.motion
    val visible = segments.filter { it.weight > 0f }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        visible.forEachIndexed { index, segment ->
            val fill = remember { Animatable(if (motion.reduced) 1f else 0f) }
            LaunchedEffect(Unit) {
                if (!motion.reduced) {
                    fill.animateTo(
                        1f,
                        tween(motion.fillMillis, delayMillis = FILL_DELAY + index * FILL_STAGGER, easing = motion.easing),
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .weight(segment.weight)
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleX = fill.value
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }.background(segment.color, RoundedCornerShape(6.dp)),
            )
        }
    }
}

private const val FILL_DELAY = 350
private const val FILL_STAGGER = 120
