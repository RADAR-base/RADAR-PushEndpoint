package org.radarbase.gateway.exception

import javax.ws.rs.core.Response.Status

class GatewayTimeoutException(message: String) : HttpApplicationException(Status.GATEWAY_TIMEOUT, "gateway_timeout", message)
