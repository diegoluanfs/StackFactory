package com.diegoluan.stackfactory.daily

import android.content.Context
import java.time.LocalDate

/** Interface simples para integrar os eventos do jogo às missões diárias. */
class DailyMissionManager(context: Context) {
    private val repository = DailyMissionRepository(context)

    fun today(): DailyMission = repository.getToday()

    fun onBlockPlaced(): DailyMission = repository.recordBlockPlaced()

    fun onGameFinished(): DailyMission = repository.recordGameFinished()

    fun onScoreChanged(score: Int): DailyMission = repository.recordScore(score)

    fun streak(): Int {
        val lastCompleted = repository.lastCompletedDate()
        val today = LocalDate.now()
        if (lastCompleted != today && lastCompleted != today.minusDays(1)) return 0
        return repository.getStreak()
    }
}
