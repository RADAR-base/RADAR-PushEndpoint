package org.radarbase.push.integration.fitbit.request.route

import org.radarbase.gateway.Config
import org.radarbase.gateway.kafka.ProducerPool
import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.common.user.UserRepository
import org.radarbase.push.integration.fitbit.converter.DateRange
import org.radarbase.push.integration.fitbit.converter.FitbitFoodLogConverter
import org.radarbase.push.integration.fitbit.converter.PayloadToSourceRecordConverter
import org.radarbase.push.integration.fitbit.request.FitbitRequestGenerator
import org.radarbase.push.integration.fitbit.request.FitbitRestRequest
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class FitbitFoodLogRoute(
    generator: FitbitRequestGenerator,
    userRepository: UserRepository,
    config: Config,
    producerPool: ProducerPool
) : FitbitPollingRoute(generator, userRepository, "food_log", config, producerPool) {
    private val converter: FitbitFoodLogConverter

    init {
        converter = FitbitFoodLogConverter(config.pushIntegration.fitbit.foodLogTopic)
    }

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

    override fun getUrlFormat(baseUrl: String?): String {
        return "$baseUrl/1/user/%s/foods/log/date/%s.json"
    }

    override fun converter(): PayloadToSourceRecordConverter {
        return converter
    }

    override var pollIntervalPerUser: Duration
        get() = FOOD_LOG_POLL_INTERVAL
        set(pollIntervalPerUser) {
            super.pollIntervalPerUser = pollIntervalPerUser
        }

    companion object {
        val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
            .withZone(ZoneOffset.UTC)
        private val FOOD_LOG_POLL_INTERVAL = Duration.ofDays(1)
    }
}