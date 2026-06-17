package com.dps.evenup.core.network.api

data class WorkerApiConfig(
    val baseUrl: String,
) {
    init {
        require(baseUrl.isNotBlank()) { "Worker base URL must not be blank." }
    }
}
