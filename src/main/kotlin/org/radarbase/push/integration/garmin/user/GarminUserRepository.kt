/*
 * Copyright 2018 The Hyve
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.radarbase.push.integration.garmin.user

import org.radarbase.gateway.Config
import org.radarbase.push.integration.common.auth.SignRequestParams
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.common.user.UserRepository
import java.io.IOException
import java.time.Instant
import jakarta.ws.rs.NotAuthorizedException

/**
 * User repository for Garmin users.
 */
abstract class GarminUserRepository(private val config: Config) : UserRepository {

    /**
     * Garmin uses Oauth 1.0 and hence has a user access
     * token secret instead of a refresh token. This should
     * not be required in most cases anyways since only the access token
     * is required.
     */
    @Throws(IOException::class, NotAuthorizedException::class)
    override fun getRefreshToken(user: User): String {
        return getUserAccessTokenSecret(user)
    }

    @Throws(IOException::class, NotAuthorizedException::class)
    abstract fun getUserAccessTokenSecret(user: User): String

    fun getBackfillStartDate(user: User): Instant {
        return config.pushIntegration.garmin.backfill.userBackfill.find {
            it.userId == user.versionedId
        }?.startDate ?: user.startDate
    }

    fun getBackfillEndDate(user: User): Instant {
        return config.pushIntegration.garmin.backfill.userBackfill.find {
            it.userId == user.versionedId
        }?.endDate?.takeIf { it <= user.endDate }
            ?: config.pushIntegration.garmin.backfill.defaultEndDate.takeIf { it < user.endDate }
            ?: user.createdAt
    }

    abstract fun getSignedRequest(user: User, payload: SignRequestParams): SignRequestParams


    /**
     * This is to deregister the users from garmin. It requires serviceUserId and userAccessToken.
     *
     * */
    abstract fun deregisterUser(serviceUserId: String, userAccessToken: String)
}
