package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarbase.push.integration.common.user.User
import org.radarcns.push.garmin.GarminSleepScoreSample
import java.time.Instant

class SleepScoreGarminAvroConverter(
    topic: String = "push_integration_garmin_sleep_score"
) :
    GarminAvroConverter(topic) {
    override fun validate(tree: JsonNode) = Unit

    override fun convert(tree: JsonNode, user: User): List<Pair<SpecificRecord, SpecificRecord>> {
        return tree[ROOT].map { node -> Pair(user.observationKey, getRecord(node)) }
    }

    private fun getRecord(node: JsonNode): SpecificRecord {
        return GarminSleepScoreSample.newBuilder().apply {
            summaryId = summaryId
            time = node["startTimeInSeconds"].asDouble()
            startTimeOffset = node["startTimeOffsetInSeconds"]?.asInt()
            timeReceived = Instant.now().toEpochMilli() / 1000.0
            totalDurationScoreQualifier = node[SUB_NODE]?.get("totalDuration")?.get(QUALIFIER)?.asText()
            stressScoreQualifier = node[SUB_NODE]?.get("stress")?.get(QUALIFIER)?.asText()
            awakeCountScoreQualifier = node[SUB_NODE]?.get("awakeCount")?.get(QUALIFIER)?.asText()
            remPercentageScoreQualifier = node[SUB_NODE]?.get("remPercentage")?.get(QUALIFIER)?.asText()
            restlessnessScoreQualifier = node[SUB_NODE]?.get("restlessness")?.get(QUALIFIER)?.asText()
            lightPercentageScoreQualifier = node[SUB_NODE]?.get("lightPercentage")?.get(QUALIFIER)?.asText()
            deepPercentageScoreQualifier = node[SUB_NODE]?.get("deepPercentage")?.get(QUALIFIER)?.asText()
        }.build()
    }

    companion object {
        const val ROOT = "sleeps"
        const val SUB_NODE = "sleepScores"
        const val QUALIFIER = "qualifierKey"
    }
}
