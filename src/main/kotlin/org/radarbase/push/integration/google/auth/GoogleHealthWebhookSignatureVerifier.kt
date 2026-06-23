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

package org.radarbase.push.integration.google.auth

import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.PublicKeyVerify
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.signature.SignatureConfig
import org.radarbase.jersey.exception.HttpInternalServerException
import org.radarbase.jersey.exception.HttpUnauthorizedException
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.GeneralSecurityException
import java.time.Duration
import java.time.Instant
import java.util.Base64

/**
 * Verifies the `GOOGLE-HEALTH-API-SIGNATURE` header that Google Health attaches to every webhook
 * data push (see https://developers.google.com/health/webhooks#signature_verification).
 */
class GoogleHealthWebhookSignatureVerifier(
    private val keysetUrl: String = DEFAULT_KEYSET_URL,
    private val refreshInterval: Duration = DEFAULT_REFRESH_INTERVAL,
    private val minFetchInterval: Duration = DEFAULT_MIN_FETCH_INTERVAL,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) {
    init {
        SignatureConfig.register()
    }

    private val lock = Any()

    @Volatile
    private var cached: CachedVerifier? = null

    @Volatile
    private var lastFetchAttempt: Instant = Instant.EPOCH

    private class CachedVerifier(val verifier: PublicKeyVerify, val fetchedAt: Instant)


    fun verify(signatureHeader: String?, body: ByteArray) {
        if (signatureHeader.isNullOrBlank()) {
            throw HttpUnauthorizedException("missing_signature", "Missing $SIGNATURE_HEADER header")
        }
        val signature = try {
            Base64.getDecoder().decode(signatureHeader.trim())
        } catch (ex: IllegalArgumentException) {
            throw HttpUnauthorizedException("invalid_signature", "$SIGNATURE_HEADER is not valid base64")
        }

        val verifier = verifier(forceRefresh = false)
            ?: throw HttpInternalServerException(
                "signature_keyset_unavailable",
                "Could not load the Google Health webhook public keyset",
            )
        if (tryVerify(verifier, signature, body)) return

        // A valid signature may come from a just-rotated key our cached keyset predates: refresh once
        // (throttled) and retry before rejecting.
        val refreshed = verifier(forceRefresh = true)
        if (refreshed != null && refreshed !== verifier && tryVerify(refreshed, signature, body)) {
            return
        }

        throw HttpUnauthorizedException(
            "invalid_signature",
            "Google Health webhook signature verification failed",
        )
    }

    private fun tryVerify(verifier: PublicKeyVerify, signature: ByteArray, body: ByteArray): Boolean =
        try {
            verifier.verify(signature, body)
            true
        } catch (ex: GeneralSecurityException) {
            false
        }

    private fun verifier(forceRefresh: Boolean): PublicKeyVerify? {
        val current = cached
        val fresh = current != null &&
            Duration.between(current.fetchedAt, Instant.now()) < refreshInterval
        if (!forceRefresh && fresh) return current!!.verifier
        return refresh(forceRefresh) ?: current?.verifier
    }

    private fun refresh(forceRefresh: Boolean): PublicKeyVerify? = synchronized(lock) {
        val now = Instant.now()
        val current = cached
        when {
            !forceRefresh && current != null &&
                Duration.between(current.fetchedAt, now) < refreshInterval -> current.verifier
            Duration.between(lastFetchAttempt, now) < minFetchInterval -> current?.verifier
            else -> {
                lastFetchAttempt = now
                try {
                    val handle: KeysetHandle = TinkJsonProtoKeysetFormat.parseKeysetWithoutSecret(fetchKeyset())
                    val verifier = handle.getPrimitive(RegistryConfiguration.get(), PublicKeyVerify::class.java)
                    cached = CachedVerifier(verifier, now)
                    logger.info("Loaded Google Health webhook public keyset from {}", keysetUrl)
                    verifier
                } catch (ex: Exception) {
                    logger.error("Failed to load Google Health webhook public keyset from {}", keysetUrl, ex)
                    current?.verifier
                }
            }
        }
    }

    private fun fetchKeyset(): String {
        val request = HttpRequest.newBuilder(URI.create(keysetUrl))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) {
            "Keyset endpoint $keysetUrl returned HTTP ${response.statusCode()}"
        }
        return response.body()
    }

    companion object {
        const val SIGNATURE_HEADER = "GOOGLE-HEALTH-API-SIGNATURE"
        const val DEFAULT_KEYSET_URL =
            "https://www.gstatic.com/googlehealthapi/webhooks/webhooks_public_keyset.json"
        private val DEFAULT_REFRESH_INTERVAL: Duration = Duration.ofHours(6)
        private val DEFAULT_MIN_FETCH_INTERVAL: Duration = Duration.ofSeconds(30)
        private val logger = LoggerFactory.getLogger(GoogleHealthWebhookSignatureVerifier::class.java)
    }
}
