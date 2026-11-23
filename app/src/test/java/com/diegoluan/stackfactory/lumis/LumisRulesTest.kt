package com.diegoluan.stackfactory.lumis

import com.diegoluan.stackfactory.checkin.MedalTier
import org.junit.Assert.assertEquals
import org.junit.Test

class LumisRulesTest {
    @Test fun dailyMedalBoundaries() {
        assertEquals(0, LumisRules.totalDailyUnits(MedalTier.fromScore(39)))
        assertEquals(10, LumisRules.totalDailyUnits(MedalTier.fromScore(40)))
        assertEquals(20, LumisRules.totalDailyUnits(MedalTier.fromScore(75)))
        assertEquals(30, LumisRules.totalDailyUnits(MedalTier.fromScore(100)))
    }

    @Test fun sameDayUpgradeOnlyGrantsDifference() {
        assertEquals(10, LumisRules.dailyUpgradeUnits(0, MedalTier.BRONZE))
        assertEquals(10, LumisRules.dailyUpgradeUnits(10, MedalTier.SILVER))
        assertEquals(10, LumisRules.dailyUpgradeUnits(20, MedalTier.GOLD))
        assertEquals(0, LumisRules.dailyUpgradeUnits(30, MedalTier.BRONZE))
        assertEquals(0, LumisRules.dailyUpgradeUnits(30, MedalTier.GOLD))
    }

    @Test fun sevenDayBonusesHaveNoRepeatOnOtherDays() {
        assertEquals(0, LumisRules.streakBonusUnits(6, true))
        assertEquals(50, LumisRules.streakBonusUnits(7, true))
        assertEquals(0, LumisRules.streakBonusUnits(7, false))
        assertEquals(0, LumisRules.streakBonusUnits(8, true))
        assertEquals(50, LumisRules.streakBonusUnits(14, true))
    }

    @Test fun amountsDisplayInLumisWithoutFloatingPoint() {
        assertEquals("0,00", LumisRules.formatUnits(0))
        assertEquals("0,10", LumisRules.formatUnits(10))
        assertEquals("2,60", LumisRules.formatUnits(260))
        assertEquals("-0,50", LumisRules.formatUnits(-50))
    }
}
