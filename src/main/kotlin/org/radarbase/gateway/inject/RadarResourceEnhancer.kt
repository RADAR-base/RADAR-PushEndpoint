package org.radarbase.gateway.inject

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import jakarta.ws.rs.ext.ContextResolver
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.glassfish.jersey.internal.inject.AbstractBinder
import org.glassfish.jersey.server.ResourceConfig
import org.radarbase.jersey.auth.filter.AuthenticationFilter
import org.radarbase.jersey.auth.filter.AuthorizationFeature
import org.radarbase.jersey.enhancer.JerseyResourceEnhancer
import java.util.concurrent.TimeUnit

class RadarResourceEnhancer: JerseyResourceEnhancer {

    var mapper: ObjectMapper = ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .registerModule(JavaTimeModule())
        .registerModule(KotlinModule())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    val audienceInterceptor = Interceptor { chain ->
        var req = chain.request()
        if (req.method.equals("POST", ignoreCase = true) && req.body is FormBody) {
            val oldBody = req.body as FormBody
            val newBodyBuilder = FormBody.Builder()
            for (i in 0 until oldBody.size) {
                newBodyBuilder.addEncoded(oldBody.encodedName(i), oldBody.encodedValue(i))
            }
            newBodyBuilder.add("audience", "res_restAuthorizer")
            req = req.newBuilder().post(newBodyBuilder.build()).build()
        }
        chain.proceed(req)
    }

    var client: OkHttpClient = OkHttpClient().newBuilder()
        .addInterceptor(audienceInterceptor)
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override val classes = arrayOf(
        AuthenticationFilter::class.java,
        AuthorizationFeature::class.java)

    override fun ResourceConfig.enhance() {
        register(ContextResolver { mapper })
    }

    override fun AbstractBinder.enhance() {

        bind(client)
            .to(OkHttpClient::class.java)

        bind(mapper)
            .to(ObjectMapper::class.java)
    }
}
