package com.peoplehub.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.peoplehub.R
import com.peoplehub.core.ui.components.GlassIconButton
import com.peoplehub.core.ui.components.GlassPanel
import com.peoplehub.core.ui.components.Hairline
import com.peoplehub.core.ui.components.IconTile
import com.peoplehub.core.ui.components.PeopleHubTopBar
import com.peoplehub.core.ui.components.VellutoBackground
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.modifier.VellutoElevation
import com.peoplehub.core.ui.modifier.riseIn
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes
import com.peoplehub.locale.AppLocale
import com.peoplehub.locale.findActivity

/**
 * Language picker: "System default", Italian or English. Selecting a language persists it and
 * recreates the activity so the new language is applied immediately across the UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(AppLocale.currentTag(context)) }
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing

    VellutoBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                PeopleHubTopBar(
                    title = stringResource(R.string.vault_language_title),
                    centered = true,
                    navigationIcon = {
                        GlassIconButton(
                            icon = VellutoIcons.ArrowLeft,
                            description = stringResource(R.string.action_back),
                            onClick = onBack,
                            modifier = Modifier.padding(start = spacing.xxs),
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
                        .padding(horizontal = spacing.gutter, vertical = spacing.l),
                verticalArrangement = Arrangement.spacedBy(spacing.ml),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = spacing.xxs).riseIn(0),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.m),
                ) {
                    IconTile(VellutoIcons.Globe)
                    Text(
                        text = stringResource(R.string.vault_language_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.text2,
                        modifier = Modifier.weight(1f),
                    )
                }
                GlassPanel(
                    modifier = Modifier.fillMaxWidth().riseIn(1),
                    shape = VellutoShapes.CardLarge,
                    shadow = VellutoElevation.Raised,
                ) {
                    Column(modifier = Modifier.padding(vertical = spacing.xs).selectableGroup()) {
                        AppLocale.SUPPORTED_TAGS.forEachIndexed { index, tag ->
                            if (index > 0) Hairline(modifier = Modifier.padding(horizontal = 18.dp))
                            LanguageRow(
                                label = languageLabel(tag),
                                selected = tag == selected,
                                onSelect = {
                                    if (tag != selected) {
                                        selected = tag
                                        AppLocale.setTag(context, tag)
                                        context.findActivity()?.recreate()
                                    }
                                },
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.vault_language_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.text2,
                    modifier = Modifier.padding(horizontal = spacing.xxs).riseIn(2),
                )
            }
        }
    }
}

/** A 56dp selectable row: the language name, and the accent check on the selected one. */
@Composable
private fun LanguageRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    val c = PeopleHubTheme.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PeopleHubTheme.spacing.m),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = c.text,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = VellutoIcons.Check,
                contentDescription = null,
                tint = c.accentTextSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun languageLabel(tag: String): String =
    when (tag) {
        AppLocale.SYSTEM -> stringResource(R.string.language_system)
        "it" -> stringResource(R.string.language_italian)
        else -> stringResource(R.string.language_english)
    }
