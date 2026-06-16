package com.dps.evenup.core.navigation.impl

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey
import com.dps.evenup.core.navigation.api.EvenUpNavigator

class DefaultEvenUpNavigator(
    startDestination: NavKey,
) : EvenUpNavigator {
    override val backStack: SnapshotStateList<NavKey> = mutableStateListOf(startDestination)

    override fun navigate(destination: NavKey) {
        backStack.add(destination)
    }

    override fun replaceAll(destination: NavKey) {
        backStack.clear()
        backStack.add(destination)
    }

    override fun navigateBack(): Boolean {
        if (backStack.size <= 1) {
            return false
        }
        backStack.removeLastOrNull()
        return true
    }
}
