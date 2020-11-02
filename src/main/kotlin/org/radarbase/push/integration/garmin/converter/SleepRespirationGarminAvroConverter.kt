package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarcns.kafka.ObservationKey
import org.radarcns.push.garmin.GarminRespiration
import java.time.Instant
import javax.ws.rs.container.ContainerRequestContext

class SleepRespirationGarminAvroConverter(
    topic: String = "push_integration_garmin_respiration"
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
                observationKey, node["startTimeInSeconds"].asDouble(),
                node["startTimeOffsetInSeconds"]?.intValue()
            )
        }.flatten()
    }

    private fun getSamples(
        node: JsonNode?,
        summaryId: String,
        observationKey: ObservationKey,
        startTime: Double,
        offset: Int?
    ): List<Pair<ObservationKey, GarminRespiration>> {
        if (node == null) {
            return emptyList()
        }
        return node.fields().asSequence().map { (key, value) ->
            Pair(
                observationKey,
                GarminRespiration.newBuilder().apply {
                    this.summaryId = summaryId
                    this.time = startTime + key.toDouble()
                    this.timeReceived = Instant.now().toEpochMilli() / 1000.0
                    this.respiration = value?.floatValue()
                    this.startTimeOffset = offset
                }.build()
            )
        }.toList()
    }

    companion object {
        const val ROOT = "sleeps"
        const val SUB_NODE = "timeOffsetSleepRespiration"
    }
}
