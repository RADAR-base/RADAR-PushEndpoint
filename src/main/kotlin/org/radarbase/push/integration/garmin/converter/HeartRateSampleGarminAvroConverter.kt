package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarcns.kafka.ObservationKey
import org.radarcns.push.garmin.GarminHeartRateSample
import java.time.Instant
import javax.ws.rs.container.ContainerRequestContext

class HeartRateSampleGarminAvroConverter(
    topic: String = "push_integration_garmin_heart_rate_sample"
) :
    GarminAvroConverter(topic) {
    override fun validate(tree: JsonNode) = Unit

    override fun convert(
        tree: JsonNode,
        request: ContainerRequestContext
    ): List<Pair<SpecificRecord, SpecificRecord>> {
        val observationKey = observationKey(request)

        return tree[ROOT].map { node ->
            getSamples(
                node[SUB_NODE], node["summaryId"].asText(),
                observationKey, node["startTimeInSeconds"].asDouble()
            )
        }.flatten()
    }

    private fun getSamples(
        node: JsonNode?,
        summaryId: String,
        observationKey: ObservationKey,
        startTime: Double
    ): List<Pair<ObservationKey, GarminHeartRateSample>> {
        if (node == null) {
            return emptyList()
        }

        return node.fields().asSequence().map { (key, value) ->
            Pair(
                observationKey,
                GarminHeartRateSample.newBuilder().apply {
                    this.summaryId = summaryId
                    this.time = startTime + key.toDouble()
                    this.timeReceived = Instant.now().toEpochMilli() / 1000.0
                    this.heartRate = value?.floatValue()
                }.build()
            )
        }.toList()
    }

    companion object {
        const val ROOT = "dailies"
        const val SUB_NODE = "timeOffsetHeartRateSamples"
    }
}
