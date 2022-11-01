package org.radarbase.push.integration.fitbit.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecordBase
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class FitbitFoodLogConverter(
    private val foodLogTopic: String
): FitbitDataConverter {
    override fun processRecords(
        dateRange: DateRange,
        root: JsonNode,
        timeReceived: Double
    ): Sequence<Result<TopicData>> {
        val array = root.optArray("foods")?:return emptySequence()

        return array.asSequence()
            .sortedBy { it["logDate"].textValue() }
            .mapCatching { s ->
                val startTime = OffsetDateTime.parse(s["logDate"].textValue())
                val startInstant = startTime.toInstant()
                TopicData(
                    sourceOffset = startInstant,
                    topic = foodLogTopic,
                    value = s.toFoodLogRecord(startInstant, startTime.offset)
                )
            }
    }

    private fun JsonNode.toFoodLogRecord(
        startTime: Instant, offset: ZoneOffset
    ):SpecificRecordBase{
        TODO("RADARSchemas ready")
    }

}