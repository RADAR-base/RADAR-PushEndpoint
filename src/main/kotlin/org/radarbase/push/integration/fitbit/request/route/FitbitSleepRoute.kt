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

import org.radarbase.gateway.Config
import org.radarbase.gateway.kafka.ProducerPool
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.common.user.UserRepository
import org.radarbase.push.integration.fitbit.converter.DateRange
import org.radarbase.push.integration.fitbit.converter.FitbitSleepDataConverter
import org.radarbase.push.integration.fitbit.request.FitbitRequestGenerator
import org.radarbase.push.integration.fitbit.request.FitbitRestRequest
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

open class FitbitSleepRoute(
    generator: FitbitRequestGenerator,
    userRepository: UserRepository,
    config: Config,
    producerPool: ProducerPool
) : FitbitPollingRoute(generator, userRepository, "sleep", config, producerPool) {
    private val converter = FitbitSleepDataConverter(
        config.pushIntegration.fitbit.sleepStagesTopic,
        config.pushIntegration.fitbit.sleepClassicTopic
    )

    override fun getUrlFormat(baseUrl: String?): String {
        return "$baseUrl/1.2/user/%s/sleep/list.json?sort=asc&afterDate=%s&limit=100&offset=0"
    }

    /**
     * Actually construct a request, based on the current offset
     * @param user Fitbit user
     * @return request to make
     */
    override fun createRequests(user: User): Sequence<FitbitRestRequest?> {
        val startDate: ZonedDateTime = getOffsets(user)?.lastSuccessOffset?.plus(ONE_SECOND)
            ?.atZone(ZoneOffset.UTC)
            ?.truncatedTo(ChronoUnit.SECONDS)
            ?: user.startDate.atZone(ZoneOffset.UTC)

        val endDate: ZonedDateTime = getOffsets(user)?.latestOffset?.plus(ONE_SECOND)
            ?.atZone(ZoneOffset.UTC)
            ?.truncatedTo(ChronoUnit.SECONDS)
            ?: return emptySequence()

        return if (endDate > startDate) {
            sequenceOf(
                newRequest(
                    user, DateRange(startDate, endDate),
                    user.serviceUserId, DATE_TIME_FORMAT.format(startDate)
                )
            )
        } else emptySequence()
    }

    override var pollIntervalPerUser: Duration
        get() = SLEEP_POLL_INTERVAL
        set(pollIntervalPerUser) {
            super.pollIntervalPerUser = pollIntervalPerUser
        }

    override fun converter(): FitbitSleepDataConverter {
        return converter
    }

    companion object {
        val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
            .withZone(ZoneOffset.UTC)
        private val SLEEP_POLL_INTERVAL = Duration.ofDays(1)
    }
}
