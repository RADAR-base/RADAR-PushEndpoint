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
package org.radarbase.push.integrations.garmin.user

import org.radarbase.push.integrations.common.user.User
import java.io.IOException
import java.util.stream.Stream
import javax.ws.rs.NotAuthorizedException

/**
 * User repository for Garmin users.
 */
interface UserRepository {
    /**
     * Get specified Garmin user.
     *
     * @throws IOException if the user cannot be retrieved from the repository.
     */
    @Throws(IOException::class)
    operator fun get(key: String): User?

    /**
     * Get all relevant Garmin users.
     *
     * @throws IOException if the list cannot be retrieved from the repository.
     */
    @Throws(IOException::class)
    fun stream(): Stream<out User>

    /**
     * Get the current access token of given user.
     *
     * @throws IOException            if the new access token cannot be retrieved from the repository.
     * @throws NotAuthorizedException if the refresh token is no longer valid. Manual action
     * should be taken to get a new refresh token.
     * @throws NoSuchElementException if the user does not exists in this repository.
     */
    @Throws(IOException::class, NotAuthorizedException::class)
    fun getAccessToken(user: User): String

    /**
     * Get the current access token Secret of given user.
     *
     * @throws IOException            if the new access token secret cannot be retrieved from the repository.
     * @throws NotAuthorizedException if the token is no longer valid. Manual action
     * should be taken to get a new token.
     * @throws NoSuchElementException if the user does not exists in this repository.
     */
    @Throws(IOException::class, NotAuthorizedException::class)
    fun getUserAccessTokenSecret(user: User): String

    /**
     * This is to report any deregistrations of the users.
     * This should update the user's authorised status to false in the external repository.
     *
     * @throws IOException            if the user's status cannot be updated in the repository.
     * @throws NoSuchElementException if the user does not exists in this repository.
     */
    @Throws(IOException::class)
    fun reportDeregistration(user: User)

    /**
     * The functions allows the repository to supply when there are pending updates.
     * This gives more control to the user repository in updating and caching users.
     *
     * @return `true` if there are new updates available, `false` otherwise.
     */
    fun hasPendingUpdates(): Boolean

    /**
     * Apply any pending updates to users. This could include, for instance, refreshing a cache
     * of users with latest information.
     * This is called when [.hasPendingUpdates] is `true`.
     *
     * @throws IOException if there was an error when applying updates.
     */
    @Throws(IOException::class)
    fun applyPendingUpdates()
}
