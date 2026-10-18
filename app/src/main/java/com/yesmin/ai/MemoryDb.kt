package com.yesmin.ai

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class MemoryItem(val id: Long, val category: String, val text: String, val created: Long)

/** Plain SQLite (no annotation processor needed, so the build stays light on a phone). */
class MemoryDb(ctx: Context) : SQLiteOpenHelper(ctx, "yesmin.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE memory(id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT NOT NULL, text TEXT NOT NULL, created INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, text TEXT NOT NULL, ts INTEGER NOT NULL)")
        createReminders(db)
    }

    private fun createReminders(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS reminders(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, note TEXT NOT NULL, time INTEGER NOT NULL, repeat TEXT NOT NULL, done INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createReminders(db)
    }

    fun all(): List<MemoryItem> = try {
        readableDatabase.rawQuery("SELECT id,category,text,created FROM memory ORDER BY id DESC", null).use { c ->
            val l = mutableListOf<MemoryItem>()
            while (c.moveToNext()) l.add(MemoryItem(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)))
            l
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun add(category: String, text: String): Boolean = try {
        val v = ContentValues().apply {
            put("category", category); put("text", text); put("created", System.currentTimeMillis())
        }
        writableDatabase.insertOrThrow("memory", null, v)
        true
    } catch (e: Exception) {
        false
    }

    fun update(id: Long, text: String): Boolean = try {
        val v = ContentValues().apply { put("text", text) }
        writableDatabase.update("memory", v, "id=?", arrayOf(id.toString())) > 0
    } catch (e: Exception) {
        false
    }

    fun delete(id: Long): Boolean = try {
        writableDatabase.delete("memory", "id=?", arrayOf(id.toString())) > 0
    } catch (e: Exception) {
        false
    }

    fun clearMemory() { try { writableDatabase.delete("memory", null, null) } catch (e: Exception) {} }

    fun addHistory(fromUser: Boolean, text: String) {
        try {
            val v = ContentValues().apply {
                put("role", if (fromUser) "user" else "model"); put("text", text); put("ts", System.currentTimeMillis())
            }
            writableDatabase.insertOrThrow("history", null, v)
        } catch (e: Exception) {
        }
    }

    fun history(limit: Int): List<ChatMsg> = try {
        readableDatabase.rawQuery("SELECT role,text FROM history ORDER BY id DESC LIMIT $limit", null).use { c ->
            val l = mutableListOf<ChatMsg>()
            while (c.moveToNext()) l.add(ChatMsg(c.getString(0) == "user", c.getString(1)))
            l.reversed()
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun reminders(): List<Reminder> = try {
        readableDatabase.rawQuery("SELECT id,title,note,time,repeat,done FROM reminders ORDER BY time ASC", null).use { c ->
            val l = mutableListOf<Reminder>()
            while (c.moveToNext()) l.add(Reminder(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getString(4), c.getInt(5) == 1))
            l
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun reminder(id: Long): Reminder? = reminders().firstOrNull { it.id == id }

    fun addReminder(title: String, note: String, time: Long, repeat: String): Long = try {
        val v = ContentValues().apply {
            put("title", title); put("note", note); put("time", time); put("repeat", repeat); put("done", 0)
        }
        writableDatabase.insertOrThrow("reminders", null, v)
    } catch (e: Exception) {
        -1L
    }

    fun updateReminder(id: Long, title: String, note: String, time: Long, repeat: String): Boolean = try {
        val v = ContentValues().apply {
            put("title", title); put("note", note); put("time", time); put("repeat", repeat); put("done", 0)
        }
        writableDatabase.update("reminders", v, "id=?", arrayOf(id.toString())) > 0
    } catch (e: Exception) {
        false
    }

    fun updateReminderTime(id: Long, time: Long) {
        try {
            val v = ContentValues().apply { put("time", time) }
            writableDatabase.update("reminders", v, "id=?", arrayOf(id.toString()))
        } catch (e: Exception) {
        }
    }

    fun setReminderDone(id: Long, done: Boolean) {
        try {
            val v = ContentValues().apply { put("done", if (done) 1 else 0) }
            writableDatabase.update("reminders", v, "id=?", arrayOf(id.toString()))
        } catch (e: Exception) {
        }
    }

    fun deleteReminder(id: Long) { try { writableDatabase.delete("reminders", "id=?", arrayOf(id.toString())) } catch (e: Exception) {} }
    fun clearReminders() { try { writableDatabase.delete("reminders", null, null) } catch (e: Exception) {} }

    fun clearHistory() { try { writableDatabase.delete("history", null, null) } catch (e: Exception) {} }
}
