package org.radarbase.push.integration.common.converter

import com.fasterxml.jackson.databind.JsonNode
import org.apache.avro.specific.SpecificRecord
import org.radarbase.push.integration.common.user.User
import java.io.IOException

interface AvroConverter {

    val topic: String

    @Throws(IOException::class)
    fun convert(
        tree: JsonNode,
        user: User
    ): List<Pair<SpecificRecord, SpecificRecord>>
}
