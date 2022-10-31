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
package org.radarbase.push.integration.fitbit.request.route

import java.time.Duration
import java.time.Instant
import java.time.temporal.TemporalAmount

interface PollingRequestRoute : RequestRoute {
    /**
     * General polling interval for retrying this route.
     */
    val pollInterval: Duration?

    /**
     * Last time the route was polled.
     */
    val lastPoll: Instant

    /**
     * Actual times that new data will be needed.
     */
    fun nextPolls(): Sequence<Instant?>

    /**
     * Get the time that this route should be polled again.
     */
    override val timeOfNextRequest: Instant?
        get() = max(
            lastPoll.plus(pollInterval),
            nextPolls()
                .minWithOrNull(Comparator.naturalOrder())
                ?: nearFuture()
        )

    companion object {
        fun nearFuture(): Instant? {
            return Instant.now().plus(NEAR_FUTURE)
        }

        fun <T : Comparable<T>?> max(a: T, b: T): T {
            return if (a != null && (b == null || a >= b)) a else b
        }

        private val NEAR_FUTURE: TemporalAmount = Duration.ofDays(31L)
    }
}
