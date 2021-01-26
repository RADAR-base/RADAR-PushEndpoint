package org.radarbase.push.integration.garmin.user

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class OAuth1UserCredentials(
        @JsonProperty("accessToken") var accessToken: String,
        @JsonProperty("refreshToken") var accessTokenSecret: String,
)


