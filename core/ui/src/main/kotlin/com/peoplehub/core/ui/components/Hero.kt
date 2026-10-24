package com.peoplehub.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.peoplehub.core.ui.modifier.riseIn
import com.peoplehub.core.ui.theme.PeopleHubTheme

/**
 * Builds the Velluto two-tone title: [text] in the serif, with the [accent] part in italic accent.
 * When [accent] is `null` (or not found) the last word is the accent ("La *Cerchia*"); a one-word
 * title is accented from its middle ("Crono*logia*", "Pro*memoria*"), as in the mockups.
 */
fun accentTitle(
    text: String,
    accentColor: Color,
    accent: String? = null,
): AnnotatedString {
    val start =
        when {
            accent != null && text.indexOf(accent) >= 0 -> text.indexOf(accent)
            text.trim().contains(' ') -> text.trimEnd().lastIndexOf(' ') + 1
            else -> text.length / 2
        }
    val end = if (accent != null && text.indexOf(accent) >= 0) start + accent.length else text.length
    return buildAnnotatedString {
        append(text.substring(0, start))
        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = accentColor)) { append(text.substring(start, end)) }
        append(text.substring(end))
    }
}

/**
 * The hero title of a Velluto screen ("La *Cerchia*"): Instrument Serif at display size, the accent
 * part in italic accent. See [accentTitle] for how the accent part is chosen.
 */
@Composable
fun DisplayTitle(
    text: String,
    modifier: Modifier = Modifier,
    accent: String? = null,
    style: TextStyle = MaterialTheme.typography.displayLarge,
) {
    val c = PeopleHubTheme.colors
    val annotated = remember(text, accent, c.accentText) { accentTitle(text, c.accentText, accent) }
    Text(
        text = annotated,
        style = style,
        color = c.text,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * The top of every Velluto tab: an eyebrow row (caps label + optional glass actions), the two-tone
 * display title, an optional supporting line, optional extra content (a primary button), and the
 * floating 3D object bleeding out of the top-right corner. Rises in on first composition.
 *
 * @param scene the 3D object, or `null` for none.
 * @param sceneOffsetX / sceneOffsetY where the object sits relative to the top-right corner (it
 * deliberately bleeds out of the screen edge).
 */
@Composable
fun ScreenHero(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    titleAccent: String? = null,
    subtitle: String? = null,
    scene: SceneArt? = null,
    sceneOffsetX: Dp = 24.dp,
    sceneOffsetY: Dp = 56.dp,
    titleTopPadding: Dp = 40.dp,
    subtitleMaxWidth: Dp = 270.dp,
    subtitleStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    Box(modifier = modifier.fillMaxWidth()) {
        if (scene != null) {
            FloatingScene(
                art = scene,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = sceneOffsetX, y = sceneOffsetY),
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(start = spacing.heroInset, end = spacing.gutter, top = spacing.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    if (eyebrow != null) CapsLabel(text = eyebrow, color = c.text2)
                }
                actions()
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = spacing.heroInset, end = spacing.heroInset, top = titleTopPadding)
                        .riseIn(0),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DisplayTitle(text = title, accent = titleAccent)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = subtitleStyle,
                        color = c.text2,
                        modifier = Modifier.widthIn(max = subtitleMaxWidth),
                    )
                }
                content()
            }
        }
    }
}
