
package com.diegoluan.stackfactory.daily

import android.content.Context
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

class DailyMissionRepository(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        "stack_factory_daily_missions",
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun getToday(date: LocalDate = LocalDate.now()): DailyMission {
        val storedDate = preferences.getString(KEY_DATE, null)

        if (storedDate != date.toString()) {
            val mission = generate(date)

            preferences.edit()
                .putString(KEY_DATE, date.toString())
                .putInt(KEY_PROGRESS, 0)
                .putBoolean(KEY_COMPLETED, false)
                .commit()

            return mission
        }

        val template = generate(date)

        val progress = preferences.getInt(
            KEY_PROGRESS,
            0
        ).coerceIn(0, template.target)

        return template.copy(
            progress = progress,
            completed = preferences.getBoolean(KEY_COMPLETED, false) ||
                    progress >= template.target
        )
    }

    @Synchronized
    fun recordBlockPlaced(
        date: LocalDate = LocalDate.now()
    ): DailyMission =
        update(
            date = date,
            type = MissionType.STACK_BLOCKS,
            value = 1,
            cumulative = true
        )

    @Synchronized
    fun recordGameFinished(
        date: LocalDate = LocalDate.now()
    ): DailyMission =
        update(
            date = date,
            type = MissionType.FINISH_GAMES,
            value = 1,
            cumulative = true
        )

    @Synchronized
    fun recordScore(
        score: Int,
        date: LocalDate = LocalDate.now()
    ): DailyMission =
        update(
            date = date,
            type = MissionType.REACH_SCORE,
            value = max(0, score),
            cumulative = false
        )

    fun getStreak(): Int =
        preferences.getInt(KEY_STREAK, 0)

    fun lastCompletedDate(): LocalDate? =
        preferences.getString(KEY_LAST_COMPLETED, null)
            ?.let {
                runCatching {
                    LocalDate.parse(it)
                }.getOrNull()
            }

    @Synchronized
    private fun update(
        date: LocalDate,
        type: MissionType,
        value: Int,
        cumulative: Boolean
    ): DailyMission {

        val mission = getToday(date)

        if (
            mission.completed ||
            mission.type != type ||
            value <= 0
        ) {
            return mission
        }

        val newProgress = min(
            mission.target,
            if (cumulative) {
                mission.progress + value
            } else {
                max(mission.progress, value)
            }
        )

        val completedNow = newProgress >= mission.target

        val editor = preferences.edit()
            .putInt(KEY_PROGRESS, newProgress)
            .putBoolean(KEY_COMPLETED, completedNow)

        if (completedNow) {
            val previous = preferences.getString(
                KEY_LAST_COMPLETED,
                null
            )?.let {
                runCatching {
                    LocalDate.parse(it)
                }.getOrNull()
            }

            val streak = if (
                previous != null &&
                ChronoUnit.DAYS.between(previous, date) == 1L
            ) {
                preferences.getInt(KEY_STREAK, 0) + 1
            } else {
                1
            }

            editor
                .putInt(KEY_STREAK, streak)
                .putString(KEY_LAST_COMPLETED, date.toString())
        }

        editor.commit()

        return mission.copy(
            progress = newProgress,
            completed = completedNow
        )
    }

    /**
     * Gera uma missão diária com dificuldade reduzida.
     *
     * As missões seguem um ciclo determinístico de sete dias.
     * Cada data recebe sempre a mesma missão.
     *
     * Metas:
     * - Empilhar 2 blocos
     * - Finalizar 2 partidas
     * - Empilhar 3 blocos
     * - Alcançar 3 pontos
     * - Finalizar 3 partidas
     * - Empilhar 4 blocos
     * - Alcançar 5 pontos
     */
    private fun generate(date: LocalDate): DailyMission {

        val missions = listOf(
            MissionType.STACK_BLOCKS to 2,
            MissionType.FINISH_GAMES to 2,
            MissionType.STACK_BLOCKS to 3,
            MissionType.REACH_SCORE to 3,
            MissionType.FINISH_GAMES to 3,
            MissionType.STACK_BLOCKS to 4,
            MissionType.REACH_SCORE to 5
        )

        val index = Math.floorMod(
            date.toEpochDay(),
            missions.size.toLong()
        ).toInt()

        val (type, target) = missions[index]

        return DailyMission(
            date = date,
            type = type,
            target = target,
            progress = 0,
            completed = false
        )
    }

    private companion object {
        const val KEY_DATE = "date"
        const val KEY_PROGRESS = "progress"
        const val KEY_COMPLETED = "completed"
        const val KEY_STREAK = "streak"
        const val KEY_LAST_COMPLETED = "last_completed"
    }
}
