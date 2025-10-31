package com.example.vtsdaily.model

import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class ContactEntry(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var phoneNumber: String
)
