package com.example.vtsdaily.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PassengerEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun passengersDao(): PassengersDao
}
