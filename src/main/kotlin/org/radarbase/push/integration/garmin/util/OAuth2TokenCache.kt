package org.radarbase.push.integration.garmin.util

import org.radarbase.push.integration.garmin.user.OAuth2UserCredentials
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Suppress("unused")
class OAuth2TokenCache(private val removalAdvance: Duration = Duration.ofMinutes(30)) {
    private val map = ConcurrentHashMap<String, OAuth2UserCredentials>()

    fun put(userId: String, accessToken: String, expiry: Instant) {
        map[userId] = OAuth2UserCredentials(accessToken, expiry)
    }

    fun remove(key: String) {
        map.remove(key)
    }

    /**
     * Get the value if it's still valid and not near expiry, otherwise call `fetch`,
     * store a returned pair, and return the fresh value.
     *
     * @param fetch returns the new access token and its expiry time.
     */
    fun getOrFetchToken(key: String, fetch: () -> OAuth2UserCredentials): String {
        val now = Instant.now()
        val entry = map.compute(key) { _, existing ->
            val shouldFetch = when {
                existing == null -> true
                now.isAfter(existing.expiresAt) || now == existing.expiresAt -> true
                !now.isBefore(existing.expiresAt.minus(removalAdvance)) -> true
                else -> false
            }

            if (shouldFetch) {
                fetch()
            } else {
                existing
            }
        }!!
        return entry.accessToken
    }

    fun isValidAndNotNear(key: String): Boolean {
        val e = map[key] ?: return false
        val now = Instant.now()
        return now.isBefore(e.expiresAt.minus(removalAdvance))
    }
}
