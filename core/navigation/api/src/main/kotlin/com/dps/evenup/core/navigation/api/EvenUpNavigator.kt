package com.dps.evenup.core.navigation.api

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey

interface EvenUpNavigator {
    val backStack: SnapshotStateList<NavKey>

    fun navigate(destination: NavKey)

    fun navigateBack(): Boolean
}
