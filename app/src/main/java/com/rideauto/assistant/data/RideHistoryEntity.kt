package com.rideauto.assistant.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for storing accepted ride history with indexing.
 */
@Entity(
    tableName = "ride_history",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["appName"]),
        Index(value = ["isCancelled"]),
        Index(value = ["appName", "isCancelled", "timestamp"]),
        Index(value = ["status", "isCancelled"])
    ]
)
data class RideHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appName: String,
    val fare: Int?,
    val distance: Float?,            // Pickup distance in km
    val tripDistance: Float? = null, // Trip / Drop distance in km
    val pickupLocation: String? = null,
    val dropLocation: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val accepted: Boolean = true,
    val method: String = "accessibility", // "pari", "shizuku", or "accessibility"
    val status: String = "ACCEPTED",      // "ACCEPTED", "CANCELLED", "COMPLETED"
    val isCancelled: Boolean = false,
    val cancellationReason: String? = null
)
