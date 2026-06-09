/*
 * Copyright 2026 King's College London
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.radarbase.push.integration.google.subscriptions.model

/** A subscription as returned by Google's list endpoint. */
data class RemoteSubscription(
    val name: String,
    val user: String,
    val dataTypes: List<String>,
) {
    val healthUserId: String?
        get() = user.removePrefix("users/").takeIf { it.isNotEmpty() && it != user }

    /**
     * Bare data-type tokens (e.g. "steps"), with Google's "users/{id}/dataTypes/" qualifier stripped.
     * Google stores and returns subscription data types as fully-qualified resource names like
     * (users/{userId}/dataTypes/daily-resting-heart-rate), while the
     * config and create payloads use bare tokens, normalise before comparing the two.
     */
    val dataTypeIds: List<String>
        get() = dataTypes.map { it.substringAfterLast('/') }
}
