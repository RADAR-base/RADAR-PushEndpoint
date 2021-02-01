package org.radarbase.push.integration.common.auth

import okhttp3.Request
import okhttp3.RequestBody
import okio.Buffer
import okio.ByteString
import java.io.IOException
import java.net.URLEncoder
import java.security.GeneralSecurityException
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.HashMap

class Oauth1Signing(
    val oauthKeys: OauthKeys,
    val nonce: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis() / 1000L
) {
    fun getParams(): HashMap<String, String>
         = hashMapOf(
            OAUTH_CONSUMER_KEY to oauthKeys.consumerKey,
            OAUTH_NONCE to nonce,
            OAUTH_SIGNATURE_METHOD to OAUTH_SIGNATURE_METHOD_VALUE,
            OAUTH_TIMESTAMP to timestamp.toString(),
            OAUTH_VERSION to OAUTH_VERSION_VALUE
        )

    @Throws(IOException::class)
    fun signRequest(request: Request, parameters: HashMap<String, String>, signature: String): Request {
        // Insert token to parameters
        oauthKeys.accessToken?.let { parameters[OAUTH_TOKEN] = it }

        //Copy query parameters into param map
        val url = request.url
        for (i in 0 until url.querySize) {
            parameters[url.queryParameterName(i)] = url.queryParameterValue(i) ?: ""
        }

        //Copy form body into param map
        request.body?.let {
            it.asString().split('&')
                .takeIf { it.isNotEmpty() }
                ?.map { it.split('=', limit = 2) }
                ?.filter {
                    (it.size == 2).also { hasTwoParts ->
                        if (!hasTwoParts)
                            throw IllegalStateException("Key with no value: ${it.getOrNull(0)}")
                    }
                }
                ?.associate {
                    val (key, value) = it
                    key to value
                }
                ?.also { parameters.putAll(it) }
        }

        //Insert signature
        parameters[OAUTH_SIGNATURE] = signature

        //Create auth header
        val authHeader = "OAuth ${parameters.toHeaderFormat()}"
        return request.newBuilder().addHeader("Authorization", authHeader).build()
    }

    private fun RequestBody.asString() = Buffer().run {
        writeTo(this)
        readUtf8().replace("+", "%2B")
    }

    @Throws(GeneralSecurityException::class)
    private fun sign(key: String, data: String): String {
        val secretKey = SecretKeySpec(key.toBytesUtf8(), "HmacSHA1")
        val macResult = Mac.getInstance("HmacSHA1").run {
            init(secretKey)
            doFinal(data.toBytesUtf8())
        }
        return ByteString.of(*macResult).base64()
    }

    private fun String.toBytesUtf8() = this.toByteArray()

    private fun HashMap<String, String>.toHeaderFormat() =
        filterKeys { it in baseKeys }
            .entries
            .sortedBy { (key, _) -> key }
            .joinToString(", ") { (key, value) -> "$key=\"$value\"" }

    private fun String.encodeUtf8() = URLEncoder.encode(this, "UTF-8").replace("+", "%2B")

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
