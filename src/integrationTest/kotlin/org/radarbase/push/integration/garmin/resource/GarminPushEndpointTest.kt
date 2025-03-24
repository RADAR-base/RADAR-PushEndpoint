package org.radarbase.push.integration.garmin.resource

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.nhaarman.mockitokotlin2.stub
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.radarbase.push.integration.garmin.utils.GarminHealthData
import org.radarbase.push.integration.garmin.utils.PushTestUtils.DUMMY_ACCESS_TOKEN
import org.radarbase.push.integration.garmin.utils.PushTestUtils.GARMIN_CONNECT_STUB_USERS_URL
import org.radarbase.push.integration.garmin.utils.PushTestUtils.OAUTH_CREDENTIALS_URL
import org.radarbase.push.integration.garmin.utils.PushTestUtils.TEST_USERS
import org.radarbase.push.integration.garmin.utils.PushTestUtils.USER_DE_REGISTRATION_URL
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

//        wireMockServer.stubFor(
//            WireMock.get(WireMock.urlMatching("/users.*"))
//                .withQueryParam("source-type", WireMock.equalTo("Garmin"))
//                .withQueryParam("authorized", WireMock.equalTo("true"))
//                .willReturn(
//                    WireMock.aResponse()
//                        .withStatus(200)
//                        .withBody(TEST_USERS)
//                )
//        )


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
            WireMock.delete(WireMock.urlEqualTo(USER_DE_REGISTRATION_URL))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(204)
                )
        )
    }

    @Test
    fun testEpoch() = runBlocking {
        val response = httpClient.post("http://0.0.0.0:8090/push/integrations/garmin/epochs") {
            setBody(GarminHealthData.epoch)
            contentType(ContentType.Application.Any)
        }
        println("Status is: ${response.headers}.status")
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
