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
package org.radarbase.push.integration.fitbit.request

import jakarta.ws.rs.NotAuthorizedException
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.common.user.UserRepository
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * Authenticator for Fitbit, which tries to refresh the access token if a request is unauthorized.
 */
class TokenAuthenticator internal constructor(user: User, userRepository: UserRepository) : Authenticator {
    private val user: User
    private val userRepository: UserRepository

    init {
        this.user = user
        this.userRepository = userRepository
    }

    @Throws(IOException::class)
    override fun authenticate(requestRoute: Route?, response: Response): Request? {
        return if (response.code != 401) {
            null
        } else try {
            val newAccessToken: String = userRepository.getAccessToken(user)
            response.request.newBuilder()
                .header("Authorization", "Bearer $newAccessToken")
                .build()
        } catch (ex: NotAuthorizedException) {
            logger.error("Cannot get a new refresh token for user {}. Cancelling request.", user, ex)
            null
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TokenAuthenticator::class.java)
    }
}
