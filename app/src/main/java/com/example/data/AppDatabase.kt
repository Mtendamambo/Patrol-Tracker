package com.example.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "patrol_database_sqlite.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_NAME = "patrol_entries"
        private const val COLUMN_ID = "id"
        private const val COLUMN_CHECKPOINT = "checkpoint"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_STATUS = "status"
        private const val COLUMN_NOTES = "notes"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_CHECKPOINT TEXT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_STATUS TEXT NOT NULL,
                $COLUMN_NOTES TEXT
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun getAllEntries(): List<PatrolEntry> {
        val list = mutableListOf<PatrolEntry>()
        val db = readableDatabase
        val cursor = db.query(TABLE_NAME, null, null, null, null, null, "$COLUMN_TIMESTAMP DESC")
        
        cursor.use {
            if (it.moveToFirst()) {
                val idIdx = it.getColumnIndexOrThrow(COLUMN_ID)
                val cpIdx = it.getColumnIndexOrThrow(COLUMN_CHECKPOINT)
                val tsIdx = it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)
                val stIdx = it.getColumnIndexOrThrow(COLUMN_STATUS)
                val ntIdx = it.getColumnIndexOrThrow(COLUMN_NOTES)
                
                do {
                    list.add(
                        PatrolEntry(
                            id = it.getInt(idIdx),
                            checkpoint = it.getString(cpIdx),
                            timestamp = it.getLong(tsIdx),
                            status = it.getString(stIdx),
                            notes = it.getString(ntIdx) ?: ""
                        )
                    )
                } while (it.moveToNext())
            }
        }
        return list
    }

    fun insertEntry(entry: PatrolEntry): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_CHECKPOINT, entry.checkpoint)
            put(COLUMN_TIMESTAMP, entry.timestamp)
            put(COLUMN_STATUS, entry.status)
            put(COLUMN_NOTES, entry.notes)
        }
        return db.insert(TABLE_NAME, null, values)
    }

    fun insertAll(entries: List<PatrolEntry>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            entries.forEach { entry ->
                val values = ContentValues().apply {
                    put(COLUMN_CHECKPOINT, entry.checkpoint)
                    put(COLUMN_TIMESTAMP, entry.timestamp)
                    put(COLUMN_STATUS, entry.status)
                    put(COLUMN_NOTES, entry.notes)
                }
                db.insert(TABLE_NAME, null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteEntry(id: Int) {
        val db = writableDatabase
        db.delete(TABLE_NAME, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun clearAll() {
        val db = writableDatabase
        db.delete(TABLE_NAME, null, null)
    }
}
