package org.radarbase.gateway.inject

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import okhttp3.OkHttpClient
import org.glassfish.jersey.internal.inject.AbstractBinder
import org.glassfish.jersey.server.ResourceConfig
import org.radarbase.jersey.auth.filter.AuthenticationFilter
import org.radarbase.jersey.auth.filter.AuthorizationFeature
import org.radarbase.jersey.config.JerseyResourceEnhancer
import java.util.concurrent.TimeUnit
import jakarta.ws.rs.ext.ContextResolver

class RadarResourceEnhancer: JerseyResourceEnhancer {

    var mapper: ObjectMapper = ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .registerModule(JavaTimeModule())
        .registerModule(KotlinModule())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    var client: OkHttpClient = OkHttpClient().newBuilder()
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
