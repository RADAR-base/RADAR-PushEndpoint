package org.radarbase.gateway.resource

import okhttp3.OkHttpClient
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.radarbase.gateway.resource.KafkaTopicsTest.Companion.call
import jakarta.ws.rs.core.Response.Status

class KafkaRootTest {
    @Test
    fun queryRoot() {
        httpClient.call(Status.OK) {
            url(BASE_URI)
        }
        httpClient.call(Status.OK) {
            url(BASE_URI)
            head()
        }
    }

    companion object {
        private lateinit var httpClient: OkHttpClient

        @BeforeAll
        @JvmStatic
        fun setUpClass() {
            httpClient = OkHttpClient()
        }

        const val BASE_URI = "http://localhost:8092/radar-gateway"
    }
}
