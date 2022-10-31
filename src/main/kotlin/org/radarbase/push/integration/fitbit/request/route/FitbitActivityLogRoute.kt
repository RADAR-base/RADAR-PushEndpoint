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
import org.radarbase.push.integration.fitbit.converter.FitbitActivityLogDataConverter
import org.radarbase.push.integration.fitbit.request.FitbitRequestGenerator
import org.radarbase.push.integration.fitbit.request.FitbitRestRequest
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

open class FitbitActivityLogRoute(
    generator: FitbitRequestGenerator,
    userRepository: UserRepository,
    config: Config,
    producerPool: ProducerPool
) : FitbitPollingRoute(generator, userRepository, "activity_log", config, producerPool) {
    private val converter: FitbitActivityLogDataConverter

    init {
        converter = FitbitActivityLogDataConverter(config.pushIntegration.fitbit.activityLogTopic)
    }

    override fun getUrlFormat(baseUrl: String?): String {
        return "$baseUrl/1/user/%s/activities/list.json?sort=asc&afterDate=%s&limit=20&offset=0"
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
                    user.serviceUserId, FitbitSleepRoute.DATE_TIME_FORMAT.format(startDate)
                )
            )
        } else emptySequence()
    }

    override var pollIntervalPerUser: Duration
        protected get() = ACTIVITY_LOG_POLL_INTERVAL
        set(pollIntervalPerUser) {
            super.pollIntervalPerUser = pollIntervalPerUser
        }

    override fun converter(): FitbitActivityLogDataConverter {
        return converter
    }

    companion object {
        val DATE_TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME
            .withZone(ZoneOffset.UTC)
        private val ACTIVITY_LOG_POLL_INTERVAL = Duration.ofDays(1)
    }
}
