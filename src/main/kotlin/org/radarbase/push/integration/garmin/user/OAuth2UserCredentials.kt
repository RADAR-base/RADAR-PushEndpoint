package org.radarbase.push.integration.garmin.user

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class OAuth2UserCredentials(
    @param:JsonProperty("accessToken") val accessToken: String,
    @param:JsonProperty("expiresAt") val expiresAt: Instant
)
