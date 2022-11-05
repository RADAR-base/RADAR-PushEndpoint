package org.radarbase.push.integration.fitbit.service.subscription

import okhttp3.Request
import org.radarbase.push.integration.common.user.User

data class SubscriptionRequest(
    val request: Request,
    val user: User,
    val subscriptionID: String
)
