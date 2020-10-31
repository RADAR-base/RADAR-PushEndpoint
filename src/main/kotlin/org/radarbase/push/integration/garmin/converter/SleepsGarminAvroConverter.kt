package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarcns.push.integration.garmin.GarminSleepSummary
import java.time.Instant
import javax.ws.rs.BadRequestException
import javax.ws.rs.container.ContainerRequestContext

class SleepsGarminAvroConverter(topic: String = "push_integration_garmin_sleep") :
    GarminAvroConverter(topic) {

    override fun validate(tree: JsonNode) {
        val sleeps = tree[ROOT]
        if (sleeps == null || !sleeps.isArray) {
            throw BadRequestException("The sleep data was invalid.")
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

    private fun getRecord(node: JsonNode): GarminSleepSummary {
        return GarminSleepSummary.newBuilder().apply {
            summaryId = node["summaryId"]?.asText()
            time = node["startTimeInSeconds"].asDouble()
            timeReceived = Instant.now().toEpochMilli() / 1000.0
            calendarDate = node["calendarDate"]?.asText()
            startTimeOffsetInSeconds = node["startTimeOffsetInSeconds"]?.asInt()
            durationInSeconds = node["durationInSeconds"]?.asInt()
            unmeasurableSleepInSeconds = node["unmeasurableSleepDurationInSeconds"]?.asInt()
            deepSleepDurationInSeconds = node["deepSleepDurationInSeconds"]?.asInt()
            lightSleepDurationInSeconds = node["lightSleepDurationInSeconds"]?.asInt()
            remSleepInSeconds = node["remSleepInSeconds"]?.asInt()
            awakeDurationInSeconds = node["awakeDurationInSeconds"]?.asInt()
            validation = node["validation"]?.asText()
        }.build()
    }

    companion object {
        const val ROOT = "sleeps"
    }
}
