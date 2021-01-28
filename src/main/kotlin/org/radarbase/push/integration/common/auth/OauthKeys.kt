package org.radarbase.push.integration.common.auth

data class OauthKeys(
    val consumerKey: String,
    val consumerSecret: String,
    val accessToken: String? = null,
    val accessSecret: String? = null
)
