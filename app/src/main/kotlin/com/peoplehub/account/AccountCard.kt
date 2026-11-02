package com.peoplehub.account

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.peoplehub.R
import com.peoplehub.core.domain.model.Account
import com.peoplehub.core.ui.components.SettingsCard
import com.peoplehub.core.ui.components.VellutoButton
import com.peoplehub.core.ui.components.VellutoButtonStyle
import com.peoplehub.core.ui.icon.VellutoIcons
import com.peoplehub.core.ui.theme.PeopleHubTheme

/**
 * The Vault's account card: which Google account the data belongs to, where it lives, and "Sign out"
 * (which returns to the sign-in screen; nothing is left on the phone apart from photos).
 */
@Composable
internal fun AccountCard(
    account: Account?,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(
        title = stringResource(R.string.account_title),
        icon = VellutoIcons.Cloud,
        description = stringResource(R.string.account_desc),
        modifier = modifier,
    ) {
        if (account != null) {
            Text(
                text = stringResource(R.string.account_signed_in_as, account.email ?: account.displayName ?: account.uid),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = PeopleHubTheme.colors.text,
            )
        }
        VellutoButton(
            text = stringResource(R.string.account_sign_out),
            onClick = onSignOut,
            style = VellutoButtonStyle.Ghost,
            icon = VellutoIcons.LogOut,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
