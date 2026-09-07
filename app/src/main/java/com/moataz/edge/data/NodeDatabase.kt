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
        db.execSQL("""CREATE TABLE jobs (id INTEGER PRIMARY KEY AUTOINCREMENT, update_id INTEGER NOT NULL UNIQUE, payload TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING', attempts INTEGER NOT NULL DEFAULT 0, last_error TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE logs (id INTEGER PRIMARY KEY AUTOINCREMENT, created_at INTEGER NOT NULL, level TEXT NOT NULL, message TEXT NOT NULL)""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun enqueue(updateId: Long, payload: String) {
        val now=System.currentTimeMillis(); val v=ContentValues().apply { put("update_id",updateId);put("payload",payload);put("status","PENDING");put("attempts",0);put("created_at",now);put("updated_at",now) }
        writableDatabase.insertWithOnConflict("jobs",null,v,SQLiteDatabase.CONFLICT_IGNORE)
    }
    fun pendingJobs(limit:Int=20):List<QueueJob>{ val r=mutableListOf<QueueJob>(); readableDatabase.query("jobs",arrayOf("id","update_id","payload","attempts"),"status = ?",arrayOf("PENDING"),null,null,"id ASC",limit.coerceIn(1,100).toString()).use{c->while(c.moveToNext())r+=QueueJob(c.getLong(0),c.getLong(1),c.getString(2),c.getInt(3))};return r }
    fun markDone(id:Long){ val v=ContentValues().apply{put("status","DONE");put("updated_at",System.currentTimeMillis())};writableDatabase.update("jobs",v,"id = ?",arrayOf(id.toString())) }
    fun markRetry(id:Long,currentAttempts:Int,error:String){val n=currentAttempts+1;val v=ContentValues().apply{put("attempts",n);put("last_error",error.take(1000));put("status",if(n>=MAX_ATTEMPTS)"FAILED" else "PENDING");put("updated_at",System.currentTimeMillis())};writableDatabase.update("jobs",v,"id = ?",arrayOf(id.toString()))}
    fun queueCounts():Pair<Int,Int> = queueStats().let{it.pending to it.failed}
    fun queueStats():QueueStats{var p=0;var f=0;var d=0;readableDatabase.rawQuery("SELECT status, COUNT(*) FROM jobs GROUP BY status",null).use{c->while(c.moveToNext())when(c.getString(0)){"PENDING"->p=c.getInt(1);"FAILED"->f=c.getInt(1);"DONE"->d=c.getInt(1)}};return QueueStats(p,f,d)}
    fun failedJobs(limit:Int=20):List<FailedJob>{val r=mutableListOf<FailedJob>();readableDatabase.query("jobs",arrayOf("update_id","attempts","last_error","updated_at"),"status = ?",arrayOf("FAILED"),null,null,"updated_at DESC",limit.coerceIn(1,100).toString()).use{c->while(c.moveToNext())r+=FailedJob(c.getLong(0),c.getInt(1),c.getString(2).orEmpty(),c.getLong(3))};return r}
    fun retryAllFailed(){val v=ContentValues().apply{put("status","PENDING");put("attempts",0);putNull("last_error");put("updated_at",System.currentTimeMillis())};writableDatabase.update("jobs",v,"status = ?",arrayOf("FAILED"))}
    fun clearCompleted(){writableDatabase.delete("jobs","status = ?",arrayOf("DONE"))}
    fun log(level:String,message:String){val v=ContentValues().apply{put("created_at",System.currentTimeMillis());put("level",level.take(16));put("message",message.take(4000))};writableDatabase.insert("logs",null,v)}
    fun recentLogs(limit:Int=50):List<LogEntry> = queryLogs(null,null,limit)
    fun recentErrors(limit:Int=30):List<LogEntry> = queryLogs("level IN (?, ?, ?)",arrayOf("ERROR","WARN","PYTHON_ERROR"),limit)
    fun clearLogs(){writableDatabase.delete("logs",null,null)}
    private fun queryLogs(selection:String?,args:Array<String>?,limit:Int):List<LogEntry>{val r=mutableListOf<LogEntry>();readableDatabase.query("logs",arrayOf("created_at","level","message"),selection,args,null,null,"id DESC",limit.coerceIn(1,100).toString()).use{c->while(c.moveToNext())r+=LogEntry(c.getLong(0),c.getString(1),c.getString(2))};return r}
    private companion object{const val DB_NAME="moataz_edge.db";const val DB_VERSION=1;const val MAX_ATTEMPTS=10}
}
