package com.example.data

data class PatrolEntry(
    val id: Int = 0,
    val checkpoint: String,
    val timestamp: Long,
    val status: String, // "CHECKPOINT", "START", "FINISH", "EMERGENCY", "MISSED"
    val notes: String = ""
)
