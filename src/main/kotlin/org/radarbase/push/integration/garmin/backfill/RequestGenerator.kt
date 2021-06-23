package org.radarbase.push.integration.garmin.backfill

import okhttp3.Request
import okhttp3.Response
import org.radarbase.push.integration.common.user.User

interface RequestGenerator {

    fun requests(user: User, max: Int): Sequence<RestRequest>

    fun requestSuccessful(request: RestRequest, response: Response)

    fun requestFailed(request: RestRequest, response: Response)
}
