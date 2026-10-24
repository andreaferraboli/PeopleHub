package com.peoplehub.feature.reminders.science

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.DisplayTitle
import com.peoplehub.core.ui.components.FloatingScene
import com.peoplehub.core.ui.components.GlassIconButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.GoldDivider
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.SceneArt
import com.peoplehub.core.ui.components.VellutoBackground
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.riseIn
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.feature.reminders.R

/**
 * The "why an irregular cadence" explainer. Grounds the design decision in the
 * relationship-maintenance literature (Gottman, Aron's self-expansion model, Gable, Hall/Dunbar)
 * without inventing studies or figures, and closes with an informational disclaimer.
 *
 * Laid out as an editorial page: a two-tone serif title with the floating hourglass, then one glass
 * panel per section with a numbered eyebrow, a serif heading and the paragraph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderScienceScreen(onBack: () -> Unit) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    VellutoBackground(secondGlow = true) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                // The serif title lives in the page itself; the bar only carries the way back.
                PeopleHubTopBar(
                    title = "",
                    navigationIcon = {
                        GlassIconButton(
                            icon = VellutoIcons.ArrowLeft,
                            description = stringResource(R.string.action_back),
                            onClick = onBack,
                            modifier = Modifier.padding(start = spacing.s),
                        )
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier =
                    Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = spacing.section),
                verticalArrangement = Arrangement.spacedBy(spacing.ml),
            ) {
                ScienceHero()

                val sections =
                    listOf(
                        R.string.science_intro_title to R.string.science_intro_body,
                        R.string.science_why_title to R.string.science_why_body,
                        R.string.science_how_title to R.string.science_how_body,
                        R.string.science_refs_title to R.string.science_refs_body,
                    )
                sections.forEachIndexed { index, (titleRes, bodyRes) ->
                    ScienceSection(
                        number = index + 1,
                        title = stringResource(titleRes),
                        body = stringResource(bodyRes),
                        modifier = Modifier.padding(horizontal = spacing.gutter).riseIn(index + 1),
                    )
                }

                GoldDivider(
                    diamond = true,
                    modifier = Modifier.padding(horizontal = spacing.section, vertical = spacing.s),
                )

                Row(
                    modifier = Modifier.padding(horizontal = spacing.heroInset),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Icon(
                        VellutoIcons.Info,
                        contentDescription = null,
                        tint = c.text2,
                        modifier = Modifier.padding(top = 2.dp).size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.science_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.text2,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Two-tone serif title with the floating hourglass bleeding out of the right edge. */
@Composable
private fun ScienceHero() {
    val spacing = PeopleHubTheme.spacing
    Box(modifier = Modifier.fillMaxWidth()) {
        FloatingScene(
            art = SceneArt.Hourglass,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 18.dp, y = (-4).dp),
        )
        DisplayTitle(
            text = stringResource(R.string.science_title),
            style = MaterialTheme.typography.displayMedium,
            modifier =
                Modifier
                    .padding(start = spacing.heroInset, end = spacing.heroInset, top = spacing.xxl, bottom = spacing.l)
                    .widthIn(max = 280.dp)
                    .riseIn(0),
        )
    }
}

@Composable
private fun ScienceSection(
    number: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    GlassPanel(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            CapsLabel(text = number.toString().padStart(2, '0'), color = c.accentText)
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = c.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = c.text2,
            )
        }
    }
}
