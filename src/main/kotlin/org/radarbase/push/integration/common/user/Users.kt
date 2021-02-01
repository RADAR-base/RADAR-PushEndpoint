package org.radarbase.push.integration.common.user

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.radarbase.push.integration.garmin.user.GarminUser


@JsonIgnoreProperties(ignoreUnknown = true)
data class Users(@JsonProperty("users") val users: List<GarminUser>)
