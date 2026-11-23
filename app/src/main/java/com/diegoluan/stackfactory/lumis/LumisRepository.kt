package com.diegoluan.stackfactory.lumis

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.diegoluan.stackfactory.checkin.CheckInState
import com.diegoluan.stackfactory.checkin.MedalTier

/**
 * Carteira LOCAL de recompensas GRATUITAS. Nao e uma carteira blockchain.
 * A base SQLite grava em uma transacao o valor concedido e o evento correspondente.
 * IDs dos eventos e dias tornam a concessao idempotente contra callbacks repetidos.
 * O dispositivo nao e autoridade confiavel; backend sera necessario para prevenir fraude.
 */
class LumisRepository(context: Context) {
    private val helper = LumisDatabase(context.applicationContext)

    @Synchronized
    fun snapshot(todayKey: String): LumisSnapshot {
        val db = helper.readableDatabase
        val balance = db.longQuery("SELECT COALESCE(SUM(delta_units), 0) FROM lumis_events")
        val earnedToday = db.longQuery(
            "SELECT COALESCE(SUM(delta_units), 0) FROM lumis_events WHERE day_key = ?",
            arrayOf(todayKey)
        )
        val recent = mutableListOf<LumisEvent>()
        db.rawQuery(
            "SELECT event_id, label, delta_units, day_key FROM lumis_events " +
                "ORDER BY created_at DESC, rowid DESC LIMIT 5", null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                recent.add(
                    LumisEvent(
                        id = cursor.getString(0),
                        label = cursor.getString(1),
                        deltaUnits = cursor.getLong(2),
                        dayKey = cursor.getString(3)
                    )
                )
            }
        }
        return LumisSnapshot(balance, earnedToday, recent)
    }

    /**
     * Chamado somente apos onScoreChanged e depois do check-in ter sido persistido.
     * Nao concede Lumis por abrir o jogo e nao usa retorno do anuncio premiado.
     */
    @Synchronized
    fun awardForProgress(state: CheckInState): LumisSnapshot {
        val dayKey = state.dayKey
        require(dayKey.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            "Dia invalido para recompensa"
        }
        if (!state.completedToday) return snapshot(dayKey)

        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val earnedBefore = db.longQuery(
                "SELECT COALESCE(awarded_units, 0) FROM lumis_daily_awards WHERE day_key = ?",
                arrayOf(dayKey)
            ).toInt()
            val totalTarget = LumisRules.totalDailyUnits(state.todayMedal)
            val earnedIncrement = LumisRules.dailyUpgradeUnits(earnedBefore, state.todayMedal)
            if (earnedIncrement > 0) {
                val values = ContentValues().apply {
                    put("day_key", dayKey)
                    put("awarded_units", totalTarget)
                }
                db.insertWithOnConflict(
                    "lumis_daily_awards", null, values, SQLiteDatabase.CONFLICT_REPLACE
                )
                db.insertEvent(
                    id = "daily:$dayKey:${state.todayMedal.name.lowercase()}",
                    dayKey = dayKey,
                    label = "Meta diaria ${state.todayMedal.title}",
                    units = earnedIncrement
                )
            }

            val streakBonus = LumisRules.streakBonusUnits(
                state.consecutiveDays,
                state.todayMedal != MedalTier.NONE
            )
            if (streakBonus > 0) {
                // UNIQUE(event_id): reabrir a partida ou voltar ao menu nao gera outro bonus.
                db.insertEvent(
                    id = "streak:$dayKey:${state.consecutiveDays}",
                    dayKey = dayKey,
                    label = "Consistencia de ${state.consecutiveDays} dias",
                    units = streakBonus
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return snapshot(dayKey)
    }

    private fun SQLiteDatabase.insertEvent(
        id: String, dayKey: String, label: String, units: Int
    ) {
        if (units <= 0) return
        val values = ContentValues().apply {
            put("event_id", id)
            put("day_key", dayKey)
            put("label", label)
            put("delta_units", units)
            put("created_at", System.currentTimeMillis())
        }
        insertWithOnConflict("lumis_events", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun SQLiteDatabase.longQuery(sql: String, args: Array<String>? = null): Long =
        rawQuery(sql, args).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
}

data class LumisEvent(val id: String, val label: String, val deltaUnits: Long, val dayKey: String)

data class LumisSnapshot(
    val balanceUnits: Long,
    val earnedTodayUnits: Long,
    val recentEvents: List<LumisEvent>
)

private class LumisDatabase(context: Context) : SQLiteOpenHelper(
    context, "stackfactory_lumis_v1.db", null, 1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE lumis_daily_awards (" +
                "day_key TEXT PRIMARY KEY NOT NULL, " +
                "awarded_units INTEGER NOT NULL CHECK(awarded_units >= 0))"
        )
        db.execSQL(
            "CREATE TABLE lumis_events (" +
                "event_id TEXT PRIMARY KEY NOT NULL, " +
                "day_key TEXT NOT NULL, " +
                "label TEXT NOT NULL, " +
                "delta_units INTEGER NOT NULL CHECK(delta_units > 0), " +
                "created_at INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX ix_lumis_events_day ON lumis_events(day_key)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // V1. Nunca apagar saldo em upgrades futuros; usar migracoes incrementais.
    }
}
