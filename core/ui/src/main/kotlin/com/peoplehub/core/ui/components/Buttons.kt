package com.peoplehub.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.peoplehub.core.ui.modifier.ShadowSpec
import com.peoplehub.core.ui.modifier.VellutoElevation
import com.peoplehub.core.ui.modifier.glassSurface
import com.peoplehub.core.ui.modifier.lightEdge
import com.peoplehub.core.ui.modifier.softShadow
import com.peoplehub.core.ui.theme.PeopleHubTheme

/** Height of the full-width primary pill ("Registra un'uscita", "Esporta tutto"). */
private val PrimaryHeight = 54.dp

/** Height of secondary pills and inline buttons (never below the 48dp touch target). */
private val SecondaryHeight = 48.dp

/** Visual style of a [VellutoButton]. */
enum class VellutoButtonStyle {
    /** Solid accent fill, dark content, accent glow: the one main action of a screen or card. */
    Primary,

    /** Translucent accent fill with accent hairline and accent text ("Li ho visti", "Fatto"). */
    Soft,

    /** Neutral filled pill on surface2 ("Cerca aggiornamenti", "Importa backup"). */
    Neutral,

    /** Transparent with a hairline ("Ghost"): low-emphasis secondary action. */
    Ghost,

    /** Transparent with an accent hairline and accent text ("Controlla notifiche adesso"). */
    Outline,

    /** Transparent with an error border and error text: destructive actions. */
    Danger,
}

/**
 * The Velluto pill button. Sentence-case Geist label (no caps), optional leading [icon], 48–54dp
 * tall, fully rounded. Pick the emphasis with [style]; only [VellutoButtonStyle.Primary] glows (and
 * breathes when [breathing] is on and animations are allowed).
 */
@Composable
fun VellutoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: VellutoButtonStyle = VellutoButtonStyle.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    breathing: Boolean = false,
    height: Dp = if (style == VellutoButtonStyle.Primary) PrimaryHeight else SecondaryHeight,
) {
    val c = PeopleHubTheme.colors
    val (container, content, border) =
        when (style) {
            VellutoButtonStyle.Primary -> Triple(c.accent, c.onAccent, null)
            VellutoButtonStyle.Soft -> Triple(c.accentSoft, c.accentOnSoft, BorderStroke(1.dp, c.accentEdge))
            VellutoButtonStyle.Neutral -> Triple(c.surface2, c.text, BorderStroke(1.dp, c.line))
            VellutoButtonStyle.Ghost -> Triple(Color.Transparent, c.text, BorderStroke(1.dp, c.line))
            VellutoButtonStyle.Outline -> Triple(Color.Transparent, c.accentText, BorderStroke(1.dp, c.accentEdge))
            VellutoButtonStyle.Danger -> Triple(Color.Transparent, c.err, BorderStroke(1.5.dp, c.err))
        }
    val glow = style == VellutoButtonStyle.Primary && enabled
    val motion = PeopleHubTheme.motion
    val pulse = rememberPulse(reduced = !breathing || motion.reduced, motion.breathMillis)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (glow) GlowBehind(color = c.glowStrong, spec = VellutoElevation.Glow, pulse = pulse)
        Button(
            onClick = onClick,
            modifier =
                Modifier
                    .heightIn(min = height)
                    .then(if (glow) Modifier.lightEdge(CircleShape, BUTTON_SHINE, Color.Transparent) else Modifier),
            enabled = enabled,
            shape = CircleShape,
            border = if (enabled) border else border?.let { BorderStroke(it.width, c.line) },
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = container,
                    contentColor = content,
                    disabledContainerColor = if (container == Color.Transparent) Color.Transparent else c.surface2,
                    disabledContentColor = c.text2,
                ),
        ) {
            ButtonContent(text = text, icon = icon)
        }
    }
}

/**
 * A soft coloured glow drawn behind its parent's bounds. [pulse] (0..1) modulates its strength in
 * the draw phase only, so a breathing glow never recomposes.
 */
@Composable
private fun BoxScope.GlowBehind(
    color: Color,
    spec: ShadowSpec,
    pulse: State<Float>?,
) {
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = BREATH_MIN + (1f - BREATH_MIN) * (pulse?.value ?: 1f) }
                .softShadow(color, CircleShape, spec),
    )
}

