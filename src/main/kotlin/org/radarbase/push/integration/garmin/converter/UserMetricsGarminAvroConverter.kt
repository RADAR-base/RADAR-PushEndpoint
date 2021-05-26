package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarbase.push.integration.common.user.User
import org.radarcns.push.garmin.GarminUserMetrics
import java.time.Instant
import jakarta.ws.rs.BadRequestException

class UserMetricsGarminAvroConverter(topic: String = "push_integration_garmin_user_metrics") :
    GarminAvroConverter(topic) {

    override fun validate(tree: JsonNode) {
        val userMetrics = tree[ROOT]
        if (userMetrics == null || !userMetrics.isArray) {
            throw BadRequestException("The User Metrics data was invalid.")
        }
    }

    override fun convert(
        tree: JsonNode,
        user: User
    ): List<Pair<SpecificRecord, SpecificRecord>> {

        return tree[ROOT]
            .map { node -> Pair(user.observationKey, getRecord(node)) }
    }

    private fun getRecord(node: JsonNode): GarminUserMetrics {
        return GarminUserMetrics.newBuilder().apply {
            summaryId = node["summaryId"]?.asText()
            time = Instant.now().toEpochMilli() / 1000.0
            timeReceived = time
            date = node["calendarDate"]?.asText()
            vo2Max = node["vo2Max"]?.floatValue()
            fitnessAge = node["fitnessAge"]?.asInt()
        }.build()
    }

    companion object {
        const val ROOT = "userMetrics"
    }
}
