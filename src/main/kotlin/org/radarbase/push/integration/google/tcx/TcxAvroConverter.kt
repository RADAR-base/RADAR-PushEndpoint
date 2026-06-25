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

import org.apache.avro.generic.IndexedRecord
import org.radarbase.push.integration.google.tcx.model.TcxTrackpoint
import org.radarcns.kafka.ObservationKey
import org.radarcns.push.googlehealth.GoogleHealthExerciseTcx
import java.time.Instant

/**
 * Maps the parsed [TcxTrackpoint]s of one exercise session into Avro [GoogleHealthExerciseTcx]
 * records — one record per trackpoint, keyed by the user's observation key. The exercise log [id]
 * is carried on every record so trackpoints can be joined back to their [GoogleHealthExercise]
 * session downstream.
 */
object TcxAvroConverter {
    fun convert(
        trackpoints: List<TcxTrackpoint>,
        exerciseId: Long,
        observationKey: ObservationKey,
    ): List<Pair<IndexedRecord, IndexedRecord>> {
        if (trackpoints.isEmpty()) return emptyList()
        val timeReceived = Instant.now().toEpochMilli() / 1000.0
        return trackpoints.map { tp ->
            val record = GoogleHealthExerciseTcx.newBuilder()
                .setTime(tp.time.toEpochMilli() / 1000.0)
                .setTimeReceived(timeReceived)
                .setId(exerciseId)
                .setLatitude(tp.latitude)
                .setLongitude(tp.longitude)
                .setAltitude(tp.altitudeMeters)
                .setDistance(tp.distanceMeters)
                .setHeartRate(tp.heartRateBpm)
                .setCadence(tp.cadence)
                .setSpeed(tp.speedMetersPerSecond)
                .build()
            observationKey to record
        }
    }
}
