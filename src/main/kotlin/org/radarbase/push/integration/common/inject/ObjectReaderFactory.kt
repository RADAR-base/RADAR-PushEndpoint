package org.radarbase.push.integration.common.inject

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import jakarta.ws.rs.core.Context
import java.util.*
import kotlin.reflect.KClass

class ObjectReaderFactory(
    @Context private val mapper: ObjectMapper,
) {
    private val readers = IdentityHashMap<KClass<*>, ObjectReader>()

    fun readerFor(kClass: KClass<*>): ObjectReader {
        return readers.computeIfAbsent(kClass) { cl -> mapper.readerFor(cl.java) }
    }
}
