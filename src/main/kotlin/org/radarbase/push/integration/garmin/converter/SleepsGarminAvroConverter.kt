package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarcns.push.integration.garmin.GarminSleepLevelsMap
import org.radarcns.push.integration.garmin.GarminSleepLevelsMapValue
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
            unmeasurableSleepInSeconds = node["unmeasurableSleepInSeconds"]?.asInt()
            deepSleepDurationInSeconds = node["deepSleepDurationInSeconds"]?.asInt()
            lightSleepDurationInSeconds = node["lightSleepDurationInSeconds"]?.asInt()
            remSleepInSeconds = node["remSleepInSeconds"]?.asInt()
            awakeDurationInSeconds = node["awakeDurationInSeconds"]?.asInt()
            sleepLevelsMap = getSleepLevelsMap(node["sleepLevelsMap"])
            validation = node["validation"]?.asText()
            timeOffsetSleepRespiration = getMap(node["timeOffsetSleepRespiration"]) ?: emptyMap()
            timeOffsetSleepSpo2 = getMap(node["timeOffsetSleepSpo2"]) ?: emptyMap()
        }.build()
    }

    private fun getSleepLevelsMap(node: JsonNode?): GarminSleepLevelsMap? {
        if (node == null) {
            return null
        }

        return GarminSleepLevelsMap.newBuilder().apply {
            deep = node["deep"]?.map { deep -> getTimePeriods(deep) } ?: emptyList()
            light = node["light"]?.map { light -> getTimePeriods(light) } ?: emptyList()
            awake = node["awake"]?.map { awake -> getTimePeriods(awake) } ?: emptyList()
            rem = node["rem"]?.map { rem -> getTimePeriods(rem) } ?: emptyList()
        }.build()
    }

    private fun getTimePeriods(node: JsonNode): GarminSleepLevelsMapValue {
        return GarminSleepLevelsMapValue.newBuilder().apply {
            startTimeInSeconds = node["startTimeInSeconds"].asDouble()
            endTimeInSeconds = node["endTimeInSeconds"].asDouble()
        }.build()
    }

    companion object {
        const val ROOT = "sleeps"
    }
}
