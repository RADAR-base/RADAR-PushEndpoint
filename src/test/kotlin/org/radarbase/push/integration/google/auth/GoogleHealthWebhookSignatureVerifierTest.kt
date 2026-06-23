package org.radarbase.push.integration.google.auth

import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.signature.PredefinedSignatureParameters
import com.google.crypto.tink.signature.SignatureConfig
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.radarbase.jersey.exception.HttpUnauthorizedException
import java.util.Base64

class GoogleHealthWebhookSignatureVerifierTest {
    private lateinit var server: MockWebServer
    private lateinit var signer: PublicKeySign
    private lateinit var verifier: GoogleHealthWebhookSignatureVerifier

    @BeforeEach
    fun setUp() {
        SignatureConfig.register()
        val privateHandle = KeysetHandle.generateNew(PredefinedSignatureParameters.ECDSA_P256)
        signer = privateHandle.getPrimitive(RegistryConfiguration.get(), PublicKeySign::class.java)
        val publicKeysetJson = TinkJsonProtoKeysetFormat.serializeKeysetWithoutSecret(
            privateHandle.publicKeysetHandle,
        )

        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody(publicKeysetJson)
        }
        server.start()

        verifier = GoogleHealthWebhookSignatureVerifier(keysetUrl = server.url("/keyset").toString())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun sign(body: ByteArray): String = Base64.getEncoder().encodeToString(signer.sign(body))

    @Test
    fun `accepts a correctly signed body`() {
        val body = """[{"data":{"healthUserId":"u1"}}]""".toByteArray()
        assertDoesNotThrow { verifier.verify(sign(body), body) }
    }

    @Test
    fun `rejects a tampered body`() {
        val body = """[{"data":{"healthUserId":"u1"}}]""".toByteArray()
        val signature = sign(body)
        val tampered = """[{"data":{"healthUserId":"attacker"}}]""".toByteArray()
        assertThrows(HttpUnauthorizedException::class.java) { verifier.verify(signature, tampered) }
    }

    @Test
    fun `rejects a missing signature header`() {
        val body = "irrelevant".toByteArray()
        assertThrows(HttpUnauthorizedException::class.java) { verifier.verify(null, body) }
    }

    @Test
    fun `rejects a non-base64 signature header`() {
        val body = "irrelevant".toByteArray()
        assertThrows(HttpUnauthorizedException::class.java) { verifier.verify("not base64!!", body) }
    }
}
