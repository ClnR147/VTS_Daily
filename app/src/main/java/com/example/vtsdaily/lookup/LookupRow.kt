package com.example.vtsdaily.lookup

data class LookupRow(
    val driveDate: String? = null,
    val passenger: String? = null,
    val pAddress: String? = null,
    val dAddress: String? = null,
    val phone: String? = null,
    val tripType: String? = null,
    val puTimeAppt: String? = null,
    val doTimeAppt: String? = null,   // <-- add this
    val rtTime: String? = null,
    val raw: Map<String, String?> = emptyMap()
)

