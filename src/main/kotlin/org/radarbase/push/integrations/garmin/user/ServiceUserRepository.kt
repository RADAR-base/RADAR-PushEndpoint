package org.radarbase.push.integrations.garmin.user

import org.radarbase.push.integrations.common.user.User
import java.io.IOException
import java.util.stream.Stream
import javax.ws.rs.NotAuthorizedException

class ServiceUserRepository : UserRepository {

    @Throws(IOException::class)
    override fun get(key: String): User {
        TODO()
    }

    @Throws(IOException::class)
    override fun stream(): Stream<out User> {
        return Stream.empty()
    }

    @Throws(IOException::class, NotAuthorizedException::class)
    override fun getAccessToken(user: User): String {
        TODO()
    }

    @Throws(IOException::class, NotAuthorizedException::class)
    override fun getUserAccessTokenSecret(user: User): String {
        TODO()
    }

    @Throws(IOException::class)
    override fun reportDeregistration(user: User) {
    }
}
