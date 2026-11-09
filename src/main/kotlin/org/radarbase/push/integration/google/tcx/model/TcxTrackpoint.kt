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

package org.radarbase.push.integration.google.tcx.model

import java.time.Instant

/** A single TCX trackpoint, the parsed form of one `<Trackpoint>` in an exercise track. */
data class TcxTrackpoint(
    val time: Instant,
    val latitude: Double?,
    val longitude: Double?,
    val altitudeMeters: Float?,
    val distanceMeters: Float?,
    val heartRateBpm: Int?,
    val cadence: Int?,
    val speedMetersPerSecond: Float?,
)
