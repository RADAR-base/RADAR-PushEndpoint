/*
 * Copyright 2026 King's College London
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.radarbase.push.integration.google.tcx

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.radarbase.push.integration.google.tcx.model.TcxTrackpoint
import org.radarbase.push.integration.google.tcx.model.Trackpoint
import org.radarbase.push.integration.google.tcx.model.TrainingCenterDatabase
import org.slf4j.LoggerFactory
import java.time.Instant
import javax.xml.stream.XMLInputFactory

/**
 * Parses a raw TCX document returned by Google Health's
 * `exportExerciseTcx?alt=media`.
 *
 */
object TcxParser {
    private val logger = LoggerFactory.getLogger(TcxParser::class.java)

    private val xmlMapper: XmlMapper = XmlMapper.builder()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build()
        .apply {
            registerKotlinModule()
            val inputFactory = factory.getXMLInputFactory()
            inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
            inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        }

    fun parse(xml: ByteArray): List<TcxTrackpoint> {
        if (xml.isEmpty()) return emptyList()
        val db = xmlMapper.readValue(xml, TrainingCenterDatabase::class.java)
        return db.activities?.activities.orEmpty()
            .flatMap { it.laps }
            .flatMap { it.tracks }
            .flatMap { it.trackpoints }
            .mapNotNull(::toTrackpoint)
    }

    private fun toTrackpoint(tp: Trackpoint): TcxTrackpoint? {
        val time = tp.time?.trim()?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: run {
            logger.debug("Skipping TCX trackpoint without a parseable <Time>")
            return null
        }
        return TcxTrackpoint(
            time = time,
            latitude = tp.position?.latitudeDegrees,
            longitude = tp.position?.longitudeDegrees,
            altitudeMeters = tp.altitudeMeters,
            distanceMeters = tp.distanceMeters,
            heartRateBpm = tp.heartRateBpm?.value,
            cadence = tp.cadence ?: tp.extensions?.tpx?.runCadence,
            speedMetersPerSecond = tp.extensions?.tpx?.speed,
        )
    }
}
