package com.dps.evenup.feature.account.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.dps.evenup.core.designsystem.api.EvenUpPrimaryButton
import com.dps.evenup.core.designsystem.api.EvenUpSecondaryButton
import com.dps.evenup.core.designsystem.api.EvenUpTextButton
import com.dps.evenup.core.designsystem.api.EvenUpTextField
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import com.dps.evenup.domain.account.api.AccountSessionState
import com.dps.evenup.core.auth.api.AuthProviderType

@Composable
internal fun AccountRootScreen(
    state: AccountUiState,
    onContinueLocal: () -> Unit,
    onSignIn: () -> Unit,
    onProfile: () -> Unit,
    onRetry: () -> Unit,
    onCancelDeletion: () -> Unit,
) {
    AccountPage(
        title = stringResource(R.string.account_welcome_title),
        body = stringResource(R.string.account_welcome_body),
        state = state,
    ) {
        when (state.session) {
            AccountSessionState.AuthenticatedBootstrapping,
            AccountSessionState.Authenticating,
            -> LoadingRow()

            is AccountSessionState.AuthenticatedBootstrapFailed -> {
                EvenUpPrimaryButton(stringResource(R.string.account_retry), onRetry)
                EvenUpSecondaryButton(stringResource(R.string.account_continue_local), onContinueLocal)
            }

            is AccountSessionState.AuthenticatedActive,
            is AccountSessionState.AuthenticatedOffline,
            -> {
                state.profile?.let { profile ->
                    Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
                    Text("@${profile.username.value}", color = EvenUpTheme.colors.textSecondary)
                }
                EvenUpPrimaryButton(stringResource(R.string.account_continue_local), onContinueLocal)
                EvenUpSecondaryButton(stringResource(R.string.account_profile), onProfile)
            }

            is AccountSessionState.DeletionPending -> {
                Text(stringResource(R.string.account_deletion_pending))
                EvenUpPrimaryButton(stringResource(R.string.account_cancel_deletion), onCancelDeletion)
                EvenUpSecondaryButton(stringResource(R.string.account_continue_local), onContinueLocal)
            }

            else -> {
                EvenUpPrimaryButton(stringResource(R.string.account_continue_local), onContinueLocal)
                EvenUpSecondaryButton(stringResource(R.string.account_sign_in), onSignIn)
            }
        }
    }
}

@Composable
internal fun AuthenticationScreen(
    state: AccountUiState,
    reason: String,
    onEmailChanged: (String) -> Unit,
    onGoogle: () -> Unit,
    onSendEmail: () -> Unit,
    onCancel: () -> Unit,
) {
    AccountPage(title = stringResource(R.string.account_authentication_title), body = reason, state = state) {
        EvenUpPrimaryButton(
            text = stringResource(R.string.account_google),
            onClick = onGoogle,
            enabled = !state.isBusy,
        )
        HorizontalDivider()
        EvenUpTextField(
            value = state.email,
            onValueChange = onEmailChanged,
            label = stringResource(R.string.account_email),
            enabled = !state.isBusy && !state.emailSent,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Email,
            ),
        )
        if (state.emailSent) {
            Text(
                text = "${stringResource(R.string.account_email_sent)} ${maskEmail(state.email)}",
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            EvenUpSecondaryButton(
                text = if (state.emailResendCooldownSeconds > 0) {
                    stringResource(R.string.account_resend_countdown, state.emailResendCooldownSeconds)
                } else {
                    stringResource(R.string.account_resend_link)
                },
                onClick = onSendEmail,
                enabled = !state.isBusy && state.emailResendCooldownSeconds == 0,
            )
        } else {
            EvenUpSecondaryButton(
                text = stringResource(R.string.account_send_link),
                onClick = onSendEmail,
                enabled = !state.isBusy && state.email.isNotBlank(),
            )
        }
        EvenUpTextButton(stringResource(R.string.account_cancel), onCancel)
    }
}

