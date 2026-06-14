package com.peoplehub.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peoplehub.R

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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.update_available_message, versionName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (notes.isNotBlank()) {
                    Text(text = notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.update_install)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) } },
    )
}

@Composable
private fun DownloadingDialog() {
    AlertDialog(
        onDismissRequest = { },
        confirmButton = { },
        title = { Text(stringResource(R.string.update_downloading)) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.update_downloading_hint), style = MaterialTheme.typography.bodyMedium)
            }
        },
    )
}
