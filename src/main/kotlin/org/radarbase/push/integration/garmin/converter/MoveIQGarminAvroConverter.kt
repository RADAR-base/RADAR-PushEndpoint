package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarbase.push.integration.common.user.User
import org.radarcns.push.garmin.GarminMoveIQSummary
import java.time.Instant
import jakarta.ws.rs.BadRequestException

class MoveIQGarminAvroConverter(topic: String = "push_integration_garmin_move_iq") :
    GarminAvroConverter(topic) {

    override fun validate(tree: JsonNode) {
        val moveIQs = tree[ROOT]
        if (moveIQs == null || !moveIQs.isArray) {
            throw BadRequestException("The Move IQ data was invalid.")
        }
    }

    override fun convert(tree: JsonNode, user: User): List<Pair<SpecificRecord, SpecificRecord>> {

        return tree[ROOT]
            .map { node -> Pair(user.observationKey, getRecord(node)) }
    }

    private fun getRecord(node: JsonNode): GarminMoveIQSummary {
        return GarminMoveIQSummary.newBuilder().apply {
            summaryId = node["summaryId"]?.asText()
            time = node["startTimeInSeconds"].asDouble()
            timeReceived = Instant.now().toEpochMilli() / 1000.0
            offset = node["offsetInSeconds"]?.asInt()
            activityType = node["activityType"]?.asText()
            duration = node["durationInSeconds"]?.asInt()
            date = node["calendarDate"]?.asText()
            activitySubType = node["activitySubType"]?.asText()
        }.build()
    }

    companion object {
        const val ROOT = "moveIQActivities"
    }
}
