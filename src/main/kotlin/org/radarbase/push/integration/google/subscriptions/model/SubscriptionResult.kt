/*
 * Copyright 2026 King's College London
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.radarbase.push.integration.google.subscriptions.model

sealed interface SubscriptionResult {
    /** The subscription is in the desired state (created, already existed, or already deleted). */
    data class Success(val name: String?) : SubscriptionResult

    /** No service account configured, the call was not attempted. */
    object NotConfigured : SubscriptionResult

    /** Retryable failure (network, 429, 5xx). The caller should retry later. */
    data class TransientFailure(val message: String) : SubscriptionResult

    /** Non-retryable failure (e.g. 4xx other than 404/409). The caller should not blindly retry. */
    data class PermanentFailure(val code: Int, val body: String?) : SubscriptionResult
}
