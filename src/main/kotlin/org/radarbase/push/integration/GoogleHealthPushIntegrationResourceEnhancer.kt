/*
 * Copyright 2026 King's College London
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.radarbase.push.integration

import jakarta.inject.Singleton
import org.glassfish.jersey.internal.inject.AbstractBinder
import org.glassfish.jersey.server.ResourceConfig
import org.radarbase.gateway.Config
import org.radarbase.jersey.auth.AuthValidator
import org.radarbase.jersey.enhancer.JerseyResourceEnhancer
import org.radarbase.push.integration.common.auth.DelegatedAuthValidator.Companion.GOOGLE_HEALTH_QUALIFIER
import org.radarbase.push.integration.garmin.util.RedisHolder
import org.radarbase.push.integration.garmin.util.RedisRemoteLockManager
import org.radarbase.push.integration.garmin.util.offset.OffsetRedisPersistence
import org.radarbase.push.integration.google.auth.GoogleHealthAuthValidator
import org.radarbase.push.integration.google.service.GoogleHealthApiService
import org.radarbase.push.integration.google.service.GoogleHealthBackfillService
import org.radarbase.push.integration.google.subscriptions.GoogleHealthSubscriptionReconcileService
import org.radarbase.push.integration.google.subscriptions.GoogleHealthSubscriptionService
import org.radarbase.push.integration.google.subscriber.SubscriberRegistrationService
import org.radarbase.googlehealth.user.GoogleHealthUserRepository
import org.radarbase.push.integration.google.util.GoogleServiceAccountTokenProvider
import redis.clients.jedis.JedisPool

class GoogleHealthPushIntegrationResourceEnhancer(private val config: Config) :
    JerseyResourceEnhancer {

    override fun ResourceConfig.enhance() {
        packages(
            "org.radarbase.push.integration.google.resource",
        )
    }

    override val classes: Array<Class<*>>
        get() {
            val ghConfig = config.pushIntegration.googlehealth
            val services = mutableListOf<Class<*>>(SubscriberRegistrationService::class.java)
            if (ghConfig.backfill.enabled) {
                services += GoogleHealthBackfillService::class.java
            }
            if (ghConfig.subscriptionReconcileEnabled) {
                services += GoogleHealthSubscriptionReconcileService::class.java
            }
            return services.toTypedArray()
        }

    override fun AbstractBinder.enhance() {
        bind(config.pushIntegration.googlehealth.userRepository)
            .to(GoogleHealthUserRepository::class.java)
            .named(GOOGLE_HEALTH_QUALIFIER)
            .`in`(Singleton::class.java)

        bind(GoogleHealthAuthValidator::class.java)
            .to(AuthValidator::class.java)
            .named(GOOGLE_HEALTH_QUALIFIER)
            .`in`(Singleton::class.java)

        val redisConfig = config.pushIntegration.googlehealth.backfill.redis
        val redisHolder = RedisHolder(JedisPool(redisConfig.uri))
        val offsetStore = OffsetRedisPersistence(redisHolder)
        val lockManager = RedisRemoteLockManager(redisHolder, redisConfig.lockPrefix)
        bind(redisHolder).to(RedisHolder::class.java)
        bind(offsetStore).to(OffsetRedisPersistence::class.java)
        bind(lockManager).to(RedisRemoteLockManager::class.java)

        bind(GoogleHealthApiService::class.java)
            .to(GoogleHealthApiService::class.java)
            .`in`(Singleton::class.java)

        bind(GoogleServiceAccountTokenProvider::class.java)
            .to(GoogleServiceAccountTokenProvider::class.java)
            .`in`(Singleton::class.java)

        bind(GoogleHealthSubscriptionService::class.java)
            .to(GoogleHealthSubscriptionService::class.java)
            .`in`(Singleton::class.java)
    }
}
