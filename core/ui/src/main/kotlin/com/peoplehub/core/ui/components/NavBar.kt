package com.peoplehub.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.peoplehub.core.ui.modifier.VellutoElevation
import com.peoplehub.core.ui.modifier.lightEdge
import com.peoplehub.core.ui.modifier.softShadow
import com.peoplehub.core.ui.theme.PeopleHubTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/**
 * The [HazeState] of the content scrolling under the floating navigation bar, provided by the app
 * shell. `null` where no navigation bar is drawn. Screens normally never touch it.
 */
val LocalNavHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Bottom clearance a top-level screen must leave below its scrolling content and above its FAB so
 * nothing ends up under the floating navigation bar (navigation bar + its margin + system navigation
 * inset). `0.dp` on screens without the navigation bar. Provided by the app shell.
 */
val LocalNavBarClearance = staticCompositionLocalOf { 0.dp }

/** One destination of the [VellutoNavBar]. The label is never drawn: it is the accessible name. */
@Immutable
data class NavBarItem(
    val label: String,
    val icon: ImageVector,
)

/** Opacity of the navigation glass where the backdrop cannot be blurred (API < 31). */
private const val FALLBACK_GLASS_ALPHA = 0.92f

/** Size of one navigation item (above the 48dp touch target). */
private val ItemSize = 50.dp

/**
 * The Velluto floating navigation bar: a 316dp glass pill, 68dp tall, with one 50dp round item per
 * destination; the selected one is filled with the accent and glows. With [hazeState] the content
 * behind is blurred (Android 12+); otherwise, and below API 31, the glass is a translucent fill.
 */
@Composable
fun VellutoNavBar(
    items: List<NavBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    width: Dp = PeopleHubTheme.spacing.navWidth,
) {
    val c = PeopleHubTheme.colors
    val shape = CircleShape
    Row(
        modifier =
            modifier
                .widthIn(max = width)
                .fillMaxWidth()
                .height(PeopleHubTheme.spacing.navHeight)
                .softShadow(c.shadow, shape, VellutoElevation.Nav)
                .clip(shape)
                .then(
                    if (hazeState != null) {
                        Modifier.hazeChild(
                            state = hazeState,
                            style =
                                HazeStyle(
                                    backgroundColor = c.bg,
                                    tints = listOf(HazeTint(c.navGlass)),
                                    blurRadius = 22.dp,
                                    noiseFactor = 0f,
                                    // Below Android 12 there is no blur: a denser glass keeps the icons legible.
                                    fallbackTint = HazeTint(c.navGlass.copy(alpha = FALLBACK_GLASS_ALPHA)),
                                ),
                        )
                    } else {
                        Modifier.background(c.navGlass, shape)
                    },
                ).lightEdge(shape, highlight = c.hi, border = c.line)
                .selectableGroup()
                .padding(horizontal = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            NavItem(item = item, selected = index == selectedIndex, onClick = { onSelect(index) })
        }
    }
}

@Composable
private fun NavItem(
    item: NavBarItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = PeopleHubTheme.colors
    val spec = tween<Color>(PeopleHubTheme.motion.quickMillis)
    val background by animateColorAsState(if (selected) c.accent else Color.Transparent, spec, label = "navBg")
    val content by animateColorAsState(if (selected) c.onAccent else c.text2, spec, label = "navFg")
    WithTooltip(description = item.label) {
        Box(
            modifier =
                Modifier
                    .size(ItemSize)
                    .then(if (selected) Modifier.softShadow(c.glowStrong, CircleShape, VellutoElevation.GlowSmall) else Modifier)
                    .background(background, CircleShape)
                    .clip(CircleShape)
                    .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                    .semantics { contentDescription = item.label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        }
    }
}
