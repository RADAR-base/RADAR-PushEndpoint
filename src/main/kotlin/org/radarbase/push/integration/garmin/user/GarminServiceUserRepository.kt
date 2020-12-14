package org.radarbase.push.integration.garmin.user

import org.radarbase.gateway.Config
import org.radarbase.push.integration.common.user.User
import java.io.IOException
import java.time.Instant
import java.util.stream.Stream
import javax.ws.rs.NotAuthorizedException
import javax.ws.rs.core.Context

class GarminServiceUserRepository(
    @Context private val config: Config
) : GarminUserRepository(config) {

    // TODO: Index by externalId for quick lookup
    private val cachedMap: Map<String, User> = mapOf(
        "1" to GarminUser(
            id = "1",
            projectId = "test",
            userId = "test",
            sourceId = "test",
            externalUserId = "a0015b7d-8904-40d7-8852-815cb7ad7a0b",
            isAuthorized = true,
            startDate = Instant.ofEpochSecond(1590324060),
            endDate = Instant.ofEpochSecond(1590418800),
        )
    )

    @Throws(IOException::class)
    override fun get(key: String): User? = cachedMap[key]

    @Throws(IOException::class)
    override fun stream(): Stream<out User> = cachedMap.values.stream()

    @Throws(IOException::class, NotAuthorizedException::class)
    override fun getAccessToken(user: User): String {
        return ""
    }

    @Throws(IOException::class, NotAuthorizedException::class)
    override fun getUserAccessTokenSecret(user: User): String {
        return ""
    }

    @Throws(IOException::class)
    override fun reportDeregistration(user: User) {
    }

    override fun findByExternalId(externalId: String): User {
        return super.findByExternalId(externalId)
    }
}
