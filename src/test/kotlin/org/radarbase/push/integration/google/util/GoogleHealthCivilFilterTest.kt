package org.radarbase.push.integration.google.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

/**
 * The expected filters were checked against the real Google Health API: each one returns the data
 * point the notification is about.
 */
class GoogleHealthCivilFilterTest {

    @Test
    fun `daily summary asks for exactly the notification's local date`() {
        // Real daily-resting-heart-rate notification of 2026-10-10.
        assertEquals(
            "daily_resting_heart_rate.date >= \"2026-10-10\" AND daily_resting_heart_rate.date < \"2026-10-11\"",
            GoogleHealthCivilFilter.dailyDate(
                "daily-resting-heart-rate",
                LocalDateTime.parse("2026-10-10T00:00:00"),
                LocalDateTime.parse("2026-10-10T00:00:00.000001"),
            ),
        )
    }

    @Test
    fun `full-day interval covers one date`() {
        assertEquals(
            "daily_sleep_temperature_derivations.date >= \"2026-07-14\" AND " +
                "daily_sleep_temperature_derivations.date < \"2026-07-15\"",
            GoogleHealthCivilFilter.dailyDate(
                "daily-sleep-temperature-derivations",
                LocalDateTime.parse("2026-07-14T00:00:00"),
                LocalDateTime.parse("2026-07-15T00:00:00"),
            ),
        )
    }

    @Test
    fun `exercise filters on the local start time of the session`() {
        // returned nothing. This filter returns the ride.
        assertEquals(
            "exercise.interval.civil_start_time >= \"2026-06-03T20:23:36\" AND " +
                "exercise.interval.civil_start_time < \"2026-06-03T20:47:49\"",
            GoogleHealthCivilFilter.civilStartTime(
                "exercise",
                LocalDateTime.parse("2026-06-03T20:23:46"),
                LocalDateTime.parse("2026-06-03T20:47:39"),
                Duration.ofSeconds(10),
            ),
        )
    }
}
