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

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import okhttp3.OkHttpClient
import org.radarbase.gateway.Config
import org.radarbase.gateway.kafka.ProducerPool
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.common.user.UserRepository
import org.radarbase.push.integration.fitbit.request.route.FitbitActivityLogRoute
import org.radarbase.push.integration.fitbit.request.route.FitbitFoodLogRoute
import org.radarbase.push.integration.fitbit.request.route.FitbitSleepRoute
import org.radarbase.push.integration.fitbit.request.route.RequestRoute
import org.slf4j.LoggerFactory

/**
 * Generate all requests for Fitbit API.
 */
class FitbitRequestGenerator(
    private val userRepository: UserRepository,
    config: Config,
    producerPool: ProducerPool
) : RequestGeneratorRouter() {
    private var baseClient: OkHttpClient? = OkHttpClient()
    private val clients: MutableMap<String, OkHttpClient> = mutableMapOf()
    private var routes: List<RequestRoute> = mutableListOf(
        FitbitSleepRoute(this, userRepository, config, producerPool),
        FitbitActivityLogRoute(this, userRepository, config, producerPool),
        FitbitFoodLogRoute(this, userRepository, config, producerPool)
    )

    override fun routes(): Sequence<RequestRoute> {
        return routes.asSequence()
    }

    fun getClient(user: User): OkHttpClient {
        return clients.computeIfAbsent(user.id) {
            baseClient!!.newBuilder()
                .authenticator(TokenAuthenticator(user, userRepository))
                .build()
        }
    }

    companion object {
        val JSON_FACTORY = JsonFactory()
        val JSON_READER = ObjectMapper(JSON_FACTORY)
            .registerModule(JavaTimeModule())
            .reader()
    }
}
