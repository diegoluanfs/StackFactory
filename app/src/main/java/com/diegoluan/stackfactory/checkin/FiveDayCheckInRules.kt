package com.diegoluan.stackfactory.checkin

/** Maior selo conquistado em uma partida do dia. */
enum class MedalTier(val threshold: Int, val title: String) {
    NONE(0, "Nenhum selo"),
    BRONZE(40, "Bronze"),
    SILVER(75, "Prata"),
    GOLD(100, "Ouro");

    companion object {
        fun fromScore(points: Int): MedalTier = when {
            points >= GOLD.threshold -> GOLD
            points >= SILVER.threshold -> SILVER
            points >= BRONZE.threshold -> BRONZE
            else -> NONE
        }

        fun fromStoredValue(value: Int): MedalTier = entries.getOrElse(value) { NONE }
    }
}

/** Mantém campos legados para não apagar progresso existente. */
data class CheckInRecord(
    val completedDay: String = "",
    val streak: Int = 0,
    val bestScoreDay: String = "",
    val bestScore: Int = 0,
    val medalDay: String = "",
    val medalOrdinal: Int = 0,
    val weekKey: String = "",
    val weekDayMedals: List<Int> = List(7) { 0 }
)

data class CheckInState(
    val bestScoreToday: Int,
    val todayMedal: MedalTier,
    val consecutiveDays: Int,
    val currentDayIndex: Int,
    val weekDayMedals: List<MedalTier>,
    val weekBronzeDays: Int,
    val weekSilverDays: Int,
    val weekGoldDays: Int,
    val dayKey: String = ""
) {
    // Mantidos para compatibilidade com os componentes anteriores.
    val targetScore: Int get() = MedalTier.BRONZE.threshold
    val bronzeTarget: Int get() = MedalTier.BRONZE.threshold
    val silverTarget: Int get() = MedalTier.SILVER.threshold
    val goldTarget: Int get() = MedalTier.GOLD.threshold
    val completedToday: Boolean get() = todayMedal != MedalTier.NONE
    /** Progresso da meta de sete dias, sem limitar o valor real da sequência. */
    val completedDaysOfSeven: Int get() = consecutiveDays.coerceIn(0, FiveDayCheckInRules.DAYS)
    /** Mantido para compatibilidade binária/fonte com telas legadas. */
    val completedDaysOfFive: Int get() = consecutiveDays.coerceIn(0, 5)
    val weekBronzeTargetDays: Int get() = 3
    val weekSilverTargetDays: Int get() = 5
    val weekGoldTargetDays: Int get() = 7
    val weeklyCompleted: Boolean get() = weekGoldDays == weekGoldTargetDays
    val nextTier: MedalTier? get() = when (todayMedal) {
        MedalTier.NONE -> MedalTier.BRONZE
        MedalTier.BRONZE -> MedalTier.SILVER
        MedalTier.SILVER -> MedalTier.GOLD
        MedalTier.GOLD -> null
    }
    val pointsToNextTier: Int get() = (nextTier?.threshold?.minus(bestScoreToday) ?: 0).coerceAtLeast(0)
}

/**
 * Regras puras, determinísticas, sem Android:
 * - Uma melhor partida por dia; não somar várias partidas.
 * - 40/75/100 pontos elevam o selo do mesmo dia.
 * - Um dia com ao menos bronze conta apenas uma vez na sequência de sete dias.
 * - Metas semanais: 3 bronze+, 5 prata+, 7 ouro (dias únicos, segunda a domingo).
 * - Chave semanal = data da segunda-feira em yyyy-MM-dd, definida no repositório.
 */
object FiveDayCheckInRules {
    const val DAYS = 7
    const val TARGET_SCORE = 40

    fun recordScore(
        previous: CheckInRecord,
        scoreInCurrentGame: Int,
        today: String,
        yesterday: String,
        weekKey: String,
        dayIndex: Int
    ): CheckInRecord {
        require(today.isNotBlank() && yesterday.isNotBlank() && weekKey.isNotBlank())
        require(dayIndex in 1..7)

        val best = maxOf(
            if (previous.bestScoreDay == today) previous.bestScore.coerceAtLeast(0) else 0,
            scoreInCurrentGame.coerceAtLeast(0)
        )
        val medal = MedalTier.fromScore(best)
        val alreadyCheckedToday = previous.completedDay == today
        val earnedToday = !alreadyCheckedToday && medal != MedalTier.NONE
        val nextStreak = when {
            !earnedToday -> previous.streak
            previous.completedDay == yesterday -> if (previous.streak < Int.MAX_VALUE) previous.streak + 1 else Int.MAX_VALUE
            else -> 1
        }

        val dayMedals = if (previous.weekKey == weekKey) normalizeWeek(previous.weekDayMedals)
                        else List(7) { 0 }
        val updatedWeek = dayMedals.toMutableList().also { week ->
            week[dayIndex - 1] = maxOf(week[dayIndex - 1], medal.ordinal)
        }

        return previous.copy(
            completedDay = if (earnedToday) today else previous.completedDay,
            streak = nextStreak,
            bestScoreDay = today,
            bestScore = best,
            medalDay = today,
            medalOrdinal = medal.ordinal,
            weekKey = weekKey,
            weekDayMedals = updatedWeek
        )
    }

    fun view(
        record: CheckInRecord,
        today: String,
        yesterday: String,
        weekKey: String,
        dayIndex: Int
    ): CheckInState {
        require(dayIndex in 1..7)
        val best = if (record.bestScoreDay == today) record.bestScore.coerceAtLeast(0) else 0
        // Migração: versões antigas armazenavam a pontuação, não o selo.
        val medal = MedalTier.fromScore(best)
        val week = if (record.weekKey == weekKey) normalizeWeek(record.weekDayMedals).toMutableList()
                   else MutableList(7) { 0 }
        week[dayIndex - 1] = maxOf(week[dayIndex - 1], medal.ordinal)
        val medals = week.map(MedalTier::fromStoredValue)
        val streak = if (record.completedDay == today || record.completedDay == yesterday) {
            record.streak.coerceAtLeast(0)
        } else 0

        return CheckInState(
            bestScoreToday = best,
            todayMedal = medal,
            consecutiveDays = streak,
            currentDayIndex = dayIndex,
            weekDayMedals = medals,
            weekBronzeDays = medals.count { it >= MedalTier.BRONZE },
            weekSilverDays = medals.count { it >= MedalTier.SILVER },
            weekGoldDays = medals.count { it >= MedalTier.GOLD },
            dayKey = today
        )
    }

    private fun normalizeWeek(values: List<Int>): List<Int> = List(7) { i ->
        values.getOrNull(i)?.coerceIn(0, MedalTier.GOLD.ordinal) ?: 0
    }
}