private fun maskEmail(email: String): String {
    val separator = email.indexOf('@')
    if (separator <= 0) return email
    val local = email.substring(0, separator)
    val visible = local.take(1)
    return "$visible${"•".repeat((local.length - 1).coerceAtLeast(2))}${email.substring(separator)}"
}

@Composable
internal fun EmailLinkCompletionScreen(
    state: AccountUiState,
    onEmailChanged: (String) -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
) {
    AccountPage(
        title = stringResource(R.string.account_finish_sign_in_title),
        body = stringResource(R.string.account_finish_sign_in_body),
        state = state,
    ) {
        EvenUpTextField(
            value = state.email,
            onValueChange = onEmailChanged,
            label = stringResource(R.string.account_email),
            enabled = !state.isBusy,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Email,
            ),
        )
        EvenUpPrimaryButton(
            text = stringResource(R.string.account_complete_link),
            onClick = onComplete,
            enabled = !state.isBusy && state.email.isNotBlank(),
        )
        EvenUpTextButton(stringResource(R.string.account_cancel), onCancel)
    }
}

@Composable
internal fun ProfileScreen(
    state: AccountUiState,
    onEdit: () -> Unit,
    onMethods: () -> Unit,
    onDelete: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
) {
    AccountPage(title = stringResource(R.string.account_profile), body = null, state = state) {
        val profile = state.profile
        if (profile == null) {
            LoadingRow()
        } else {
            profile.avatarUrl?.let { avatarUrl ->
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = stringResource(
                        R.string.account_profile_photo_description,
                        profile.displayName,
                    ),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(EvenUpTheme.spacing.space32 * 2)
                        .clip(CircleShape),
                )
            }
            ProfileLine(stringResource(R.string.account_display_name), profile.displayName)
            ProfileLine(stringResource(R.string.account_username), "@${profile.username.value}")
            ProfileLine(stringResource(R.string.account_currency), profile.defaultCurrency)
            ProfileLine(stringResource(R.string.account_locale), profile.locale)
            if (state.session is AccountSessionState.AuthenticatedOffline) {
                Text(stringResource(R.string.account_offline), color = EvenUpTheme.colors.textSecondary)
            }
            EvenUpPrimaryButton(stringResource(R.string.account_edit_profile), onEdit)
            EvenUpSecondaryButton(stringResource(R.string.account_methods), onMethods)
            EvenUpSecondaryButton(stringResource(R.string.account_sign_out), onSignOut)
            EvenUpTextButton(stringResource(R.string.account_delete), onDelete)
        }
        EvenUpTextButton(stringResource(R.string.account_back), onBack)
    }
}

@Composable
internal fun EditProfileScreen(
    state: AccountUiState,
    onDisplayName: (String) -> Unit,
    onUsername: (String) -> Unit,
    onCurrency: (String) -> Unit,
    onLocale: (String) -> Unit,
    onAvatar: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    AccountPage(title = stringResource(R.string.account_edit_profile), body = null, state = state) {
        state.profile?.avatarUrl?.let { avatarUrl ->
            AsyncImage(
                model = avatarUrl,
                contentDescription = stringResource(R.string.account_current_profile_photo),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(EvenUpTheme.spacing.space32 * 2)
                    .clip(CircleShape),
            )
        }
        EvenUpSecondaryButton(
            stringResource(R.string.account_choose_profile_photo),
            onAvatar,
            enabled = !state.isBusy,
        )
        EvenUpTextField(state.displayName, onDisplayName, stringResource(R.string.account_display_name))
        EvenUpTextField(
            state.username,
            onUsername,
            stringResource(R.string.account_username),
            supportingText = stringResource(R.string.account_username_support),
        )
        EvenUpTextField(state.currency, onCurrency, stringResource(R.string.account_currency))
        EvenUpTextField(state.locale, onLocale, stringResource(R.string.account_locale))
        EvenUpPrimaryButton(
            stringResource(R.string.account_save),
            onSave,
            enabled = !state.isBusy && state.profile != null,
        )
        EvenUpTextButton(stringResource(R.string.account_cancel), onCancel)
    }
}

