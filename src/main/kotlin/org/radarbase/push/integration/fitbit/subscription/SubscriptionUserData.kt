package org.radarbase.push.integration.fitbit.subscription

import java.time.Instant

data class SubscriptionUserData(
    var subscriptionStatus: Boolean,
    val subscriptionID: String,
    var nextRequestTime: Instant
)
