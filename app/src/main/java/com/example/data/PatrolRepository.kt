package com.example.data

class PatrolRepository(private val db: AppDatabase) {
    suspend fun getAllEntries(): List<PatrolEntry> = db.getAllEntries()
    suspend fun insert(entry: PatrolEntry): Long = db.insertEntry(entry)
    suspend fun insertAll(entries: List<PatrolEntry>) = db.insertAll(entries)
    suspend fun deleteById(id: Int) = db.deleteEntry(id)
    suspend fun clearAll() = db.clearAll()
}
