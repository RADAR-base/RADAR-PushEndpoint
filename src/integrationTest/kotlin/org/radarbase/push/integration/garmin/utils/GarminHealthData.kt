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

    const val SLEEPS = """
    {
        "sleeps": [
            {
            	"userId": "sub-1",
				"userAccessToken": "dummy-access-token",
                "summaryId": "EXAMPLE 567890",
                "calendarDate": "2016-01-10",
                "durationInSeconds": 15264,
                "startTimeInSeconds": 1452419581,
                "startTimeOffsetInSeconds": 7200,
                "unmeasurableSleepDurationInSeconds": 0,
                "deepSleepDurationInSeconds": 11231,
                "lightSleepDurationInSeconds": 3541,
                "remSleepInSeconds": 0,
                "awakeDurationInSeconds": 492,
                "sleepLevelsMap": {
                    "deep": [
                        {
                            "startTimeInSeconds": 1452419581,
                            "endTimeInSeconds": 1452478724
                        }
                    ],
                    "light": [
                        {
                            "startTimeInSeconds": 1452478725,
                            "endTimeInSeconds": 1452479725
                        },
                        {
                            "startTimeInSeconds": 1452481725,
                            "endTimeInSeconds": 1452484266
                        }
                    ]
                },
                "validation": "Device"
            },
            {
            	"userId": "sub-2",
				"userAccessToken": "dummy-access-token",
                "summaryId": "EXAMPLE 567891",
                "durationInSeconds": 11900,
                "startTimeInSeconds": 1452467493,
                "startTimeOffsetInSeconds": 7200,
                "unmeasurableSleepDurationInSeconds": 0,
                "deepSleepDurationInSeconds": 9446,
                "lightSleepDurationInSeconds": 0,
                "remSleepInSeconds": 2142,
                "awakeDurationInSeconds": 312,
                "sleepLevelsMap": {
                    "deep": [
                        {
                            "startTimeInSeconds": 1452467493,
                            "endTimeInSeconds": 1452476939
                        }
                    ],
                    "light": [
                        {
                            "startTimeInSeconds": 1452478725,
                            "endTimeInSeconds": 1452479725
                        },
                        {
                            "startTimeInSeconds": 1452481725,
                            "endTimeInSeconds": 1452484266
                        }
                    ],
                    "rem": [
                        {
                            "startTimeInSeconds": 1452476940,
                            "endTimeInSeconds": 1452479082
                        }
                    ]
                },
                "validation": "DEVICE",
                "timeOffsetSleepRespiration": {
                    "60": 15.31,
                    "120": 14.58,
                    "180": 12.73
                },
                "timeOffsetSleepSpo2": {
                    "0": 95,
                    "60": 96,
                    "120": 97,
                    "180": 93,
                    "240": 94,
                    "300": 95,
                    "360": 96
                },
                "overallSleepScore": {
                    "value": 87,
                    "qualifierKey": "GOOD"
                },
                "sleepScores": {
                    "totalDuration": {
                        "qualifierKey": "EXCELLENT"
                    },
                    "stress": {
                        "qualifierKey": "EXCELLENT"
                    },
                    "awakeCount": {
                        "qualifierKey": "FAIR"
                    },
                    "remPercentage": {
                        "qualifierKey": "FAIR"
                    },
                    "lightPercentage": {
                        "qualifierKey": "GOOD"
                    },
                    "deepPercentage": {
                        "qualifierKey": "POOR"
                    }
                }
            }
        ]
    }
"""

    const val BODY_COMPS = """{
    "bodyComps": [
        {
            "userId": "sub-1",
			"userAccessToken": "dummy-access-token",
            "summaryId": "EXAMPLE_678901",
            "measurementTimeInSeconds": 1439741130,
            "measurementTimeOffsetInSeconds": 0,
            "muscleMassInGrams": 25478,
            "boneMassInGrams": 2437,
            "bodyWaterInPercent": 59.4,
            "bodyFatInPercent": 17.1,
            "bodyMassIndex": 23.2,
            "weightInGrams": 75450
        },
        {
            "userId": "sub-2",
			"userAccessToken": "dummy-access-token",
            "summaryId": "EXAMPLE_678902",
            "measurementTimeInSeconds": 1439784330,
            "measurementTimeOffsetInSeconds": 0,
            "muscleMassInGrams": 25482,
            "boneMassInGrams": 2434,
            "bodyWaterInPercent": 59.8,
            "bodyFatInPercent": 17.3,
            "bodyMassIndex": 23.1,
            "weightInGrams": 75173
        }
    ]
}
"""

    const val STRESS = """
{
    "stressDetails": [
        {
            "userId": "sub-1",
            "userAccessToken": "dummy-access-token",
            "summaryId": "EXAMPLE_6789124",
            "startTimeInSeconds": 1490245200,
            "calendarDate": "2017-03-23",
            "startTimeOffsetInSeconds": 0,
            "durationInSeconds": 540,
            "timeOffsetStressLevelValues": {
                "0": 18,
                "180": 51,
                "360": 28,
                "540": 29
            },
            "timeOffsetBodyBatteryValues": {
                "0": 55,
                "180": 56,
                "360": 59
            }
        }
    ]
}
"""

    const val USER_METRICS = """
    {
        "userMetrics": [
            {
                "userId": "sub-1",
                "userAccessToken": "dummy-access-token",
                "summaryId": "EXAMPLE 843244",
                "calendarDate": "2017-03-23",
                "vo2Max": 48.0,
                "enhanced": true,
                "fitnessAge": 32
            }
        ]
    }
    """

    const val PULSE_OX = """
    {
        "pulseox": [
            {
                "userId": "sub-1",
                "userAccessToken": "dummy-access-token",
                "summaryId": "example1234-spo2OnDemand",
                "calendarDate": "2018-08-27",
                "startTimeInSeconds": 1572303600,
                "timeOffsetSpo2Values": {
                    "55740": 93
                },
                "durationInSeconds": 0,
                "startTimeOffsetInSeconds": 3600,
                "onDemand": true
            }
        ]
    }
    """

    const val RESPIRATION = """
        {
            "allDayRespiration": [
                {
                    "userId": "sub-1",
                    "userAccessToken": "dummy-access-token",
                    "summaryId": "x15372ea-5d7866b4",
                    "startTimeInSeconds": 1568171700,   
                    "durationInSeconds": 900,   
                    "startTimeOffsetInSeconds": -18000, 
                    "timeOffsetEpochToBreaths": {
                     "0": 14.63, 
                     "60": 14.4, 
                     "120": 14.38, 
                     "180": 14.38, 
                     "300": 17.1, 
                     "540": 16.61, 
                     "600": 16.14, 
                     "660": 14.59, 
                     "720": 14.65, 
                     "780": 15.09, 
                     "840": 14.88 
                        }
                }
            ]
        }
    """

    const val HEALTH_SNAPSHOT = """
{
    "healthSnapshot": [
        {
            "userId": "sub-1",
            "userAccessToken": "dummy-access-token",
            "summaryId": "x42f72c9-612e11dae53d462a-0b98-4ae8-9fdc-28f392a1cd8078",
            "calendarDate": "2021-08-31",
            "startTimeInSeconds": 1630409178,
            "durationInSeconds": 120,
            "offsetStartTimeInSeconds": 7200,
            "summaries": [
                {
                    "summaryType": "heart_rate",
                    "minValue": 78.0,
                    "maxValue": 87.0,
                    "avgValue": 83.0,
                    "epochSummaries": {
                        "0": 84.0,
                        "1": 84.0,
                        "2": 83.0,
                        "3": 83.0,
                        "4": 83.0,
                        "5": 84.0
                    }
                },
                {
                    "summaryType": "respiration",
                    "minValue": 13.45,
                    "maxValue": 15.32,
                    "avgValue": 14.49,
                    "epochSummaries": {
                        "0": 15.32,
                        "1": 15.32,
                        "2": 15.32,
                        "3": 15.32,
                        "4": 15.09,
                        "5": 15.09,
                        "115": 13.86,
                        "116": 13.86,
                        "117": 14.30,
                        "118": 15.23,
                        "119": 15.23,
                        "120": 15.32
                        }
                    }
                ]
            }
         ]
        }
    """

    const val HRV = """
{
    "hrv": [
        {
            "userId": "sub-1",
            "userAccessToken": "dummy-access-token",
            "summaryId": "x473db21-6295abc4",
            "calendarDate": "2022-05-31",
            "lastNightAvg": 44,
            "lastNight5MinHigh": 72,
            "startTimeOffsetInSeconds": -18000,
            "durationInSeconds": 3820,
            "startTimeInSeconds": 1653976004,
            "hrvValues": {
                "300": 32,
                "600": 24,
                "900": 31,
                "1200": 35,
                "1500": 39,
                "1800": 47,
                "2100": 32,
                "2400": 24,
                "2700": 31,
                "3000": 35,
                "3300": 39,
                "3600": 47
            }
        }
    ]
}
"""
    const val BLOOD_PRESSURE = """
{
    "bloodPressures": [
        {
            "userId": "sub-1",
            "userAccessToken": "dummy-access-token",
            "summaryId": "x473db21-632b3500",
            "systolic": 120,
            "diastolic": 110,
            "pulse": 82,
            "sourceType": "MANUAL",
            "startTimeInSeconds": 1519679700,
            "measurementTimeInSeconds": 1663776000,
            "measurementTimeOffsetInSeconds": -18000
        }
    ]
}
"""

    const val MOVE_IQ = """
{
    "moveIQActivities": [
        {
            "userId": "sub-1",
            "userAccessToken": "dummy-access-token",
            "summaryId": "EXAMPLE_843244",
            "calendarDate": "2017-03-23",
            "startTimeInSeconds": 1490245200,
            "durationInSeconds": 738,
            "offsetInSeconds": 0,
            "activityType": "Running",
            "activitySubType": "Hurdles"
        }
    ]
}
"""

    const val ACTIVITIES = """
{
    "activities": [
        {
            "userId": "sub-1",
            "userAccessToken": "dummy-access-token",
            "summaryId": "5001968355",
            "activityId": 5001968355,
            "activityType": "RUNNING",
            "startTimeInSeconds": 1452470400,
            "startTimeOffsetInSeconds": 0,
            "durationInSeconds": 11580,
            "averageSpeedInMetersPerSecond": 2.888999938964844,
            "distanceInMeters": 519818.125,
            "activeKilocalories": 448,
            "deviceName": "Forerunner 910XT",
            "averagePaceInMinutesPerKilometer": 0.5975272352046997
        },
        {
            "userId": "sub-2",
            "userAccessToken": "dummy-access-token",
            "summaryId": "5001968355",
            "activityId": 5001968355,
            "activityType": "CYCLING",
            "startTimeInSeconds": 1452506094,
            "startTimeOffsetInSeconds": 0,
            "durationInSeconds": 1824,
            "averageSpeedInMetersPerSecond": 8.75,
            "distanceInMeters": 4322.357,
            "activeKilocalories": 360,
            "deviceName": "Forerunner 910XT"
        }
    ]
}
"""

    const val ACTIVITY_DETAILS = """
{
    "activityDetails": [
        {
            "userId": "sub-1",
            "userAccessToken": "dummy-access-token",
            "summaryId": "5001968355-detail",
            "activityId": 5001968355,
            "summary": {
                "durationInSeconds": 1789,
                "startTimeInSeconds": 1512234126,
                "startTimeOffsetInSeconds": -25200,
                "activityType": "RUNNING",
                "averageHeartRateInBeatsPerMinute": 144,
                "averageRunCadenceInStepsPerMinute": 84.0,
                "averageSpeedInMetersPerSecond": 2.781,
                "averagePaceInMinutesPerKilometer": 15.521924,
                "activeKilocalories": 367,
                "deviceName": "forerunner935",
                "distanceInMeters": 4976.83,
                "maxHeartRateInBeatsPerMinute": 159,
                "maxPaceInMinutesPerKilometer": 10.396549,
                "maxRunCadenceInStepsPerMinute": 106.0,
                "maxSpeedInMetersPerSecond": 4.152,
                "startingLatitudeInDegree": 51.053232522681355,
                "startingLongitudeInDegree": -114.06880217604339,
                "steps": 5022,
                "totalElevationGainInMeters": 16.0,
                "totalElevationLossInMeters": 22.0
            },
            "samples": [
                {
                    "startTimeInSeconds": 1669313992,
                    "latitudeInDegree": 38.832325832918286,
                    "longitudeInDegree": -94.74890395067632,
                    "elevationInMeters": 314.0,
                    "heartRate": 108,
                    "speedMetersPerSecond": 1.3250000476837158,
                    "totalDistanceInMeters": 1903.4200439453125,
                    "timerDurationInSeconds": 1460,
                    "clockDurationInSeconds": 1460,
                    "movingDurationInSeconds": 1379
                },
                {
                    "startTimeInSeconds": 1669314001,
                    "latitudeInDegree": 38.832390792667866,
                    "longitudeInDegree": -94.74878308363259,
                    "elevationInMeters": 314.20001220703125,
                    "heartRate": 109,
                    "speedMetersPerSecond": 1.315999984741211,
                    "totalDistanceInMeters": 1916.18994140625,
                    "timerDurationInSeconds": 1469,
                    "clockDurationInSeconds": 1469,
                    "movingDurationInSeconds": 1388
                }
            ],
            "laps": [
                {
                    "startTimeInSeconds": 1512234126
                },
                {
                    "startTimeInSeconds": 1512234915
                }
            ]
        }
    ]
}
"""
}
