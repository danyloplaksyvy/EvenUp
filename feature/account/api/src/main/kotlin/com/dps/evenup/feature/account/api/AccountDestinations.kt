package com.dps.evenup.feature.account.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object AccountRootDestination : NavKey

@Serializable
data class AuthenticationDestination(
    val reason: String = "Sign in to save and share your expense.",
) : NavKey

@Serializable
data class EmailLinkCompletionDestination(
    val link: String,
) : NavKey

@Serializable
data object ProfileDestination : NavKey

@Serializable
data object EditProfileDestination : NavKey

@Serializable
data object SignInMethodsDestination : NavKey

@Serializable
data object ReauthenticationDestination : NavKey

@Serializable
data object DeleteAccountDestination : NavKey
