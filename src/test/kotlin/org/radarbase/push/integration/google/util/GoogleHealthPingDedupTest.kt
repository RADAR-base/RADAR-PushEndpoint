package org.radarbase.push.integration.google.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class GoogleHealthPingDedupTest {

    @Test
    fun `claim returns true first time and false while TTL is active`() {
        val dedup = GoogleHealthPingDedup(Duration.ofMinutes(5))
        assertTrue(dedup.claim("user-1:2026-04-08T01:00"))
        assertFalse(dedup.claim("user-1:2026-04-08T01:00"))
    }

    @Test
    fun `claim admits different keys independently`() {
        val dedup = GoogleHealthPingDedup(Duration.ofMinutes(5))
        assertTrue(dedup.claim("user-1:2026-04-08T01:00"))
        assertTrue(dedup.claim("user-2:2026-04-08T01:00"))
        assertTrue(dedup.claim("user-1:2026-04-08T02:00"))
    }
}
