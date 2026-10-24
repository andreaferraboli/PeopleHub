package com.peoplehub.core.ui.modifier

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import com.peoplehub.core.ui.theme.LocalVellutoMotion
import kotlinx.coroutines.launch

/**
 * The Velluto entrance: the element rises 16dp and fades in with the house easing, delayed by
 * `index * stagger` so a column of cards arrives in cascade. Renders directly in its final state when
 * the system asks to remove animations.
 *
 * Use it on the first screenful of content (hero, first cards): items of a lazy list replay it when
 * they re-enter composition, which is fine for a few rows but noisy deep in a long list.
 */
fun Modifier.riseIn(index: Int = 0): Modifier = this then RiseInElement(index)

private data class RiseInElement(
    val index: Int,
) : ModifierNodeElement<RiseInNode>() {
    override fun create() = RiseInNode(index)

    override fun update(node: RiseInNode) {
        node.index = index
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "riseIn"
        properties["index"] = index
    }
}

private class RiseInNode(
    var index: Int,
) : Modifier.Node(),
    LayoutModifierNode,
    CompositionLocalConsumerModifierNode {
    private var progress = Animatable(0f)
    private var offsetPx = 0f

    override fun onAttach() {
        val motion = currentValueOf(LocalVellutoMotion)
        offsetPx = with(currentValueOf(LocalDensity)) { motion.riseOffset.toPx() }
        if (motion.reduced) {
            progress = Animatable(1f)
            return
        }
        val delay = (index * motion.staggerMillis).coerceAtMost(motion.maxStaggerMillis)
        coroutineScope.launch {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = motion.riseMillis, delayMillis = delay, easing = motion.easing),
            )
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val p = progress.value
                alpha = p
                translationY = (1f - p) * offsetPx
            }
        }
    }
}
