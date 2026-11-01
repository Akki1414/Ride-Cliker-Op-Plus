package com.rideauto.assistant.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for ride history.
 */
@Dao
interface RideHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRide(ride: RideHistoryEntity): Long

    @Query("SELECT * FROM ride_history ORDER BY timestamp DESC")
    fun getAllRides(): Flow<List<RideHistoryEntity>>

    @Query("SELECT * FROM ride_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentRides(limit: Int = 50): Flow<List<RideHistoryEntity>>

    @Query("SELECT SUM(fare) FROM ride_history WHERE (status = 'ACCEPTED' OR accepted = 1) AND isCancelled = 0 AND fare IS NOT NULL")
    fun getTotalEarnings(): Flow<Int?>

    @Query("SELECT COUNT(*) FROM ride_history WHERE (status = 'ACCEPTED' OR accepted = 1) AND isCancelled = 0")
    fun getAcceptedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM ride_history WHERE isCancelled = 1 OR status = 'CANCELLED'")
    fun getCancelledCount(): Flow<Int>

    @Query("UPDATE ride_history SET status = 'CANCELLED', isCancelled = 1, cancellationReason = :reason WHERE id = :id")
    suspend fun updateRideCancelled(id: Long, reason: String? = "Cancelled by Customer")

    @Query("SELECT * FROM ride_history WHERE appName = :appName AND isCancelled = 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestActiveRide(appName: String): RideHistoryEntity?

    @Query("DELETE FROM ride_history WHERE id NOT IN (SELECT id FROM ride_history ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun pruneOldRides(keepCount: Int = 100)

    @Query("DELETE FROM ride_history WHERE appName LIKE '%systemui%' OR appName LIKE '%android%'")
    suspend fun deleteDirtySystemUiEntries()

    @Query("DELETE FROM ride_history WHERE (appName LIKE '%systemui%' OR appName LIKE '%android%') OR (fare IS NULL AND distance IS NULL AND tripDistance IS NULL AND (pickupLocation IS NULL OR pickupLocation = '' OR pickupLocation = 'Pickup Location'))")
    suspend fun deleteCorruptedHistoryEntries()

    @Query("DELETE FROM ride_history")
    suspend fun clearHistory()
}
