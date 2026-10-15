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

package org.radarbase.push.integration.google.util

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Filters built from a notification's civil interval, i.e. the user's local clock times. Used for
 * the types Google stores by local time (daily summaries and exercise), so the filter does not
 * depend on the user's current time zone setting.
 * Reference: https://developers.google.com/health/reference/rest/v4/users.dataTypes.dataPoints/list
 */
object GoogleHealthCivilFilter {
    private val CIVIL_DT_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    /**
     * Daily summary filter for exactly the local dates the interval covers. Google sends a daily
     * summary as a zero-length interval at local midnight (e.g. 2026-10-10T00:00:00 to
     * 2026-10-10T00:00:00.000001), and a full day as midnight to the next midnight. Both cover
     * exactly one date.
     */
    fun dailyDate(dataType: String, start: LocalDateTime, end: LocalDateTime): String {
        val stem = dataType.replace('-', '_')
        val startDate = start.toLocalDate()
        val endsAtMidnight = end.toLocalTime() == LocalTime.MIDNIGHT && end.toLocalDate().isAfter(startDate)
        val endDate = if (endsAtMidnight) end.toLocalDate() else end.toLocalDate().plusDays(1)
        return "$stem.date >= \"$startDate\" AND $stem.date < \"$endDate\""
    }

    /** Session filter on the local start time, widened by [overlap] on both sides. */
    fun civilStartTime(dataType: String, start: LocalDateTime, end: LocalDateTime, overlap: Duration): String {
        val stem = dataType.replace('-', '_')
        val from = CIVIL_DT_FMT.format(start.minus(overlap))
        val to = CIVIL_DT_FMT.format(end.plus(overlap))
        return "$stem.interval.civil_start_time >= \"$from\" AND $stem.interval.civil_start_time < \"$to\""
    }
}
