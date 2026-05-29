package com.example.data

import kotlinx.coroutines.flow.Flow

class PatrolRepository(
    private val patrolDao: PatrolDao,
    val preferences: PatrolPreferences
) {
    val allEntriesFlow: Flow<List<PatrolEntry>> = patrolDao.getAllEntriesFlow()

    suspend fun getAllEntries(): List<PatrolEntry> = patrolDao.getAllEntries()

    suspend fun insertEntry(entry: PatrolEntry): Long = patrolDao.insertEntry(entry)

    suspend fun insertEntries(entries: List<PatrolEntry>) = patrolDao.insertEntries(entries)

    suspend fun deleteEntry(entry: PatrolEntry) = patrolDao.deleteEntry(entry)

    suspend fun deleteEntryById(id: Long) = patrolDao.deleteEntryById(id)

    suspend fun clearAllEntries() = patrolDao.clearAllEntries()
}