private const val BREATH_MIN = 0.65f

/** The white top shine of the solid accent pill (`inset 0 1px 0 rgba(255,255,255,.5)`). */
private val BUTTON_SHINE = Color.White.copy(alpha = 0.5f)

@Composable
private fun ButtonContent(
    text: String,
    icon: ImageVector?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The primary call to action: solid accent pill with the accent glow. Kept as a named wrapper of
 * [VellutoButton] so existing call sites keep working.
 */
@Composable
fun PrimaryGoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    VellutoButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled, icon = icon)
}

/** A low-emphasis secondary action: transparent pill with a hairline border. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    VellutoButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        style = VellutoButtonStyle.Ghost,
    )
}

/**
 * A 48dp circular glass icon button (header actions: calendar, import, sort) with its tooltip and
 * accessibility label from [description].
 *
 * @param selected renders it as a soft accent button (e.g. an active toggle).
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    tint: Color = if (selected) PeopleHubTheme.colors.accentOnSoft else PeopleHubTheme.colors.text,
) {
    val c = PeopleHubTheme.colors
    WithTooltip(description = description) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier =
                modifier
                    .size(48.dp)
                    .then(
                        if (selected) {
                            Modifier
                                .clip(CircleShape)
                                .lightEdge(CircleShape, Color.Transparent, c.accentEdge)
                        } else {
                            Modifier.glassSurface(c, CircleShape)
                        },
                    ),
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = if (selected) c.accentSoft else Color.Transparent,
                    contentColor = tint,
                    disabledContentColor = c.text2.copy(alpha = DISABLED_ALPHA),
                ),
        ) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
        }
    }
}

private const val DISABLED_ALPHA = 0.5f

/**
 * The accent floating action button: a 60dp accent circle with icon, or — when [text] is given — the
 * extended pill ("Aggiungi persona"). It glows and pops in on first composition.
 */
@Composable
fun AccentFab(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
) {
    val c = PeopleHubTheme.colors
    val motion = PeopleHubTheme.motion
    val pop = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!motion.reduced) pop.animateTo(1f, tween(motion.popMillis, delayMillis = POP_DELAY, easing = motion.easing))
    }
    WithTooltip(description = description) {
        Box(
            modifier =
                modifier.graphicsLayer {
                    val p = pop.value
                    alpha = p
                    scaleX = POP_FROM + (1f - POP_FROM) * p
                    scaleY = POP_FROM + (1f - POP_FROM) * p
                },
            contentAlignment = Alignment.Center,
        ) {
            GlowBehind(color = c.glowStrong, spec = VellutoElevation.Glow, pulse = null)
            Button(
                onClick = onClick,
                shape = CircleShape,
                modifier =
                    Modifier
                        .lightEdge(CircleShape, BUTTON_SHINE, Color.Transparent)
                        .then(if (text == null) Modifier.size(60.dp) else Modifier.heightIn(min = 56.dp)),
                contentPadding =
                    if (text == null) PaddingValues(0.dp) else PaddingValues(start = 18.dp, end = 22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
            ) {
                if (text == null) {
                    Icon(icon, contentDescription = description, modifier = Modifier.size(26.dp))
                } else {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 10.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private const val POP_FROM = 0.7f
private const val POP_DELAY = 300

/**
 * The round accent button that sits in a card's notch ("Vedi chi richiede attenzione", "Fatto"):
 * 64–68dp, breathing glow, optional caption under the icon.
 */
@Composable
fun NotchButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    size: Dp = 64.dp,
    glow: Color = PeopleHubTheme.colors.glowStrong,
) {
    val c = PeopleHubTheme.colors
    val motion = PeopleHubTheme.motion
    val pulse = rememberPulse(motion.reduced, motion.breathMillis)
    WithTooltip(description = description) {
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            GlowBehind(color = glow, spec = ShadowSpec(blur = 36.dp, offsetY = 12.dp), pulse = pulse)
            Button(
                onClick = onClick,
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(size),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        icon,
                        contentDescription = if (caption == null) description else null,
                        modifier = Modifier.size(24.dp),
                    )
                    if (caption != null) {
                        Text(text = caption, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
