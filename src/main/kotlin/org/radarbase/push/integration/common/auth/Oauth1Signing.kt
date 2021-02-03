package org.radarbase.push.integration.common.auth

import okhttp3.Request
import java.io.IOException
import kotlin.collections.HashMap

class Oauth1Signing(
    val parameters: HashMap<String, String>
) {

    @Throws(IOException::class)
    fun signRequest(request: Request): Request {
        //Create auth header
        val authHeader = "OAuth ${parameters.toHeaderFormat()}"

        return request.newBuilder().addHeader("Authorization", authHeader).build()
    }

    private fun HashMap<String, String>.toHeaderFormat() =
        filterKeys { it in baseKeys }
            .entries
            .sortedBy { (key, _) -> key }
            .joinToString(", ") { (key, value) -> "$key=\"$value\"" }

    companion object {
        private const val OAUTH_CONSUMER_KEY = "oauth_consumer_key"
        private const val OAUTH_NONCE = "oauth_nonce"
        private const val OAUTH_SIGNATURE = "oauth_signature"
        private const val OAUTH_SIGNATURE_METHOD = "oauth_signature_method"
        private const val OAUTH_SIGNATURE_METHOD_VALUE = "HMAC-SHA1"
        private const val OAUTH_TIMESTAMP = "oauth_timestamp"
        private const val OAUTH_TOKEN = "oauth_token"
        private const val OAUTH_VERSION = "oauth_version"
        private const val OAUTH_VERSION_VALUE = "1.0"

        private val baseKeys = arrayListOf(
            OAUTH_CONSUMER_KEY,
            OAUTH_NONCE,
            OAUTH_SIGNATURE,
            OAUTH_SIGNATURE_METHOD,
            OAUTH_TIMESTAMP,
            OAUTH_TOKEN,
            OAUTH_VERSION
        )
    }
}
