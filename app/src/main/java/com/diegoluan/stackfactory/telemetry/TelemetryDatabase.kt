package com.diegoluan.stackfactory.telemetry

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Eventos imutáveis, com identificador único e estado de envio persistente. */
data class TelemetryRecord(
    val id: String,
    val type: String,
    val sessionId: String,
    val gameId: String,
    val createdAtMs: Long,
    val value: Int,
    val detail: String
)

internal class TelemetryDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext, "stackfactory_telemetry_v1.db", null, 1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE telemetry_events (" +
                "id TEXT PRIMARY KEY NOT NULL, " +
                "type TEXT NOT NULL, " +
                "session_id TEXT NOT NULL, " +
                "game_id TEXT NOT NULL, " +
                "created_at_ms INTEGER NOT NULL, " +
                "numeric_value INTEGER NOT NULL, " +
                "detail TEXT NOT NULL, " +
                "uploaded INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX ix_telemetry_pending ON telemetry_events(uploaded,created_at_ms)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Nunca destruir os eventos no upgrade; implementar migrações incrementais.
    }

    @Synchronized
    fun append(event: TelemetryRecord): Boolean {
        val values = ContentValues().apply {
            put("id", event.id)
            put("type", event.type)
            put("session_id", event.sessionId)
            put("game_id", event.gameId)
            put("created_at_ms", event.createdAtMs)
            put("numeric_value", event.value)
            put("detail", event.detail.take(80))
            put("uploaded", 0)
        }
        return writableDatabase.insertWithOnConflict(
            "telemetry_events", null, values, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    @Synchronized
    fun pending(limit: Int = 30): List<TelemetryRecord> {
        val events = mutableListOf<TelemetryRecord>()
        readableDatabase.rawQuery(
            "SELECT id,type,session_id,game_id,created_at_ms,numeric_value,detail " +
                "FROM telemetry_events WHERE uploaded = 0 " +
                "ORDER BY created_at_ms ASC LIMIT ?",
            arrayOf(limit.coerceIn(1, 100).toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                events.add(
                    TelemetryRecord(
                        id = cursor.getString(0),
                        type = cursor.getString(1),
                        sessionId = cursor.getString(2),
                        gameId = cursor.getString(3),
                        createdAtMs = cursor.getLong(4),
                        value = cursor.getInt(5),
                        detail = cursor.getString(6)
                    )
                )
            }
        }
        return events
    }

    @Synchronized
    fun uploaded(eventId: String) {
        val values = ContentValues().apply { put("uploaded", 1) }
        writableDatabase.update("telemetry_events", values, "id = ?", arrayOf(eventId))
    }

    @Synchronized
    fun purgeAll() {
        writableDatabase.delete("telemetry_events", null, null)
    }

    @Synchronized
    fun purgeUploaded() {
        // Não armazenar histórico de telemetria indefinidamente no aparelho.
        val deadline = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
        writableDatabase.delete(
            "telemetry_events", "uploaded = 1 AND created_at_ms < ?",
            arrayOf(deadline.toString())
        )
    }
}
