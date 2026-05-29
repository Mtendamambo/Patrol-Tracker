package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "patrol_entries")
data class PatrolEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val dateISO: String,
    val time: String,
    val point: Int?, // Null if it's a custom note
    val note: String?, // Null if it's a point logging
    val timestamp: Long,
    val outOfWindow: Boolean
)
