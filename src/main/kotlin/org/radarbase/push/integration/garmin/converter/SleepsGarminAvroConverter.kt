package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarbase.push.integration.common.user.User
import org.radarcns.push.garmin.GarminSleepSummary
import java.time.Instant
import jakarta.ws.rs.BadRequestException

class SleepsGarminAvroConverter(topic: String = "push_integration_garmin_sleep") :
    GarminAvroConverter(topic) {

    override fun validate(tree: JsonNode) {
        val sleeps = tree[ROOT]
        if (sleeps == null || !sleeps.isArray) {
            throw BadRequestException("The sleep data was invalid.")
        }
    }

    override fun convert(tree: JsonNode, user: User): List<Pair<SpecificRecord, SpecificRecord>> {
        return tree[ROOT]
            .map { node -> Pair(user.observationKey, getRecord(node)) }
    }

    private fun getRecord(node: JsonNode): GarminSleepSummary {
        return GarminSleepSummary.newBuilder().apply {
            summaryId = node["summaryId"]?.asText()
            time = node["startTimeInSeconds"].asDouble()
            timeReceived = Instant.now().toEpochMilli() / 1000.0
            date = node["calendarDate"]?.asText()
            startTimeOffset = node["startTimeOffsetInSeconds"]?.asInt()
            duration = node["durationInSeconds"]?.asInt()
            unmeasurableSleepDuration = node["unmeasurableSleepDurationInSeconds"]?.asInt()
            deepSleepDuration = node["deepSleepDurationInSeconds"]?.asInt()
            lightSleepDuration = node["lightSleepDurationInSeconds"]?.asInt()
            remSleepDuration = node["remSleepInSeconds"]?.asInt()
            awakeDuration = node["awakeDurationInSeconds"]?.asInt()
            validation = node["validation"]?.asText()
        }.build()
    }

    companion object {
        const val ROOT = "sleeps"
    }
}
