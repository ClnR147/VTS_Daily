package com.example.vtsdaily

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class TripDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "trips.db"  // Database name
        private const val DB_VERSION = 1        // Database version
        private const val TABLE_NAME = "completed_trips" // Table name for completed trips
        private const val PREFS_NAME = "app_preferences"  // SharedPreferences file name
        private const val PREFS_KEY = "data_inserted"  // Key for checking data insert status
    }

    // Called when the database is created
    override fun onCreate(db: SQLiteDatabase?) {
        val createTableQuery = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                ID INTEGER PRIMARY KEY,
                DriveDate TEXT,
                Passenger TEXT,
                WheelChair TEXT,
                Attendant TEXT,
                Blind TEXT,
                D2D TEXT,
                SG TEXT,
                [A/R] TEXT,
                PUTimeAppt TEXT,
                RTTime TEXT,
                PAddress TEXT,
                DAddress TEXT,
                Phone TEXT,
                Deceased TEXT
            );
        """
        db?.execSQL(createTableQuery)
    }

    // Called when the database version is updated
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // Drop the existing table and recreate it
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    // Function to execute SQL insert statements from the .sql file
    fun executeInsertStatements(db: SQLiteDatabase, sqlStatements: String) {
        val statements = sqlStatements.split(";")  // Split statements by semicolon
        for (statement in statements) {
            val trimmedStatement = statement.trim()
            if (trimmedStatement.isNotBlank()) {
                try {
                    db.execSQL(trimmedStatement)  // Execute each insert statement
                } catch (e: Exception) {
                    e.printStackTrace()  // Log any errors during execution
                }
            }
        }
    }

    // Function to check if the data has been inserted (using SharedPreferences)
    fun isDataInserted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREFS_KEY, false)
    }

    // Function to mark data as inserted in SharedPreferences
    fun markDataInserted(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putBoolean(PREFS_KEY, false)  // Correct method: putBoolean
        editor.apply()  // Don't forget to apply the changes
    }
}
