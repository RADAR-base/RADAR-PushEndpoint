package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarcns.push.integration.garmin.GarminMoveIQSummary
import java.time.Instant
import javax.ws.rs.BadRequestException
import javax.ws.rs.container.ContainerRequestContext

class MoveIQGarminAvroConverter(topic: String = "push_integration_garmin_move_iq") :
    GarminAvroConverter(topic) {

    override fun validate(tree: JsonNode) {
        val moveIQs = tree[ROOT]
        if (moveIQs == null || !moveIQs.isArray) {
            throw BadRequestException("The Move IQ data was invalid.")
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

    private fun getRecord(node: JsonNode): GarminMoveIQSummary {
        return GarminMoveIQSummary.newBuilder().apply {
            summaryId = node["summaryId"]?.asText()
            time = node["startTimeInSeconds"].asDouble()
            timeReceived = Instant.now().toEpochMilli() / 1000.0
            offsetInSeconds = node["offsetInSeconds"]?.asInt()
            activityType = node["activityType"]?.asText()
            durationInSeconds = node["durationInSeconds"]?.asInt()
            calendarDate = node["calendarDate"]?.asText()
            activitySubType = node["activitySubType"]?.asText()
        }.build()
    }

    companion object {
        const val ROOT = "moveIQActivities"
    }
}
