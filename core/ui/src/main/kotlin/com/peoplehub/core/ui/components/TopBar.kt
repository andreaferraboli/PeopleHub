package com.peoplehub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.ShadowSpec
import com.peoplehub.core.ui.modifier.lightEdge
import com.peoplehub.core.ui.modifier.softShadow
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes

/** Opacity of the bar once content scrolls beneath it (the background shows through a little). */
private const val SCROLLED_ALPHA = 0.94f

/**
 * The shared PeopleHub top app bar: transparent over the Velluto background, an Instrument Serif
 * title in the primary text colour, and a near-opaque background once content scrolls beneath it.
 *
 * @param centered renders a [CenterAlignedTopAppBar] instead of a leading-aligned [TopAppBar].
 * @param showLogo prepends the [BrandMark] and sets the title in the italic brand serif, as in the
 * home header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleHubTopBar(
    title: String,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    centered: Boolean = false,
    showLogo: Boolean = false,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = PeopleHubTheme.colors
    val colors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = c.bg.copy(alpha = SCROLLED_ALPHA),
            titleContentColor = c.text,
            navigationIconContentColor = c.text,
            actionIconContentColor = c.text,
        )
    val titleContent: @Composable () -> Unit = {
        if (showLogo) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BrandMark()
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontStyle = FontStyle.Italic, fontSize = 23.sp),
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (centered) {
        CenterAlignedTopAppBar(
            title = titleContent,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehavior,
        )
    } else {
        TopAppBar(
            title = titleContent,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehavior,
        )
    }
}

/**
 * The PeopleHub brand mark: a 32dp gold tile (the accent ramp, lit from the top-left) with the
 * sparkle glyph in the on-accent colour.
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    val c = PeopleHubTheme.colors
    Box(
        modifier =
            modifier
                .size(32.dp)
                .softShadow(c.glowSoft, VellutoShapes.Mark, ShadowSpec(16.dp, 6.dp))
                .background(Brush.linearGradient(0f to c.g1, 0.55f to c.g3, 1f to c.g4), VellutoShapes.Mark)
                .lightEdge(VellutoShapes.Mark, Color.White.copy(alpha = 0.6f), Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(VellutoIcons.Sparkle, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(17.dp))
    }
}
