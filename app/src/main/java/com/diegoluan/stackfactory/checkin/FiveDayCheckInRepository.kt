package com.diegoluan.stackfactory.checkin

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Persistência 100% local. Abrir o app não concede check-in.
 * As novas medalhas aproveitam a melhor pontuação diária que já foi salva.
 */
class FiveDayCheckInRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "stackfactory_five_day_checkin", Context.MODE_PRIVATE
    )

    fun current(): CheckInState {
        val day = clock()
        val stored = read()
        // Migração idempotente: se a melhor partida salva já cumpriu a nova meta,
        // registra o selo e preserva a sequência existente (quando já concluída).
        val needsMigration = stored.bestScoreDay == day.today &&
            stored.bestScore >= MedalTier.BRONZE.threshold &&
            (stored.medalDay != day.today ||
                stored.medalOrdinal < MedalTier.fromScore(stored.bestScore).ordinal ||
                stored.weekKey != day.week ||
                stored.weekDayMedals.getOrNull(day.index - 1) !=
                    MedalTier.fromScore(stored.bestScore).ordinal)

        val record = if (needsMigration) {
            FiveDayCheckInRules.recordScore(
                stored, 0, day.today, day.yesterday, day.week, day.index
            ).also(::write)
        } else stored
        return FiveDayCheckInRules.view(record, day.today, day.yesterday, day.week, day.index)
    }

    fun onScoreChanged(scoreInCurrentGame: Int): CheckInState {
        val day = clock()
        val before = read()
        val after = FiveDayCheckInRules.recordScore(
            before, scoreInCurrentGame, day.today, day.yesterday, day.week, day.index
        )
        if (before != after) write(after)
        return FiveDayCheckInRules.view(after, day.today, day.yesterday, day.week, day.index)
    }

    private fun write(data: CheckInRecord) {
        prefs.edit()
            // Chaves antigas, necessárias para compatibilidade com a versão anterior.
            .putString("completed_day", data.completedDay)
            .putInt("streak", data.streak)
            .putString("best_day", data.bestScoreDay)
            .putInt("best_score", data.bestScore)
            .putString("medal_day", data.medalDay)
            .putInt("medal_ordinal", data.medalOrdinal)
            .putString("week_key", data.weekKey)
            .putString("week_medals", data.weekDayMedals.joinToString(","))
            .apply()
    }

    private fun read(): CheckInRecord {
        // Houve duas versões preparatórias da chave de medalhas semanais.
        // Se existir a chave mais nova, ela prevalece; caso contrário, lê a anterior.
        val weekStored = prefs.getString("week_medals", null)
            ?: prefs.getString("week_day_medals", "")
        val parsedMedals = weekStored.orEmpty().split(',').mapNotNull(String::toIntOrNull)
        return CheckInRecord(
            completedDay = prefs.getString("completed_day", "").orEmpty(),
            streak = prefs.getInt("streak", 0),
            bestScoreDay = prefs.getString("best_day", "").orEmpty(),
            bestScore = prefs.getInt("best_score", 0),
            medalDay = prefs.getString("medal_day", "").orEmpty(),
            medalOrdinal = prefs.getInt("medal_ordinal", 0),
            weekKey = prefs.getString("week_key", "").orEmpty(),
            weekDayMedals = if (parsedMedals.size == 7) parsedMedals else List(7) { 0 }
        )
    }

    /** Semana local de segunda a domingo, sem dependência da configuração regional do aparelho. */
    private fun clock(): DayKeys {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val now = Calendar.getInstance()
        val today = formatter.format(now.time)
        val index = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val monday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1 - index) }
        return DayKeys(today, formatter.format(yesterday.time), formatter.format(monday.time), index)
    }

    private data class DayKeys(val today: String, val yesterday: String, val week: String, val index: Int)
}
