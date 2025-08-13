package com.example.vtsdaily.data // adjust to your package name

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PassengersDao {
    @Query("""
        SELECT ID AS id,  DriveDate AS driveDate, Phone AS phone, Passenger AS passenger
        FROM passengers
        ORDER BY passenger 
    """)
    fun all(): Flow<List<PassengerEntity>>

    @Query("""
        SELECT ID AS id,  Phone AS phone, Passenger AS passenger
        FROM passengers
        WHERE Phone IS NOT NULL AND TRIM(Phone) <> ''
        ORDER BY passenger
    """)
    fun withPhone(): Flow<List<PassengerEntity>>

    @Query("""
        SELECT ID AS id, DriveDate AS driveDate, Phone AS phone, Passenger AS passenger
        FROM passengers
        WHERE DriveDate = :date
        ORDER BY "PUTimeAppt"
    """)
    fun tripsForDate(date: String): Flow<List<PassengerEntity>>
}
