package com.diegoluan.stackfactory.checkin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiveDayCheckInRulesTest {
    private val monday = "2026-11-16"
    private val sunday = "2026-11-15"
    private val week = "2026-11-16"

    private fun score(
        record: CheckInRecord,
        value: Int,
        today: String = monday,
        yesterday: String = sunday,
        weekKey: String = week,
        dayIndex: Int = 1
    ) = FiveDayCheckInRules.recordScore(record, value, today, yesterday, weekKey, dayIndex)

    private fun view(
        record: CheckInRecord,
        today: String = monday,
        yesterday: String = sunday,
        weekKey: String = week,
        dayIndex: Int = 1
    ) = FiveDayCheckInRules.view(record, today, yesterday, weekKey, dayIndex)

    @Test fun boundaryPoints() {
        assertEquals(MedalTier.NONE, MedalTier.fromScore(-10))
        assertEquals(MedalTier.NONE, MedalTier.fromScore(0))
        assertEquals(MedalTier.NONE, MedalTier.fromScore(39))
        assertEquals(MedalTier.BRONZE, MedalTier.fromScore(40))
        assertEquals(MedalTier.BRONZE, MedalTier.fromScore(74))
        assertEquals(MedalTier.SILVER, MedalTier.fromScore(75))
        assertEquals(MedalTier.SILVER, MedalTier.fromScore(99))
        assertEquals(MedalTier.GOLD, MedalTier.fromScore(100))
        assertEquals(MedalTier.GOLD, MedalTier.fromScore(101))
    }

    @Test fun openingAppOrUnder40DoesNotCheckIn() {
        assertFalse(view(CheckInRecord()).completedToday)
        val record = score(CheckInRecord(), 39)
        assertFalse(view(record).completedToday)
        assertEquals(0, record.streak)
    }

    @Test fun upgradingMedalsInSameDayDoesNotIncrementStreakAgain() {
        var record = score(CheckInRecord(), 40)
        assertEquals(MedalTier.BRONZE, view(record).todayMedal)
        record = score(record, 75)
        assertEquals(MedalTier.SILVER, view(record).todayMedal)
        record = score(record, 100)
        assertEquals(MedalTier.GOLD, view(record).todayMedal)
        assertEquals(1, view(record).consecutiveDays)
        assertEquals(1, view(record).weekBronzeDays)
        assertEquals(1, view(record).weekSilverDays)
        assertEquals(1, view(record).weekGoldDays)
        assertEquals(0, view(record).pointsToNextTier)
    }

    @Test fun scoresFromSeparateGamesNeverAddUp() {
        var record = score(CheckInRecord(), 30)
        record = score(record, 30)
        assertEquals(30, view(record).bestScoreToday)
        assertFalse(view(record).completedToday)
    }

    @Test fun lowerScoreCannotReduceMedal() {
        var record = score(CheckInRecord(), 100)
        record = score(record, 20)
        assertEquals(MedalTier.GOLD, view(record).todayMedal)
        assertEquals(100, view(record).bestScoreToday)
    }

    @Test fun consecutiveDaysAdvanceOnceAndExpireAfterMissedDay() {
        var record = score(CheckInRecord(), 40)
        record = score(record, 40, "2026-11-17", monday, week, 2)
        assertEquals(2, view(record, "2026-11-17", monday, week, 2).consecutiveDays)
        assertEquals(0, view(record, "2026-11-19", "2026-11-18", week, 4).consecutiveDays)
        record = score(record, 40, "2026-11-19", "2026-11-18", week, 4)
        assertEquals(1, view(record, "2026-11-19", "2026-11-18", week, 4).consecutiveDays)
    }

    @Test fun mondayResetsWeeklyMedalsButNotStreak() {
        var record = score(CheckInRecord(), 100, sunday, "2026-11-14", "2026-11-09", 7)
        record = score(record, 40, monday, sunday, week, 1)
        val result = view(record)
        assertEquals(1, result.weekBronzeDays)
        assertEquals(0, result.weekGoldDays)
        assertEquals(2, result.consecutiveDays)
    }

    @Test fun allSevenGoldDaysCompleteWeeklyGoal() {
        var record = CheckInRecord()
        val days = listOf(
            "2026-11-16", "2026-11-17", "2026-11-18", "2026-11-19",
            "2026-11-20", "2026-11-21", "2026-11-22"
        )
        days.forEachIndexed { index, day ->
            val previous = if (index == 0) sunday else days[index - 1]
            record = score(record, 100, day, previous, week, index + 1)
        }
        val result = view(record, "2026-11-22", "2026-11-21", week, 7)
        assertTrue(result.weeklyCompleted)
        assertEquals(7, result.weekBronzeDays)
        assertEquals(7, result.weekSilverDays)
        assertEquals(7, result.weekGoldDays)
        assertEquals(7, result.completedDaysOfSeven)
    }

    @Test fun oldBestScoreProducesMedalWithoutInventingPreviousWeek() {
        val legacy = CheckInRecord(
            completedDay = monday, streak = 3,
            bestScoreDay = monday, bestScore = 180
        )
        val result = view(legacy)
        assertEquals(MedalTier.GOLD, result.todayMedal)
        assertEquals(1, result.weekBronzeDays)
        assertEquals(1, result.weekGoldDays)
        assertEquals(3, result.consecutiveDays)
    }

    @Test fun badStoredMedalsAreClamped() {
        val legacy = CheckInRecord(weekKey = week, weekDayMedals = listOf(50, -1, 3))
        val result = view(legacy)
        assertEquals(MedalTier.GOLD, result.weekDayMedals[0])
        assertEquals(MedalTier.NONE, result.weekDayMedals[1])
        assertEquals(MedalTier.GOLD, result.weekDayMedals[2])
        assertEquals(7, result.weekDayMedals.size)
    }

    @Test fun nextTierCountsRemainingPoints() {
        val result = view(score(CheckInRecord(), 53))
        assertEquals(MedalTier.SILVER, result.nextTier)
        assertEquals(22, result.pointsToNextTier)
    }

    @Test fun sevenDayStreakContinuesBeyondTarget() {
        var record = CheckInRecord()
        val days = listOf(
            "2026-11-16", "2026-11-17", "2026-11-18", "2026-11-19",
            "2026-11-20", "2026-11-21", "2026-11-22", "2026-11-23"
        )
        days.forEachIndexed { index, day ->
            val yesterdayKey = if (index == 0) sunday else days[index - 1]
            val weekKey = if (index < 7) week else "2026-11-23"
            val dayIndex = index % 7 + 1
            record = score(record, 40, day, yesterdayKey, weekKey, dayIndex)
        }
        val view = view(record, "2026-11-23", "2026-11-22", "2026-11-23", 1)
        assertEquals(8, view.consecutiveDays)
        assertEquals(7, view.completedDaysOfSeven)
        assertEquals(1, view.weekBronzeDays)
        assertTrue(view.completedToday)
    }

    @Test fun missingOneDayBreaksStreakButKeepsPriorWeeklyMedals() {
        var record = score(CheckInRecord(), 40, monday, sunday, week, 1)
        record = score(record, 75, "2026-11-17", monday, week, 2)
        val missed = view(record, "2026-11-19", "2026-11-18", week, 4)
        assertEquals(0, missed.consecutiveDays)
        assertEquals(2, missed.weekBronzeDays)
        record = score(record, 75, "2026-11-19", "2026-11-18", week, 4)
        val resumed = view(record, "2026-11-19", "2026-11-18", week, 4)
        assertEquals(1, resumed.consecutiveDays)
        assertEquals(3, resumed.weekBronzeDays)
    }

    @Test fun reachingMilestoneNeverAddsMultipleDaysInOneDay() {
        var record = CheckInRecord()
        record = score(record, 40)
        record = score(record, 75)
        record = score(record, 100)
        record = score(record, 140)
        assertEquals(1, view(record).consecutiveDays)
        assertEquals(1, view(record).completedDaysOfSeven)
        assertEquals(1, view(record).weekBronzeDays)
    }

    @Test fun olderStreakOfFiveIsPreservedOnUpgrade() {
        val old = CheckInRecord(completedDay = sunday, streak = 5)
        val updated = score(old, 40)
        assertEquals(6, view(updated).consecutiveDays)
        assertEquals(6, view(updated).completedDaysOfSeven)
    }
}
