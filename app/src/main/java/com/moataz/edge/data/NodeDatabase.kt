package com.moataz.edge.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class QueueJob(
    val id: Long,
    val updateId: Long,
    val payload: String,
    val attempts: Int
)

data class LogEntry(
    val createdAt: Long,
    val level: String,
    val message: String
)

class NodeDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE jobs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                update_id INTEGER NOT NULL UNIQUE,
                payload TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDING',
                attempts INTEGER NOT NULL DEFAULT 0,
                last_error TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at INTEGER NOT NULL,
                level TEXT NOT NULL,
                message TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun enqueue(updateId: Long, payload: String) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("update_id", updateId)
            put("payload", payload)
            put("status", "PENDING")
            put("attempts", 0)
            put("created_at", now)
            put("updated_at", now)
        }
        writableDatabase.insertWithOnConflict("jobs", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun pendingJobs(limit: Int = 20): List<QueueJob> {
        val jobs = mutableListOf<QueueJob>()
        readableDatabase.query(
            "jobs",
            arrayOf("id", "update_id", "payload", "attempts"),
            "status = ?",
            arrayOf("PENDING"),
            null,
            null,
            "id ASC",
            limit.coerceIn(1, 100).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                jobs += QueueJob(
                    id = cursor.getLong(0),
                    updateId = cursor.getLong(1),
                    payload = cursor.getString(2),
                    attempts = cursor.getInt(3)
                )
            }
        }
        return jobs
    }

    fun markDone(id: Long) {
        val values = ContentValues().apply {
            put("status", "DONE")
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("jobs", values, "id = ?", arrayOf(id.toString()))
    }

    fun markRetry(id: Long, currentAttempts: Int, error: String) {
        val nextAttempts = currentAttempts + 1
        val values = ContentValues().apply {
            put("attempts", nextAttempts)
            put("last_error", error.take(1000))
            put("status", if (nextAttempts >= MAX_ATTEMPTS) "FAILED" else "PENDING")
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("jobs", values, "id = ?", arrayOf(id.toString()))
    }

    fun queueCounts(): Pair<Int, Int> {
        var pending = 0
        var failed = 0
        readableDatabase.rawQuery(
            "SELECT status, COUNT(*) FROM jobs WHERE status IN ('PENDING', 'FAILED') GROUP BY status",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                when (cursor.getString(0)) {
                    "PENDING" -> pending = cursor.getInt(1)
                    "FAILED" -> failed = cursor.getInt(1)
                }
            }
        }
        return pending to failed
    }

    fun log(level: String, message: String) {
        val values = ContentValues().apply {
            put("created_at", System.currentTimeMillis())
            put("level", level.take(16))
            put("message", message.take(4000))
        }
        writableDatabase.insert("logs", null, values)
    }

    fun recentLogs(limit: Int = 30): List<LogEntry> {
        val logs = mutableListOf<LogEntry>()
        readableDatabase.query(
            "logs",
            arrayOf("created_at", "level", "message"),
            null,
            null,
            null,
            null,
            "id DESC",
            limit.coerceIn(1, 100).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                logs += LogEntry(cursor.getLong(0), cursor.getString(1), cursor.getString(2))
            }
        }
        return logs
    }

    private companion object {
        const val DB_NAME = "moataz_edge.db"
        const val DB_VERSION = 1
        const val MAX_ATTEMPTS = 10
    }
}
