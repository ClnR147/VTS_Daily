package com.example.vtsdaily.drivers

data class Driver(
    val name: String,
    val van: String,
    val year: Int?,   // allow blank
    val make: String,
    val model: String,
    val phone: String
)
