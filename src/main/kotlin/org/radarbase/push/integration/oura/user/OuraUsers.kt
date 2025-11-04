package org.radarbase.push.integration.oura.user

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.radarbase.oura.user.OuraUser


@JsonIgnoreProperties(ignoreUnknown = true)
data class OuraUsers(@JsonProperty("users") val users: List<OuraUser>)
