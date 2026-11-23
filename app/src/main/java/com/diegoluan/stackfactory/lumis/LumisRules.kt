package com.diegoluan.stackfactory.lumis

import com.diegoluan.stackfactory.checkin.MedalTier

/**
 * Lumis sao uma recompensa virtual gratuita, sem conversao em dinheiro.
 * Todos os valores sao armazenados em centilumis inteiros (1 LM = 100 unidades).
 * Nenhum calculo financeiro usa Float ou Double.
 */
object LumisRules {
    const val UNITS_PER_LUMIS = 100
    const val SEVEN_DAY_BONUS_UNITS = 50

    fun totalDailyUnits(medal: MedalTier): Int = when (medal) {
        MedalTier.NONE -> 0
        MedalTier.BRONZE -> 10
        MedalTier.SILVER -> 20
        MedalTier.GOLD -> 30
    }

    /** O upgrade recebe apenas a diferenca, nunca a soma das tres medalhas. */
    fun dailyUpgradeUnits(previousAwardedUnits: Int, medal: MedalTier): Int =
        (totalDailyUnits(medal) - previousAwardedUnits.coerceAtLeast(0)).coerceAtLeast(0)

    /** Um bonus a cada 7 dias consecutivos, incluido 7, 14, 21... */
    fun streakBonusUnits(streakDays: Int, hasDailyMedal: Boolean): Int =
        if (hasDailyMedal && streakDays >= 7 && streakDays % 7 == 0) {
            SEVEN_DAY_BONUS_UNITS
        } else {
            0
        }

    fun formatUnits(units: Long): String {
        val negative = units < 0
        val safe = if (negative) -units else units
        val integer = safe / UNITS_PER_LUMIS
        val fraction = safe % UNITS_PER_LUMIS
        return (if (negative) "-" else "") + integer + "," + fraction.toString().padStart(2, '0')
    }
}
