package com.shbeeb.ai

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * Private, on-device, opt-in chat history. The UI must not call append until
 * the user explicitly enables the history setting. Not end-to-end encrypted.
 */
internal class ChatHistoryStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "shbeeb_history.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE chat_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                engine TEXT NOT NULL,
                role TEXT NOT NULL,
                text TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_chat_engine_id ON chat_messages(engine, id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema changes will be non-destructive migrations.
    }

    @Synchronized
    fun append(engine: String, message: ChatEntry) {
        require(engine in ChatEngine.values().map { it.id }) { "Unknown engine" }
        require(message.role in listOf("user", "assistant")) { "Invalid role" }
        if (message.text.isBlank()) return
        val values = ContentValues().apply {
            put("engine", engine)
            put("role", message.role)
            put("text", message.text.take(16000))
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insertOrThrow("chat_messages", null, values)
        // Keep local storage bounded: at most the latest 200 messages per engine.
        writableDatabase.execSQL(
            "DELETE FROM chat_messages WHERE engine=? AND id NOT IN " +
                "(SELECT id FROM chat_messages WHERE engine=? ORDER BY id DESC LIMIT 200)",
            arrayOf(engine, engine)
        )
    }

    @Synchronized
    fun load(engine: String): List<ChatEntry> {
        val latest = mutableListOf<ChatEntry>()
        readableDatabase.query(
            "chat_messages", arrayOf("role", "text"),
            "engine=?", arrayOf(engine), null, null, "id DESC", "100"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                latest.add(ChatEntry(cursor.getString(0), cursor.getString(1)))
            }
        }
        return latest.asReversed()
    }

    @Synchronized
    fun deleteAll() {
        writableDatabase.delete("chat_messages", null, null)
    }
}

internal data class SimpleTask(val id: Long, val title: String, val complete: Boolean)

/** Small on-device checklist without notification or background permissions. */
internal class TaskStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("shbeeb_tasks", Context.MODE_PRIVATE)

    fun load(): List<SimpleTask> = runCatching {
        val items = JSONArray(preferences.getString("items", "[]"))
        buildList {
            for (i in 0 until minOf(items.length(), 100)) {
                val item = items.optJSONObject(i) ?: continue
                val title = item.optString("title").take(200)
                if (title.isNotBlank()) {
                    add(SimpleTask(item.optLong("id"), title, item.optBoolean("complete")))
                }
            }
        }
    }.getOrDefault(emptyList())

    fun save(tasks: List<SimpleTask>) {
        val array = JSONArray()
        tasks.take(100).forEach {
            array.put(JSONObject()
                .put("id", it.id)
                .put("title", it.title.take(200))
                .put("complete", it.complete))
        }
        preferences.edit().putString("items", array.toString()).apply()
    }
}
