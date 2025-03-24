package org.radarbase.push.integration.garmin.utils


object PushTestUtils {
    const val WIREMOCK_PORT = 8085
    const val GARMIN_CONNECT_STUB_BASE_URL = "http://localhost:8081"
    const val GARMIN_CONNECT_STUB_USERS_URL = "/users?source-type=Garmin&authorized=true"
    const val OAUTH_CREDENTIALS_URL = "/users/1/token"
    const val USER_DE_REGISTRATION_URL = "/source-clients/Garmin/authorization/sub-1?accessToken=dummy-access-token"
    val TEST_USERS = generateUsers()
    val DUMMY_ACCESS_TOKEN = generateAccessToken()

    fun generateAccessToken(): String {
        return """
            {
                "accessToken": "dummy-access-token"
            }
            """
    }

    fun generateUsers(): String {
        return """{
        "users": [
            {
                "id": "1",
                "createdAt": "2025-03-23T12:00:00Z",
                "projectId": "radar",
                "userId": "sub-1",
                "humanReadableUserId": null,
                "sourceId": "garmin",
                "externalId": null,
                "isAuthorized": true,
                "startDate": "2024-09-23T12:00:00Z",
                "endDate": "2025-09-23T12:00:00Z",
                "version": null,
                "serviceUserId": "sub-1"
            }
        ]
    }"""
    }
}
