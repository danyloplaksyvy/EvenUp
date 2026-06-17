package com.dps.evenup

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import com.dps.evenup.core.navigation.api.EvenUpEntryProviderInstaller
import com.dps.evenup.core.navigation.api.EvenUpNavigator
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
        setContent {
            EvenUpTheme {
                NavDisplay(
                    backStack = navigator.backStack,
                    modifier = Modifier.fillMaxSize(),
                    onBack = { navigator.navigateBack() },
                    entryProvider = entryProvider {
                        entryProviderInstallers.forEach { installer ->
                            installer.install(this)
                        }
                    },
                )
            }
        }
    }
}
