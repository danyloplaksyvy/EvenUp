package com.dps.evenup

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import com.dps.evenup.core.navigation.api.EvenUpEntryProviderInstaller
import com.dps.evenup.core.navigation.api.EvenUpNavigator
import com.dps.evenup.feature.account.api.EmailLinkCompletionDestination
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var navigator: EvenUpNavigator

    @Inject
    lateinit var entryProviderInstallers: Set<@JvmSuppressWildcards EvenUpEntryProviderInstaller>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),
        )
        handleAuthLink(intent)
        setContent {
            EvenUpTheme {
                NavDisplay(
                    backStack = navigator.backStack,
                    modifier = Modifier.fillMaxSize(),
                    onBack = { navigator.navigateBack() },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider {
                        entryProviderInstallers.forEach { installer ->
                            installer.install(this)
                        }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthLink(intent)
    }

    private fun handleAuthLink(intent: Intent?) {
        val link = intent?.data?.toString() ?: return
        if (intent.data?.path == "/__/auth/links") {
            navigator.navigate(EmailLinkCompletionDestination(link))
        }
    }
}