@Composable
internal fun SignInMethodsScreen(
    state: AccountUiState,
    onLinkGoogle: () -> Unit,
    onEmailChanged: (String) -> Unit,
    onLinkEmail: () -> Unit,
    onUnlink: (AuthProviderType) -> Unit,
    onBack: () -> Unit,
) {
    AccountPage(
        title = stringResource(R.string.account_methods),
        body = stringResource(R.string.account_methods_body),
        state = state,
    ) {
        val googleLinked = AuthProviderType.Google in state.linkedProviders
        val emailLinked = AuthProviderType.EmailLink in state.linkedProviders
        ProfileLine(
            stringResource(R.string.account_google_name),
            if (googleLinked) {
                stringResource(R.string.account_connected)
            } else {
                stringResource(R.string.account_not_connected)
            },
        )
        if (googleLinked) {
            EvenUpTextButton(
                stringResource(R.string.account_disconnect_google),
                { onUnlink(AuthProviderType.Google) },
            )
        } else {
            EvenUpSecondaryButton(
                stringResource(R.string.account_connect_google),
                onLinkGoogle,
                enabled = !state.isBusy,
            )
        }
        ProfileLine(
            stringResource(R.string.account_email_link_name),
            if (emailLinked) {
                stringResource(R.string.account_connected)
            } else {
                stringResource(R.string.account_not_connected)
            },
        )
        if (emailLinked) {
            EvenUpTextButton(
                stringResource(R.string.account_disconnect_email),
                { onUnlink(AuthProviderType.EmailLink) },
            )
        } else {
            EvenUpTextField(
                value = state.email,
                onValueChange = onEmailChanged,
                label = stringResource(R.string.account_email),
                enabled = !state.isBusy,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                ),
            )
            EvenUpSecondaryButton(
                text = if (state.emailSent) {
                    stringResource(R.string.account_link_sent, maskEmail(state.email))
                } else {
                    stringResource(R.string.account_connect_email)
                },
                onClick = onLinkEmail,
                enabled = !state.isBusy && state.email.isNotBlank() && state.emailResendCooldownSeconds == 0,
            )
        }
        Text(
            stringResource(R.string.account_provider_requirement),
            color = EvenUpTheme.colors.textSecondary,
        )
        EvenUpTextButton(stringResource(R.string.account_back), onBack)
    }
}

@Composable
internal fun DeleteAccountScreen(
    state: AccountUiState,
    onConfirmation: (String) -> Unit,
    onReauthenticate: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    AccountPage(
        title = stringResource(R.string.account_delete),
        body = stringResource(R.string.account_delete_body),
        state = state,
    ) {
        EvenUpTextField(
            state.deletionConfirmation,
            onConfirmation,
            stringResource(R.string.account_delete_confirmation),
        )
        EvenUpPrimaryButton(
            stringResource(R.string.account_delete_action),
            onDelete,
            enabled = !state.isBusy && state.deletionConfirmation == "DELETE",
        )
        EvenUpSecondaryButton(
            stringResource(R.string.account_reauthenticate),
            onReauthenticate,
            enabled = !state.isBusy,
        )
        EvenUpTextButton(stringResource(R.string.account_cancel), onCancel)
    }
}

@Composable
private fun AccountPage(
    title: String,
    body: String?,
    state: AccountUiState,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = EvenUpTheme.spacing.space24,
                vertical = EvenUpTheme.spacing.space40,
            ),
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space16),
    ) {
        Spacer(Modifier.height(EvenUpTheme.spacing.space24))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        body?.let { Text(it, color = EvenUpTheme.colors.textSecondary) }
        state.error?.let {
            Text(
                text = it,
                color = EvenUpTheme.colors.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
        content()
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.account_loading))
    }
}

@Composable
private fun ProfileLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = EvenUpTheme.colors.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
