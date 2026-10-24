package com.peoplehub.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.R
import com.peoplehub.core.ui.components.VellutoButton
import com.peoplehub.core.ui.components.VellutoButtonStyle
import com.peoplehub.core.ui.modifier.lightEdge
import com.peoplehub.core.ui.theme.PeopleHubTheme
import com.peoplehub.core.ui.theme.VellutoShapes

/**
 * Silent automatic update check, mounted once at the app root. It only surfaces UI when a newer
 * release exists (or while downloading); failures and "up to date" stay invisible.
 */
@Composable
fun UpdatePrompt(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.check(silent = true) }

    when (val current = state) {
        is UpdateUiState.Available ->
            UpdateAvailableDialog(
                versionName = current.update.versionName,
                notes = current.update.notes,
                onConfirm = { viewModel.downloadAndInstall(current.update) },
                onDismiss = viewModel::dismiss,
            )
        UpdateUiState.Downloading -> DownloadingDialog()
        else -> Unit
    }
}

@Composable
private fun UpdateAvailableDialog(
    versionName: String,
    notes: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = PeopleHubTheme.colors
    val spacing = PeopleHubTheme.spacing
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.m),
            ) {
                Text(
                    text = stringResource(R.string.update_available_message, versionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.text,
                )
                if (notes.isNotBlank()) {
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.text2,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(c.surface2, VellutoShapes.Inner)
                                .lightEdge(VellutoShapes.Inner, Color.Transparent, c.line)
                                .padding(spacing.ml),
                    )
                }
            }
        },
        confirmButton = {
            VellutoButton(
                text = stringResource(R.string.update_install),
                onClick = onConfirm,
                height = spacing.touchTarget,
            )
        },
        dismissButton = {
            VellutoButton(
                text = stringResource(R.string.update_later),
                onClick = onDismiss,
                style = VellutoButtonStyle.Ghost,
            )
        },
    )
}

@Composable
private fun DownloadingDialog() {
    val c = PeopleHubTheme.colors
    AlertDialog(
        onDismissRequest = { },
        confirmButton = { },
        title = { Text(stringResource(R.string.update_downloading)) },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PeopleHubTheme.spacing.l),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = c.accent,
                    trackColor = c.track,
                    strokeWidth = 3.dp,
                    strokeCap = StrokeCap.Round,
                )
                Text(
                    text = stringResource(R.string.update_downloading_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.text2,
                )
            }
        },
    )
}
