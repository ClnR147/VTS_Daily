package com.example.vtsdaily.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "passengers")
data class PassengerEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int,
    @ColumnInfo(name = "passenger") val passenger: String,
    @ColumnInfo(name = "driveDate") val driveDate: String?,
    @ColumnInfo(name = "phone") val phone: String?
)
