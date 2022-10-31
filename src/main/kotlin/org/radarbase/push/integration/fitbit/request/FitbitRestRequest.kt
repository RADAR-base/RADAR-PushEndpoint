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
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.fitbit.converter.DateRange
import org.radarbase.push.integration.fitbit.converter.TopicData
import org.radarbase.push.integration.fitbit.request.route.RequestRoute
import java.io.IOException
import java.util.function.Predicate

/**
 * REST request taking into account the user and offsets queried. The offsets are useful for
 * defining what dates to poll (again).
 */
class FitbitRestRequest(
    private val route: RequestRoute,
    private val request: Request,
    val user: User,
    private val client: OkHttpClient, dateRange: DateRange,
    private val isValid: Predicate<FitbitRestRequest>,
) {

    private val dateRange: DateRange

    init {
        this.dateRange = dateRange
    }

    fun getDateRange(): DateRange {
        return dateRange
    }

    fun getRequest(): Request {
        return request
    }

    val isStillValid: Boolean
        get() = isValid.test(this)

    /**
     * Handle the request using the internal client, using the request route converter.
     * @return stream of resulting source records.
     * @throws IOException if making or parsing the request failed.
     */
    @Throws(IOException::class)
    fun handleRequest(): Sequence<Result<TopicData>> {
        if (!isStillValid) {
            return emptySequence()
        }
        val records: Sequence<Result<TopicData>>
        var data: ByteArray
        var headers: Headers
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    route.requestFailed(this, response)
                    return emptySequence()
                }
                headers = response.headers
                val body = response.body
                data = body?.bytes() ?: return emptySequence()
            }
        } catch (ex: IOException) {
            route.requestFailed(this, null)
            throw ex
        } catch (ex: NotAuthorizedException) {
            route.requestFailed(this, null)
            throw ex
        }
        records = route.converter().convert(this, headers, data)
        if (records.count() == 0) {
            route.requestEmpty(this)
        } else {
            route.requestSucceeded(this, records)
        }
        return records
    }

    override fun toString(): String {
        return ("FitbitRestRequest{"
            + "url=" + request.url
            + ", user=" + user
            + ", dateRange=" + dateRange
            + '}')
    }
}
