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
package org.radarbase.push.integration.fitbit.converter

import okhttp3.Headers
import org.radarbase.push.integration.fitbit.request.FitbitRestRequest
import java.io.IOException
import java.time.Duration
import java.time.Instant

interface PayloadToSourceRecordConverter {
    @Throws(IOException::class)
    fun convert(
        request: FitbitRestRequest, headers: Headers, data: ByteArray
    ): Sequence<Result<TopicData>>

    companion object {
        fun nearFuture(): Instant {
            return Instant.now().plus(NEAR_FUTURE)
        }

        val MIN_INSTANT = Instant.EPOCH
        const val TIMESTAMP_OFFSET_KEY = "timestamp"
        private val NEAR_FUTURE = Duration.ofDays(31L)
    }
}
