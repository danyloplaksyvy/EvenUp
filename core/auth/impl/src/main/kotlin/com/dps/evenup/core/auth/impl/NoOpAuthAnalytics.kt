package com.dps.evenup.core.auth.impl

import com.dps.evenup.core.auth.api.AuthAnalytics
import com.dps.evenup.core.auth.api.AuthAnalyticsEvent

class NoOpAuthAnalytics : AuthAnalytics {
    override fun record(event: AuthAnalyticsEvent) = Unit
}
