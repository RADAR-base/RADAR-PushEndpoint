package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarbase.push.integration.common.user.User
import org.radarcns.kafka.ObservationKey
import org.radarcns.push.garmin.GarminHeartRateVariabilitySample
import java.time.Instant

class HeartRateVariabilitySampleGarminAvroConverter(
    topic: String = "push_integration_garmin_heart_rate_sample"
) :
    GarminAvroConverter(topic) {
    override fun validate(tree: JsonNode) = Unit

    override fun convert(tree: JsonNode, user: User): List<Pair<SpecificRecord, SpecificRecord>> {
        return tree[ROOT].map { node ->
            getSamples(
                node[SUB_NODE], node["summaryId"].asText(),
                user.observationKey, node["startTimeInSeconds"].asDouble()
            )
        }.flatten()
    }

    private fun getSamples(
        node: JsonNode?,
        summaryId: String,
        observationKey: ObservationKey,
        startTime: Double
    ): List<Pair<ObservationKey, GarminHeartRateVariabilitySample>> {
        if (node == null) {
            return emptyList()
        }

        return node.fields().asSequence().map { (key, value) ->
            Pair(
                observationKey,
                GarminHeartRateVariabilitySample.newBuilder().apply {
                    this.summaryId = summaryId
                    this.time = startTime + key.toDouble()
                    this.timeReceived = Instant.now().toEpochMilli() / 1000.0
                    this.hrvValue = value?.floatValue()
                }.build()
            )
        }.toList()
    }

    companion object {
        const val ROOT = HeartRateVariabilityGarminAvroConverter.ROOT
        const val SUB_NODE = "hrvValues"
    }
}
