package org.radarbase.push.integration.fitbit.service.subscription

import java.time.Instant

data class SubscriptionUserData(
    var subscriptionStatus: Boolean,
    val subscriptionID: String,
    var nextRequestTime: Instant
)
