package com.dps.evenup.core.navigation.api

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

fun interface EvenUpEntryProviderInstaller {
    fun install(scope: EntryProviderScope<NavKey>)
}
