package org.radarbase.push.integration.common.user

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.radarbase.push.integration.garmin.user.GarminUser
import java.util.*


class Users @JsonCreator constructor(@JsonProperty("users") users: List<GarminUser?>?) {
    private val users: List<GarminUser?>
    fun getUsers(): List<User?> {
        return users
    }

    init {
        this.users = ArrayList<GarminUser?>(users)
    }
}
