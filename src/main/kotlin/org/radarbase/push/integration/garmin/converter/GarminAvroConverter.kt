package org.radarbase.push.integration.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarbase.push.integration.common.converter.AvroConverter
import org.radarbase.push.integration.common.user.User
import jakarta.ws.rs.BadRequestException

abstract class GarminAvroConverter(override val topic: String) : AvroConverter {

    @Throws(BadRequestException::class)
    abstract fun validate(tree: JsonNode)

    fun validateAndConvert(tree: JsonNode, user: User):
            List<Pair<SpecificRecord, SpecificRecord>> {
        validate(tree)
        return convert(tree, user)
    }
}
