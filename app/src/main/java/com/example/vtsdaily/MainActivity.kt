package com.example.vtsdaily

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.room.Room
import com.example.vtsdaily.data.AppDatabase
import com.example.vtsdaily.data.PassengersDao
import com.example.vtsdaily.ui.theme.VTSDailyTheme

class MainActivity : ComponentActivity() {

    private val db: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "passengers.db"                 // on-device DB name
        )
            .createFromAsset("passengers.db") // file at app/src/main/assets/passengers.db
            .fallbackToDestructiveMigration()
            .build()
    }

    private val dao: PassengersDao by lazy { db.passengersDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VTSDailyTheme {
                PassengerApp(dao)
            }
        }
    }
}
