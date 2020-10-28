package org.radarbase.push.integrations.common.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.generic.GenericRecord
import org.apache.avro.specific.SpecificRecord
import java.io.IOException
import javax.ws.rs.container.ContainerRequestContext
import kotlin.jvm.Throws

interface AvroConverter {

    val topic: String

    @Throws(IOException::class)
    fun convert(
        tree: JsonNode,
        request: ContainerRequestContext
    ): List<Pair<SpecificRecord, SpecificRecord>>
}
