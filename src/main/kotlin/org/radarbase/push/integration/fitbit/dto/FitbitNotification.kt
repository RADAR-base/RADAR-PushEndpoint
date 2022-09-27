package org.radarbase.push.integration.fitbit.dto

data class FitbitNotification(
    val collectionType: String,
    val date: String,
    val ownerId: String,
    val ownerType: String,
    val subscriptionId: String,
)
