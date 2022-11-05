package org.radarbase.push.integration.fitbit.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecordBase
import org.radarcns.connector.fitbit.FitbitFoodLog
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class FitbitFoodLogConverter(
    private val foodLogTopic: String
) : FitbitDataConverter {
    override fun processRecords(
        dateRange: DateRange,
        root: JsonNode,
        timeReceived: Double
    ): Sequence<Result<TopicData>> {
        val array = root.optArray("foods") ?: return emptySequence()

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
    ): SpecificRecordBase {
        return FitbitFoodLog.newBuilder().apply {
            time = startTime.toEpochMilli() / 1000.0
            timeReceived = System.currentTimeMillis() / 1000.0
            isFavorite =
                requireNotNull(optBoolean("isFavorite")) { "Food log isFavorite not specified" }
            logId = requireNotNull(optLong("logId")) { "Food log logId not specified" }
            accessLevel = get("loggedFood").optString("accessLevel")
            amount =
                requireNotNull(get("loggedFood").optInt("amount")) { "Food log amount not specified" }
            brand = get("loggedFood").optString("brand")
            foodId =
                requireNotNull(get("loggedFood").optLong("foodId")) { "Food log foodId not specified" }
            locale = get("loggedFood").optString("locale")
            mealTypeId =
                requireNotNull(get("loggedFood").optLong("mealTypeId")) { "Food log mealTypeId not specified" }
            name = get("loggedFood").optString("name")
            unitId = requireNotNull(
                get("loggedFood").get("unit").optLong("id")
            ) { "Food log unitId not specified" }
            unitName = get("loggedFood").get("unit").optString("name")
            unitPlural = get("loggedFood").get("unit").optString("plural")
            calories =
                requireNotNull(get("nutritionalValues").optFloat("calories")) { "Food log calories not specified" }
            carbs =
                requireNotNull(get("nutritionalValues").optFloat("carbs")) { "Food log carbs not specified" }
            fat =
                requireNotNull(get("nutritionalValues").optFloat("fat")) { "Food log fat not specified" }
            fiber =
                requireNotNull(get("nutritionalValues").optFloat("fiber")) { "Food log fiber not specified" }
            protein =
                requireNotNull(get("nutritionalValues").optFloat("protein")) { "Food log protein not specified" }
            sodium =
                requireNotNull(get("nutritionalValues").optFloat("sodium")) { "Food log sodium not specified" }
        }.build()
    }

}