package org.radarbase.push.integration.garmin.utils

object GarminHealthData {
    const val epoch: String = """
        {
            "epochs": [
                {
                    "userId": "sub-1",
                    "userAccessToken": "dummy-access-token",
                    "summaryId": "x153a9f3-5a9478d4-6",
                    "activityType": "WALKING",
                    "activeKilocalories": 24,
                    "steps": 93,
                    "distanceInMeters": 49.11,
                    "durationInSeconds": 840,
                    "activeTimeInSeconds": 449,
                    "startTimeInSeconds": 1519679700,
                    "startTimeOffsetInSeconds": -21600,
                    "met": 3.3020337,
                    "intensity": "ACTIVE",
                    "meanMotionIntensity": 4,
                    "maxMotionIntensity": 7
                }
            ]
        }
    """
}
