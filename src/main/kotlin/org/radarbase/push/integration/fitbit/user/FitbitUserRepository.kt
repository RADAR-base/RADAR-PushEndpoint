package org.radarbase.push.integration.fitbit.user

import org.radarbase.push.integration.common.user.User
import org.radarbase.push.integration.common.user.UserRepository

class FitbitUserRepository : UserRepository {
    override fun get(key: String): User? {
        TODO("Not yet implemented")
    }

    override fun stream(): Sequence<User> {
        TODO("Not yet implemented")
    }

    override fun getAccessToken(user: User): String {
        TODO("Not yet implemented")
    }

    override fun getRefreshToken(user: User): String {
        TODO("Not yet implemented")
    }

    override fun hasPendingUpdates(): Boolean {
        TODO("Not yet implemented")
    }

    override fun applyPendingUpdates() {
        TODO("Not yet implemented")
    }
}