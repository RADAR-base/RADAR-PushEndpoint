package org.radarbase.gateway.resource

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.runners.model.MultipleFailureException
import org.radarbase.config.ServerConfig
import org.radarbase.data.AvroRecordData
import org.radarbase.gateway.resource.KafkaRootTest.Companion.BASE_URI
import org.radarbase.gateway.resource.KafkaTopics.Companion.ACCEPT_AVRO_V2_JSON
import org.radarbase.jersey.auth.filter.AuthenticationFilter.Companion.BEARER
import org.radarbase.producer.AuthenticationException
import org.radarbase.producer.rest.RestClient
import org.radarbase.producer.rest.RestSender
import org.radarbase.producer.rest.SchemaRetriever
import org.radarbase.topic.AvroTopic
import org.radarcns.kafka.ObservationKey
import org.radarcns.passive.phone.PhoneAcceleration
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response.Status

class KafkaTopicsTest {
    private fun requestAccessToken(): String {
        val clientToken = httpClient.call( Status.OK, "access_token") {
            url("${MANAGEMENTPORTAL_URL}/oauth/token")
            addHeader("Authorization", Credentials.basic(MP_CLIENT, ""))
            post(FormBody.Builder()
                    .add("username", ADMIN_USER)
                    .add("password", ADMIN_PASSWORD)
                    .add("grant_type", "password")
                    .build())
        }

        val tokenUrl = httpClient.call(Status.OK, "tokenUrl") {
            addHeader("Authorization", BEARER + clientToken)
            url(MANAGEMENTPORTAL_URL.toHttpUrl()
                    .newBuilder("api/oauth-clients/pair")!!
                    .addEncodedQueryParameter("clientId", REST_CLIENT)
                    .addEncodedQueryParameter("login", USER)
                    .addEncodedQueryParameter("persistent", "false")
                    .build())
        }

        val refreshToken = httpClient.call(Status.OK, "refreshToken") {
            url(tokenUrl)
        }

        return httpClient.call(Status.OK, "access_token") {
            url("${MANAGEMENTPORTAL_URL}/oauth/token")
            addHeader("Authorization", Credentials.basic(REST_CLIENT, ""))
            post(FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken)
                    .build())
        }
    }

    @Test
    fun testListTopics() {
        val accessToken = requestAccessToken()

        val retriever = SchemaRetriever(ServerConfig(URL(SCHEMA_REGISTRY_URL)), 10)

        val topic = AvroTopic("test",
                ObservationKey.getClassSchema(), PhoneAcceleration.getClassSchema(),
                ObservationKey::class.java, PhoneAcceleration::class.java)

        val time = System.currentTimeMillis() / 1000.0
        val key = ObservationKey(PROJECT, USER, SOURCE)
        val value = PhoneAcceleration(time, time, 0.1f, 0.1f, 0.1f)

        // initialize topic and schema
        sendData(REST_PROXY_URL, retriever, topic, accessToken, key, value, binary = false, gzip = false)

        println("Initialized kafka brokers")

        Thread.sleep(2000)

        try {
            testTopicList(accessToken)
        } catch (ex: AuthenticationException) {
            // try again
            testTopicList(accessToken)
        }
        val results = mutableListOf<String>()
        sendData(BASE_URI, retriever, topic, accessToken, key, value, binary = true, gzip = true)
        results += sendData(BASE_URI, retriever, topic, accessToken, key, value, binary = true, gzip = true)
        results += sendData(BASE_URI, retriever, topic, accessToken, key, value, binary = true, gzip = false)
        results += sendData(BASE_URI, retriever, topic, accessToken, key, value, binary = false, gzip = true)
        results += sendData(BASE_URI, retriever, topic, accessToken, key, value, binary = false, gzip = false)
        results.forEach { println(it) }


        httpClient.call(Status.OK) {
            url("$BASE_URI/topics")
            head()
            addHeader("Authorization", BEARER + accessToken)
        }

        httpClient.call(Status.UNAUTHORIZED) {
            url("$BASE_URI/topics")
        }

        httpClient.call(Status.UNAUTHORIZED) {
            url("$BASE_URI/topics").head()
        }

        httpClient.call(Status.UNSUPPORTED_MEDIA_TYPE) {
            url("$BASE_URI/topics/test")
            post("{}".toRequestBody(JSON_TYPE))
            addHeader("Authorization", BEARER + accessToken)
        }

        httpClient.call(422) {
            url("$BASE_URI/topics/test")
            post("{}".toRequestBody(KAFKA_JSON_TYPE))
            addHeader("Authorization", BEARER + accessToken)
        }

        httpClient.call(Status.BAD_REQUEST) {
            url("$BASE_URI/topics/test")
            post("".toRequestBody(KAFKA_JSON_TYPE))
            addHeader("Authorization", BEARER + accessToken)
        }
    }

    private fun testTopicList(accessToken: String) {
        val gatewayTopicList = httpClient.call(Status.OK) {
            url("$BASE_URI/topics")
            addHeader("Authorization", BEARER + accessToken)
        }!!.elements().asSequence().mapTo(ArrayList()) { it.asText() }

        assertThat(gatewayTopicList, hasItem("test"))
    }

    private class CallableThread(runnable: () -> Unit): Thread(runnable) {
        @Volatile
        var exception: Exception? = null

        override fun run() {
            try {
                super.run()
            } catch (ex: Exception) {
                exception = ex
            }
        }
    }

    private fun sendData(url: String, retriever: SchemaRetriever, topic: AvroTopic<ObservationKey, PhoneAcceleration>, accessToken: String, key: ObservationKey, value: PhoneAcceleration, binary: Boolean, gzip: Boolean): String {
        val totalSize = LongAdder()

        val restClient = RestClient.newClient()
                .also { client ->
                    if (SHOW_DATA_SIZE) {
                        client.httpClientBuilder()
                                .addNetworkInterceptor { chain ->
                                    val request = chain.request()
                                    request.body?.let { body ->
                                        val sink = Buffer()
                                        body.writeTo(sink)
                                        totalSize.add(sink.size)
                                    }
                                    chain.proceed(request)
                                }
                    }
                }
                .server(ServerConfig(url))
                .timeout(10, TimeUnit.SECONDS)
                .gzipCompression(gzip)
                .build()

        val sender = RestSender.Builder()
                .httpClient(restClient)
                .schemaRetriever(retriever)
                .useBinaryContent(binary)
                .addHeader("Authorization", BEARER + accessToken)
                .build()

        val numRequests = LongAdder()
        val numTime = LongAdder()
        val recordData = AvroRecordData(topic, key, List(1000) { value })
        val timeStart = System.nanoTime()
        val senders = List(NUM_THREADS) {
            CallableThread {
                repeat(NUM_SENDS) {
                    sender.sender(topic).use {
                        val timeRequestStart = System.nanoTime()
                        it.send(recordData)
                        numRequests.increment()
                        numTime.add(System.nanoTime() - timeRequestStart)
                    }
                }

                assertThat(sender.isConnected, `is`(true))
            }
        }
        senders.forEach { it.start() }
        senders.forEach { it.join() }
        senders.mapNotNull { it.exception }
                .takeIf { it.isNotEmpty() }
                ?.let { throw MultipleFailureException(it) }

        val timeEnd = System.nanoTime()

        val dataSize = if (SHOW_DATA_SIZE) (totalSize.sum() / (NUM_SENDS * NUM_THREADS)).toString() else "not measured"
        val timePerRequest = numTime.sum() / (numRequests.sum() * 1_000_000)
        val totalTime = (timeEnd - timeStart) / 1_000_000_000.0

        return """
            =============================================
            url: $url, binary: $binary, gzip: $gzip
            Time per request $timePerRequest milliseconds
            Time to send data: $totalTime seconds
            Data size: $dataSize
        """.trimIndent()
    }

    companion object {
        private lateinit var httpClient: OkHttpClient

        @BeforeAll
        @JvmStatic
        fun setUpClass() {
            httpClient = OkHttpClient()
        }

        private const val MANAGEMENTPORTAL_URL = "http://localhost:8080"
        private const val SCHEMA_REGISTRY_URL = "http://localhost:8081/"
        private const val REST_PROXY_URL = "http://localhost:8082/"
        private const val NUM_THREADS = 15
        private const val NUM_SENDS = 1
        private const val SHOW_DATA_SIZE = false
        const val MP_CLIENT = "ManagementPortalapp"
        const val REST_CLIENT = "pRMT"
        const val USER = "sub-1"
        const val PROJECT = "radar"
        const val SOURCE = "03d28e5c-e005-46d4-a9b3-279c27fbbc83"
        const val ADMIN_USER = "admin"
        const val ADMIN_PASSWORD = "admin"
        private val JSON_TYPE = APPLICATION_JSON.toMediaType()
        private val KAFKA_JSON_TYPE = ACCEPT_AVRO_V2_JSON.toMediaType()

        fun OkHttpClient.call(expectedStatus: Int, requestSupplier: Request.Builder.() -> Unit): JsonNode? {
            val request = Request.Builder().apply(requestSupplier).build()
            return newCall(request).execute().use { response ->
                println("${request.method} ${request.url}")
                assertThat(response.code, `is`(expectedStatus))
                println(response.headers)
                response.body?.let { responseBody ->
                    val string = responseBody.string()
                    println(string)
                    mapper.readTree(string)
                }
            }
        }

        private val mapper = ObjectMapper()

        fun OkHttpClient.call(expectedStatus: Status, requestSupplier: Request.Builder.() -> Unit): JsonNode? {
            return call(expectedStatus.statusCode, requestSupplier)
        }

        fun OkHttpClient.call(expectedStatus: Status, stringProperty: String, requestSupplier: Request.Builder.() -> Unit): String {
            return call(expectedStatus, requestSupplier)?.get(stringProperty)?.asText()
                    ?: throw AssertionError("String property $stringProperty not found")
        }
    }
}
