package com.peoplehub.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.peoplehub.R
import com.peoplehub.core.domain.model.InitialSyncAction
import com.peoplehub.core.domain.model.SyncStatus
import com.peoplehub.core.ui.components.SettingsCard
import com.peoplehub.core.ui.components.StatusPill
import com.peoplehub.core.ui.components.TextAction
import com.peoplehub.core.ui.components.VellutoButton
import com.peoplehub.core.ui.components.VellutoButtonStyle
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.theme.PeopleHubTheme

/** Every action of the cloud sync card. */
@Immutable
internal class SyncActions(
    val onSignIn: () -> Unit = {},
    val onUpload: () -> Unit = {},
    val onDownload: () -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onRetryCheck: () -> Unit = {},
    val onSignOut: () -> Unit = {},
)

/**
 * The Vault's cloud sync card. Signed out it offers "Sign in with Google". Signed in it shows the
 * account and then, depending on [SyncStatus.initialAction], the one-off first step (upload to an
 * empty cloud, or download from a cloud that already has data) or, once that is done, the automatic
 * sync state: last sync, changes waiting, last error, and "Sync now". "Sign out" is always last.
 */
@Composable
internal fun SyncCard(
    status: SyncStatus,
    actions: SyncActions,
    modifier: Modifier = Modifier,
) {
    SettingsCard(
        title = stringResource(R.string.vault_sync_title),
        icon = VellutoIcons.Cloud,
        description = stringResource(R.string.vault_sync_desc),
        modifier = modifier,
    ) {
        val account = status.account
        if (account == null) {
            VellutoButton(
                text = stringResource(R.string.vault_sync_sign_in),
                onClick = actions.onSignIn,
                icon = VellutoIcons.Cloud,
                modifier = Modifier.fillMaxWidth(),
            )
            status.lastError?.let { ErrorLine(it) }
            return@SettingsCard
        }

        Text(
            text = stringResource(R.string.vault_sync_signed_in_as, account.email ?: account.displayName ?: account.uid),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = PeopleHubTheme.colors.text,
        )
        when (status.initialAction) {
            InitialSyncAction.CHECKING -> CheckingContent(status, actions.onRetryCheck)
            InitialSyncAction.UPLOAD ->
                FirstStep(
                    hint = stringResource(R.string.vault_sync_upload_hint),
                    button = stringResource(R.string.vault_sync_upload),
                    icon = VellutoIcons.CloudUpload,
                    busy = status.isSyncing,
                    onClick = actions.onUpload,
                )
            InitialSyncAction.DOWNLOAD ->
                FirstStep(
                    hint = stringResource(R.string.vault_sync_download_hint),
                    button = stringResource(R.string.vault_sync_download),
                    icon = VellutoIcons.CloudDownload,
                    busy = status.isSyncing,
                    onClick = actions.onDownload,
                )
            InitialSyncAction.NONE -> ActiveContent(status, actions.onSyncNow)
        }
        VellutoButton(
            text = stringResource(R.string.vault_sync_sign_out),
            onClick = actions.onSignOut,
            style = VellutoButtonStyle.Ghost,
            icon = VellutoIcons.LogOut,
            enabled = !status.isSyncing,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CheckingContent(
    status: SyncStatus,
    onRetry: () -> Unit,
) {
    val c = PeopleHubTheme.colors
    StatusPill(
        label = stringResource(R.string.vault_sync_checking),
        content = c.text2,
        container = c.surface2,
        icon = VellutoIcons.RefreshCw,
        border = c.line,
        maxLines = Int.MAX_VALUE,
    )
    status.lastError?.let {
        ErrorLine(it)
        TextAction(label = stringResource(R.string.vault_sync_retry), onClick = onRetry)
    }
}

/** The one-off first step: why, then the single gold action. */
@Composable
private fun FirstStep(
    hint: String,
    button: String,
    icon: ImageVector,
    busy: Boolean,
    onClick: () -> Unit,
) {
    Text(text = hint, style = MaterialTheme.typography.bodySmall, color = PeopleHubTheme.colors.text2)
    if (busy) RunningPill()
    VellutoButton(
        text = button,
        onClick = onClick,
        icon = icon,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ActiveContent(
    status: SyncStatus,
    onSyncNow: () -> Unit,
) {
    val c = PeopleHubTheme.colors
    if (status.isSyncing) {
        RunningPill()
    } else {
        StatusPill(
            label = stringResource(R.string.vault_sync_active),
            content = c.ok,
            container = c.okSoft,
            icon = VellutoIcons.Check,
            maxLines = Int.MAX_VALUE,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(PeopleHubTheme.spacing.xxs)) {
        Text(
            text =
                status.lastSyncAt
                    ?.let { stringResource(R.string.vault_sync_last, DateUtils.getRelativeTimeSpanString(it.toEpochMilli()).toString()) }
                    ?: stringResource(R.string.vault_sync_never),
            style = MaterialTheme.typography.bodySmall,
            color = c.text2,
        )
        if (status.pendingChanges > 0) {
            Text(
                text = stringResource(R.string.vault_sync_pending, status.pendingChanges),
                style = MaterialTheme.typography.bodySmall,
                color = c.text2,
            )
        }
        status.lastError?.let { ErrorLine(it) }
    }
    VellutoButton(
        text = stringResource(R.string.vault_sync_now),
        onClick = onSyncNow,
        style = VellutoButtonStyle.Neutral,
        icon = VellutoIcons.RefreshCw,
        enabled = !status.isSyncing,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RunningPill() {
    val c = PeopleHubTheme.colors
    StatusPill(
        label = stringResource(R.string.vault_sync_running),
        content = c.text2,
        container = c.surface2,
        icon = VellutoIcons.RefreshCw,
        border = c.line,
    )
}

@Composable
private fun ErrorLine(message: String) {
    Text(
        text = stringResource(R.string.vault_sync_error, message),
        style = MaterialTheme.typography.bodySmall,
        color = PeopleHubTheme.colors.err,
    )
}
