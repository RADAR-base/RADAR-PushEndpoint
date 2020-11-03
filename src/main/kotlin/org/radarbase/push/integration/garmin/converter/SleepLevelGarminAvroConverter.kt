package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarbase.push.integration.common.user.User
import org.radarcns.kafka.ObservationKey
import org.radarcns.push.garmin.GarminSleepLevel
import java.time.Instant

class SleepLevelGarminAvroConverter(
    topic: String = "push_integration_garmin_sleep_level"
) :
    GarminAvroConverter(topic) {
    override fun validate(tree: JsonNode) = Unit

    override fun convert(tree: JsonNode, user: User): List<Pair<SpecificRecord, SpecificRecord>> {
        return tree[ROOT].map { node ->
            getSamples(node[SUB_NODE], node["summaryId"].asText(), user.observationKey)
        }.flatten()
    }

    private fun getSamples(
        node: JsonNode?,
        summaryId: String,
        observationKey: ObservationKey
    ): List<Pair<ObservationKey, GarminSleepLevel>> {
        if (node == null) {
            return emptyList()
        }

        return node.fields().asSequence().map { (key, value) ->
            value.map {
                Pair(
                    observationKey,
                    GarminSleepLevel.newBuilder().apply {
                        time = it["startTimeInSeconds"].asDouble()
                        timeReceived = Instant.now().toEpochMilli() / 1000.0
                        this.summaryId = summaryId
                        sleepLevel = key
                        startTime = it["startTimeInSeconds"].asDouble()
                        endTime = it["endTimeInSeconds"].asDouble()
                    }.build()
                )
            }
        }.flatten().toList()
    }

    companion object {
        const val ROOT = "sleeps"
        const val SUB_NODE = "sleepLevelsMap"
    }
}
