package com.moataz.edge.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class QueueJob(val id: Long, val updateId: Long, val payload: String, val attempts: Int)
data class LogEntry(val createdAt: Long, val level: String, val message: String)
data class QueueStats(val pending: Int, val failed: Int, val done: Int)
data class FailedJob(val updateId: Long, val attempts: Int, val error: String, val updatedAt: Long)

class NodeDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
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
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at INTEGER NOT NULL,
                level TEXT NOT NULL,
                message TEXT NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun enqueue(updateId: Long, payload: String) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("update_id", updateId); put("payload", payload); put("status", "PENDING")
            put("attempts", 0); put("created_at", now); put("updated_at", now)
        }
        writableDatabase.insertWithOnConflict("jobs", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun pendingJobs(limit: Int = 20): List<QueueJob> {
        val jobs = mutableListOf<QueueJob>()
        readableDatabase.query(
            "jobs", arrayOf("id", "update_id", "payload", "attempts"), "status = ?", arrayOf("PENDING"),
            null, null, "id ASC", limit.coerceIn(1, 100).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) jobs += QueueJob(cursor.getLong(0), cursor.getLong(1), cursor.getString(2), cursor.getInt(3))
        }
        return jobs
    }

    fun markDone(id: Long) {
        val values = ContentValues().apply { put("status", "DONE"); put("updated_at", System.currentTimeMillis()) }
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

    fun queueCounts(): Pair<Int, Int> = queueStats().let { it.pending to it.failed }

    fun queueStats(): QueueStats {
        var pending = 0; var failed = 0; var done = 0
        readableDatabase.rawQuery("SELECT status, COUNT(*) FROM jobs GROUP BY status", null).use { cursor ->
            while (cursor.moveToNext()) when (cursor.getString(0)) {
                "PENDING" -> pending = cursor.getInt(1)
                "FAILED" -> failed = cursor.getInt(1)
                "DONE" -> done = cursor.getInt(1)
            }
        }
        return QueueStats(pending, failed, done)
    }

    fun failedJobs(limit: Int = 20): List<FailedJob> {
        val result = mutableListOf<FailedJob>()
        readableDatabase.query(
            "jobs", arrayOf("update_id", "attempts", "last_error", "updated_at"), "status = ?", arrayOf("FAILED"),
            null, null, "updated_at DESC", limit.coerceIn(1, 100).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) result += FailedJob(cursor.getLong(0), cursor.getInt(1), cursor.getString(2).orEmpty(), cursor.getLong(3))
        }
        return result
    }

    fun log(level: String, message: String) {
        val values = ContentValues().apply {
            put("created_at", System.currentTimeMillis()); put("level", level.take(16)); put("message", message.take(4000))
        }
        writableDatabase.insert("logs", null, values)
    }

    fun recentLogs(limit: Int = 50): List<LogEntry> = queryLogs(null, null, limit)
    fun recentErrors(limit: Int = 30): List<LogEntry> = queryLogs("level IN (?, ?, ?)", arrayOf("ERROR", "WARN", "PYTHON_ERROR"), limit)

    private fun queryLogs(selection: String?, args: Array<String>?, limit: Int): List<LogEntry> {
        val logs = mutableListOf<LogEntry>()
        readableDatabase.query(
            "logs", arrayOf("created_at", "level", "message"), selection, args, null, null,
            "id DESC", limit.coerceIn(1, 100).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) logs += LogEntry(cursor.getLong(0), cursor.getString(1), cursor.getString(2))
        }
        return logs
    }

    private companion object {
        const val DB_NAME = "moataz_edge.db"
        const val DB_VERSION = 1
        const val MAX_ATTEMPTS = 10
    }
}
