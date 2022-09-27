package org.radarbase.push.integration.fitbit.user

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class FitbitUsers(@JsonProperty("users") val users: List<FitbitUser>)
