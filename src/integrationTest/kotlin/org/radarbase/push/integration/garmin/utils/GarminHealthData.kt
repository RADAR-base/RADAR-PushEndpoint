package org.radarbase.push.integration.garmin.utils

object GarminHealthData {
    const val EPOCH: String = """
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

    const val DAILIES: String = """
	    { 
		    "dailies": [
			    {
				    "userId": "sub-1",
				    "userAccessToken": "dummy-access-token",
				    "summaryId": "EXAMPLE 67891",
				    "calendarDate": "2016-01-11",
				    "activityType": "WALKING",
				    "activeKilocalories": 321,
				    "bmrKilocalories": 1731,
				    "consumedCalories": 1121,
				    "steps": 4210,
			       	"distanceInMeters": 3146.5,
			    	"durationInSeconds": 86400,
			    	"activeTimeInSeconds": 12240,
			    	"startTimeInSeconds": 1452470400,
			    	"startTimeOffsetInSeconds": 3600,
			    	"moderateIntensityDurationInSeconds": 81870,
			    	"vigorousIntensityDurationInSeconds": 4530,
			    	"floorsClimbed": 8,
			    	"minHeartRateInBeatsPerMinute": 59,
			    	"averageHeartRateInBeatsPerMinute": 64,
			    	"maxHeartRateInBeatsPerMinute": 112,
			    	"timeOffsetHeartRateSamples": {
				    	"15": 75,
					    "30": 75,
					    "3180": 76,
					    "3195": 65,
					    "3210": 65,
					    "3225": 73,
					    "3240": 74,
					    "3255": 74
				    },
				    "averageStresslevel": 43,
				    "maxStressLevel": 87,
				    "stressDurationInSeconds": 13620,
				    "restStressDurationInSeconds": 7600,
				    "activityStressDurationInSeconds": 3450,
				    "lowStressDurationInSeconds": 6700,
				    "mediumStressDurationInSeconds": 4350,
				    "highStressDurationInSeconds": 108000,
				    "stressQualifier": "stressful_awake",
			    	"stepsGoal": 4500,
			    	"netKilocaloriesGoal": 2010,
			    	"intensityDurationGoalInSeconds": 1500,
			    	"floorsClimbedGoal": 18
		    	},
		    	{
		    		"userId": "sub-2",
		    		"userAccessToken": "dummy-access-token",
		    		"summaryId": "EXAMPLE 67892",
		    		"activityType": "WALKING",
		    		"activeKilocalories": 304,
		    		"bmrKilocalories": 1225,
		    		"consumedCalories": 1926,
		    		"steps": 3305,
		    		"distanceInMeters": 2470.1,
			    	"durationInSeconds": 86400,
			    	"activeTimeInSeconds": 7,
			    	"startTimeInSeconds": 1452556800,
			    	"startTimeOffsetInSeconds": 3600,
			    	"moderateIntensityDurationInSeconds": 83160,
			    	"vigorousIntensityDurationInSeconds": 3240,
			    	"floorsClimbed": 5,
			    	"minHeartRateInBeatsPerMinute": 62,
			    	"averageHeartRateInBeatsPerMinute": 67,
			    	"maxHeartRateInBeatsPerMinute": 122,
			    	"restingHeartRateInBeatsPerMinute": 64,
			    	"timeOffsetHeartRateSamples": {
			    		"15": 77,
			    		"30": 72,
			    		"3180": 71,
			    		"3195": 67,
				    	"3210": 62,
				    	"3225": 65,
				    	"3240": 71,
				    	"3255": 81
				    },
				    "averageStresslevel": 37,
		    		"maxStressLevel": 95,
		    		"stressDurationInSeconds": 19080,
		    		"restStressDurationInSeconds": 2700,
		    		"activityStressDurationInSeconds": 7260,
		    		"lowStressDurationInSeconds": 7800,
			    	"mediumStressDurationInSeconds": 8280,
		    		"highStressDurationInSeconds": 3000,
			    	"stressQualifier": "stressful_awake",
		    		"stepsGoal": 5000,
		    		"netKilocaloriesGoal": 2170,
		    		"intensityDurationGoalInSeconds": 1800,
		    		"floorsClimbedGoal": 20
		    	}
	    	]
    	}
    """

}
