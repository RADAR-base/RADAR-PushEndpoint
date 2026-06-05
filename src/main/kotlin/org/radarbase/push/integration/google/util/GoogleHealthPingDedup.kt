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
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class GoogleHealthPingDedup(private val ttl: Duration) {
    private val seen = ConcurrentHashMap<String, Instant>()

    fun claim(key: String): Boolean {
        val now = Instant.now()
        evictExpired(now)
        val expiresAt = now.plus(ttl)
        return seen.putIfAbsent(key, expiresAt) == null
    }

    private fun evictExpired(now: Instant) {
        seen.entries.removeIf { it.value.isBefore(now) }
    }
}
