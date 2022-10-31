/*
 * Copyright 2018 The Hyve
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.radarbase.push.integration.fitbit.request.route

import okhttp3.Response
import org.radarbase.push.integration.fitbit.converter.PayloadToSourceRecordConverter
import org.radarbase.push.integration.fitbit.converter.TopicData
import org.radarbase.push.integration.fitbit.request.FitbitRestRequest
import org.radarbase.push.integration.fitbit.request.RequestGenerator

/**
 * Single request route. This may represent e.g. a URL.
 */
interface RequestRoute : RequestGenerator {

    fun converter(): PayloadToSourceRecordConverter

    /**
     * Called when the request from this route succeeded.
     *
     * @param request non-null generated request
     * @param record  non-null resulting records
     */
    fun requestSucceeded(request: FitbitRestRequest, record: Sequence<Result<TopicData>>)
    fun requestEmpty(request: FitbitRestRequest)
    fun requestFailed(request: FitbitRestRequest, response: Response?)
}
