package org.radarbase.push.integration.fitbit.subscription

import okhttp3.Request
import okhttp3.internal.EMPTY_REQUEST
import org.radarbase.gateway.Config
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.fitbit.user.FitbitUserRepository

class SubscriptionRequestGenerator(
    val config: Config, private val userRepository: FitbitUserRepository
) {
    private fun subscriptionUrl(user: User, subscriptionID: String): String {
        return "https://api.fitbit.com/1/user/" + user.serviceUserId + "/apiSubscriptions/" + subscriptionID + ".json"
    }

    fun subscriptionCreationRequest(user: User, subscriptionID: String?): SubscriptionRequest {
        if (subscriptionID == null) {
            throw NullSubscriptionIDException()
        }
        return SubscriptionRequest(
            Request.Builder().url(subscriptionUrl(user, subscriptionID))
                .addHeader("accept", "application/json")
                .addHeader("authorization", "Bearer " + userRepository.getAccessToken(user))
                .addHeader(
                    "X-Fitbit-Subscriber-Id",
                    config.pushIntegration.fitbit.subscriptionConfig.subscriberID
                ).post(EMPTY_REQUEST).build(), user, subscriptionID
        )
    }

    fun subscriptionDeletionRequest(user: User, subscriptionID: String?): SubscriptionRequest {
        if (subscriptionID == null) {
            throw NullSubscriptionIDException()
        }
        return SubscriptionRequest(
            Request.Builder().url(subscriptionUrl(user, subscriptionID))
                .addHeader("accept", "application/json")
                .addHeader("authorization", "Bearer " + userRepository.getAccessToken(user))
                .addHeader(
                    "X-Fitbit-Subscriber-Id",
                    config.pushIntegration.fitbit.subscriptionConfig.subscriberID
                ).delete(EMPTY_REQUEST).build(), user, subscriptionID
        )
    }
}