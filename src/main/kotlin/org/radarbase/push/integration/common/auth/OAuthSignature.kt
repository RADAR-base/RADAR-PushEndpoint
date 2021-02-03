package org.radarbase.push.integration.common.auth

import com.fasterxml.jackson.annotation.JsonProperty

class OAuthSignature(
    @JsonProperty("url") val url: String,
    @JsonProperty("signedUrl") val signedUrl: String
)
