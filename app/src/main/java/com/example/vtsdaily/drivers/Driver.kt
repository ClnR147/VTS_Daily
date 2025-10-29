package com.example.vtsdaily.drivers

data class Driver(
    val id: String,
    val name: String,
    val phone: String,
    val van: String,
    val year: Int,
    val makeModel: String,
    val active: Boolean = true
)

