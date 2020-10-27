package org.radarbase.push.integrations.garmin.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.generic.GenericRecord
import org.radarbase.push.integrations.common.converter.AvroConverter
import org.radarcns.kafka.ObservationKey
import javax.ws.rs.BadRequestException
import javax.ws.rs.container.ContainerRequestContext

abstract class GarminAvroConverter(override val topic: String) : AvroConverter {

    fun observationKey(requestContext: ContainerRequestContext): ObservationKey {
        TODO(
            "Get observation key from the request context. This will be added by the auth " +
                    "validator"
        )
    }

    @Throws(BadRequestException::class)
    abstract fun validate(tree: JsonNode)
}
