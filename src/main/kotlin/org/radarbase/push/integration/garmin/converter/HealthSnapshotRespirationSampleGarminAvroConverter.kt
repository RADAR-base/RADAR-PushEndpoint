package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarbase.push.integration.common.user.User
import org.radarcns.kafka.ObservationKey
import org.radarcns.push.garmin.GarminRespiration
import java.time.Instant

class HealthSnapshotRespirationSampleGarminAvroConverter(
    topic: String = "push_integration_garmin_respiration"
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
    ): List<Pair<ObservationKey, GarminRespiration>> {
        if (node == null) {
            return emptyList()
        }
        val summary = node.find { it["summaryType"]?.asText() == "respiration" } ?: return emptyList()

        return summary["epochSummaries"].fields().asSequence().map { (key, value) ->
            Pair(
                observationKey,
                GarminRespiration.newBuilder().apply {
                    this.summaryId = summaryId
                    this.time = startTime + key.toDouble()
                    this.timeReceived = Instant.now().toEpochMilli() / 1000.0
                    this.respiration = value?.floatValue()
                }.build()
            )
        }.toList()
    }

    companion object {
        const val ROOT = HealthSnapshotGarminAvroConverter.ROOT
        const val SUB_NODE = "summaries"
    }
}
