package org.radarbase.push.integration.garmin.resource

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.radarbase.push.integration.garmin.utils.GarminHealthData
import org.radarbase.push.integration.garmin.utils.PushTestUtils.DUMMY_ACCESS_TOKEN
import org.radarbase.push.integration.garmin.utils.PushTestUtils.GARMIN_CONNECT_STUB_USERS_URL
import org.radarbase.push.integration.garmin.utils.PushTestUtils.GARMIN_PUSH_DAILIES_PATH
import org.radarbase.push.integration.garmin.utils.PushTestUtils.GARMIN_PUSH_EPOCHS_PATH
import org.radarbase.push.integration.garmin.utils.PushTestUtils.GARMIN_PUSH_SLEEPS_PATH
import org.radarbase.push.integration.garmin.utils.PushTestUtils.OAUTH_CREDENTIALS_URL
import org.radarbase.push.integration.garmin.utils.PushTestUtils.OAUTH_CREDENTIALS_URL_SECOND
import org.radarbase.push.integration.garmin.utils.PushTestUtils.TEST_USERS
import org.radarbase.push.integration.garmin.utils.PushTestUtils.USER_DE_REGISTRATION_URL
import org.radarbase.push.integration.garmin.utils.PushTestUtils.USER_DE_REGISTRATION_URL_SECOND
import org.radarbase.push.integration.garmin.utils.PushTestUtils.WIREMOCK_PORT

class GarminPushEndpointTest {

    @BeforeEach
    fun setUp() {
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlEqualTo(GARMIN_CONNECT_STUB_USERS_URL))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withBody(TEST_USERS)
                )
        )

        wireMockServer.stubFor(
            WireMock.get(WireMock.urlEqualTo(OAUTH_CREDENTIALS_URL))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(DUMMY_ACCESS_TOKEN)
                )
        )

        wireMockServer.stubFor(
            WireMock.get(WireMock.urlEqualTo(OAUTH_CREDENTIALS_URL_SECOND))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(DUMMY_ACCESS_TOKEN)
                )
        )

        wireMockServer.stubFor(
            WireMock.delete(WireMock.urlEqualTo(USER_DE_REGISTRATION_URL))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(204)
                )
        )

        wireMockServer.stubFor(
            WireMock.delete(WireMock.urlEqualTo(USER_DE_REGISTRATION_URL_SECOND))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(204)
                )
        )
    }

    @Test
    fun testEpoch() = runBlocking {
        val response = httpClient.post(GARMIN_PUSH_EPOCHS_PATH) {
            setBody(GarminHealthData.EPOCH)
            contentType(ContentType.Application.Any)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testDailies() = runBlocking {
        val response = httpClient.post(GARMIN_PUSH_DAILIES_PATH) {
            setBody(GarminHealthData.DAILIES)
            contentType(ContentType.Application.Any)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testSleeps() = runBlocking {
        val response = httpClient.post(GARMIN_PUSH_SLEEPS_PATH) {
            setBody(GarminHealthData.SLEEPS)
            contentType(ContentType.Application.Any)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }


    companion object {
        private lateinit var httpClient: HttpClient
        private lateinit var wireMockServer: WireMockServer

        @BeforeAll
        @JvmStatic
        fun setUpClientAndServer() {
            wireMockServer = WireMockServer(
                WireMockConfiguration()
                    .port(WIREMOCK_PORT)
            )
            wireMockServer.start()

            httpClient = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            coerceInputValues = true
                        },
                    )
                }
            }
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            wireMockServer.stop()
            httpClient.close()
        }
    }
}
