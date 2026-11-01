package com.rideauto.assistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for ride history persistence.
 */
@Database(entities = [RideHistoryEntity::class], version = 5, exportSchema = false)
abstract class RideHistoryDatabase : RoomDatabase() {

    abstract fun rideHistoryDao(): RideHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: RideHistoryDatabase? = null

        fun getDatabase(context: Context): RideHistoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RideHistoryDatabase::class.java,
                    "ride_history_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
