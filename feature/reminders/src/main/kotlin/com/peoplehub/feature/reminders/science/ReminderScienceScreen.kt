package com.peoplehub.feature.reminders.science

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.peoplehub.core.ui.components.CapsLabel
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.GoldDivider
import com.peoplehub.feature.reminders.R

/**
 * The "why an irregular cadence" explainer. Grounds the design decision in the
 * relationship-maintenance literature (Gottman, Aron's self-expansion model, Gable, Hall/Dunbar)
 * without inventing studies or figures, and closes with an informational disclaimer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderScienceScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.science_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                navigationIcon = {
                    com.peoplehub.core.ui.components.TooltipIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        description = stringResource(R.string.action_back),
                        onClick = onBack,
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
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScienceSection(
                title = stringResource(R.string.science_intro_title),
                body = stringResource(R.string.science_intro_body),
            )
            ScienceSection(
                title = stringResource(R.string.science_why_title),
                body = stringResource(R.string.science_why_body),
            )
            ScienceSection(
                title = stringResource(R.string.science_how_title),
                body = stringResource(R.string.science_how_body),
            )
            ScienceSection(
                title = stringResource(R.string.science_refs_title),
                body = stringResource(R.string.science_refs_body),
            )
            Text(
                text = stringResource(R.string.science_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun ScienceSection(title: String, body: String) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            CapsLabel(text = title)
            GoldDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
