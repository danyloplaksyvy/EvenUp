package com.dps.evenup.core.network.api

import kotlinx.coroutines.flow.StateFlow

interface NetworkStatus {
    val isOnline: StateFlow<Boolean>
}
