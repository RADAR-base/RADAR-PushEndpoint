package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarcns.push.integration.garmin.GarminUserMetrics
import java.time.Instant
import javax.ws.rs.BadRequestException
import javax.ws.rs.container.ContainerRequestContext

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
        request: ContainerRequestContext
    ): List<Pair<SpecificRecord, SpecificRecord>> {

        val observationKey = observationKey(request)
        return tree[ROOT]
            .map { node -> Pair(observationKey, getRecord(node)) }
    }

    private fun getRecord(node: JsonNode): GarminUserMetrics {
        return GarminUserMetrics.newBuilder().apply {
            summaryId = node["summaryId"]?.asText()
            time = Instant.now().toEpochMilli() / 1000.0
            timeReceived = time
            calendarDate = node["calendarDate"]?.asText()
            vo2Max = node["vo2Max"]?.asDouble()
            fitnessAge = node["fitnessAge"]?.asInt()
        }.build()
    }

    companion object {
        const val ROOT = "userMetrics"
    }
}
