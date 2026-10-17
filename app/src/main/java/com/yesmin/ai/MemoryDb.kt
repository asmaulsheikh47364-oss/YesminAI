package com.yesmin.ai

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class MemoryItem(val id: Long, val category: String, val text: String, val created: Long)

/** Plain SQLite (no annotation processor needed, so the build stays light on a phone). */
class MemoryDb(ctx: Context) : SQLiteOpenHelper(ctx, "yesmin.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE memory(id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT NOT NULL, text TEXT NOT NULL, created INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, text TEXT NOT NULL, ts INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

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

    fun clearHistory() { try { writableDatabase.delete("history", null, null) } catch (e: Exception) {} }
}
