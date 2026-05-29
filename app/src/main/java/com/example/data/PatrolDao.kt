package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PatrolDao {
    @Query("SELECT * FROM patrol_entries ORDER BY timestamp DESC")
    fun getAllEntriesFlow(): Flow<List<PatrolEntry>>

    @Query("SELECT * FROM patrol_entries ORDER BY timestamp ASC")
    suspend fun getAllEntries(): List<PatrolEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: PatrolEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<PatrolEntry>)

    @Delete
    suspend fun deleteEntry(entry: PatrolEntry)

    @Query("DELETE FROM patrol_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("DELETE FROM patrol_entries")
    suspend fun clearAllEntries()
}
