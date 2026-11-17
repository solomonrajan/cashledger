/*
 * Copyright (c) 2018.
 * Copyright (c) 2026 solomonrajan.
 *
 * This file is part of MoneyWallet.
 *
 * MoneyWallet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MoneyWallet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MoneyWallet.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oriondev.moneywallet.model

import com.oriondev.moneywallet.utils.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/**
 * Host-side coverage for occurrence computation.
 */
class RecurrenceSettingTest {

    @Test
    fun recurrenceSettingIsInstantiableInPlainJvm() {
        val setting = RecurrenceSetting(day(2020, 0, 1), "FREQ=DAILY")
        assertNotNull(setting.rule)
        val next = setting.getNextOccurrence(day(2020, 0, 1))
        assertDay(next, 2020, 0, 2)
    }

    @Test
    fun dailyAdvancementCollectsEveryDayUpToNow() {
        val update = RecurrenceSetting.computeOccurrences(
            "FREQ=DAILY", day(2020, 0, 1), day(2020, 0, 5)
        )
        val dates = update.occurrenceDates
        assertEquals(5, dates.size)
        assertDay(dates[0], 2020, 0, 1)
        assertDay(dates[1], 2020, 0, 2)
        assertDay(dates[2], 2020, 0, 3)
        assertDay(dates[3], 2020, 0, 4)
        assertDay(dates[4], 2020, 0, 5)
        assertDay(update.lastOccurrence, 2020, 0, 5)
        assertDay(update.nextOccurrence, 2020, 0, 6)
    }

    @Test
    fun weeklyAdvancementSteppsSevenDays() {
        val update = RecurrenceSetting.computeOccurrences(
            "FREQ=WEEKLY", day(2020, 0, 1), day(2020, 0, 20)
        )
        val dates = update.occurrenceDates
        assertEquals(3, dates.size)
        assertDay(dates[0], 2020, 0, 1)
        assertDay(dates[1], 2020, 0, 8)
        assertDay(dates[2], 2020, 0, 15)
        assertDay(update.lastOccurrence, 2020, 0, 15)
        assertDay(update.nextOccurrence, 2020, 0, 22)
    }

    @Test
    fun monthlyAdvancementSteppsOneMonth() {
        val update = RecurrenceSetting.computeOccurrences(
            "FREQ=MONTHLY", day(2020, 0, 15), day(2020, 3, 20)
        )
        val dates = update.occurrenceDates
        assertEquals(4, dates.size)
        assertDay(dates[0], 2020, 0, 15)
        assertDay(dates[1], 2020, 1, 15)
        assertDay(dates[2], 2020, 2, 15)
        assertDay(dates[3], 2020, 3, 15)
        assertDay(update.lastOccurrence, 2020, 3, 15)
        assertDay(update.nextOccurrence, 2020, 4, 15)
    }

    @Test
    fun occurrenceExactlyAtNowIsIncludedNextDayIsNext() {
        val update = RecurrenceSetting.computeOccurrences(
            "FREQ=DAILY", day(2020, 5, 10), day(2020, 5, 10)
        )
        val dates = update.occurrenceDates
        assertEquals(1, dates.size)
        assertDay(dates[0], 2020, 5, 10)
        assertDay(update.lastOccurrence, 2020, 5, 10)
        assertDay(update.nextOccurrence, 2020, 5, 11)
    }

    @Test
    fun monthEndRuleSkipsShortMonthsWithoutCrashing() {
        val update = RecurrenceSetting.computeOccurrences(
            "FREQ=MONTHLY;BYMONTHDAY=31", day(2020, 0, 31), day(2020, 6, 15)
        )
        val dates = update.occurrenceDates
        assertEquals(3, dates.size)
        assertDay(dates[0], 2020, 0, 31)
        assertDay(dates[1], 2020, 2, 31)
        assertDay(dates[2], 2020, 4, 31)
        assertDay(update.lastOccurrence, 2020, 4, 31)
        assertDay(update.nextOccurrence, 2020, 6, 31)
    }

    @Test
    fun countRuleExhaustingInsideWindowLeavesNoNextOccurrence() {
        val update = RecurrenceSetting.computeOccurrences(
            "FREQ=DAILY;COUNT=3", day(2020, 1, 1), day(2020, 1, 28)
        )
        val dates = update.occurrenceDates
        assertEquals(3, dates.size)
        assertDay(dates[0], 2020, 1, 1)
        assertDay(dates[1], 2020, 1, 2)
        assertDay(dates[2], 2020, 1, 3)
        assertDay(update.lastOccurrence, 2020, 1, 3)
        assertNull(update.nextOccurrence)
    }

    @Test
    fun untilRuleExhaustingInsideWindowLeavesNoNextOccurrence() {
        val update = RecurrenceSetting.computeOccurrences(
            "FREQ=WEEKLY;UNTIL=20200122", day(2020, 0, 1), day(2020, 11, 31)
        )
        val dates = update.occurrenceDates
        assertEquals(4, dates.size)
        assertDay(dates[0], 2020, 0, 1)
        assertDay(dates[1], 2020, 0, 8)
        assertDay(dates[2], 2020, 0, 15)
        assertDay(dates[3], 2020, 0, 22)
        assertDay(update.lastOccurrence, 2020, 0, 22)
        assertNull(update.nextOccurrence)
    }

    @Test
    fun seedInTheFutureYieldsNoOccurrencesAndKeepsSeedAsNext() {
        val update = RecurrenceSetting.computeOccurrences(
            "FREQ=MONTHLY", day(2020, 4, 10), day(2020, 4, 1)
        )
        assertTrue(update.occurrenceDates.isEmpty())
        assertDay(update.lastOccurrence, 2020, 4, 10)
        assertDay(update.nextOccurrence, 2020, 4, 10)
    }

    @Test
    fun invalidRuleYieldsNoOccurrencesAndNullNext() {
        val update = RecurrenceSetting.computeOccurrences(
            "this is not a rule", day(2020, 2, 3), day(2020, 2, 30)
        )
        assertTrue(update.occurrenceDates.isEmpty())
        assertDay(update.lastOccurrence, 2020, 2, 3)
        assertNull(update.nextOccurrence)
    }

    @Test
    fun anEditThatLeavesTheScheduleAloneKeepsAnAlreadyOwedPointer() {
        val owed = sql(day(2020, 1, 10))
        val resumed = RecurrenceSetting.resumeAfterEdit(
            "FREQ=MONTHLY", sql(day(2020, 0, 10)), owed,
            "FREQ=MONTHLY", sql(day(2020, 0, 10)), day(2020, 2, 5)
        )
        assertEquals(owed, resumed)
    }

    @Test
    fun anEditThatLeavesTheScheduleAloneKeepsAFuturePointerToo() {
        val pointer = sql(day(2020, 3, 10))
        val resumed = RecurrenceSetting.resumeAfterEdit(
            "FREQ=MONTHLY", sql(day(2020, 0, 10)), pointer,
            "FREQ=MONTHLY", sql(day(2020, 0, 10)), day(2020, 2, 5)
        )
        assertEquals(pointer, resumed)
    }

    @Test
    fun anEditThatLeavesTheScheduleAloneKeepsAnExhaustedPointer() {
        val resumed = RecurrenceSetting.resumeAfterEdit(
            "FREQ=DAILY;UNTIL=20200201", sql(day(2020, 0, 10)), null,
            "FREQ=DAILY;UNTIL=20200201", sql(day(2020, 0, 10)), day(2020, 2, 5)
        )
        assertNull(resumed)
    }

    @Test
    fun changingTheRuleMovesThePointerOntoTheNewCadence() {
        val resumed = RecurrenceSetting.resumeAfterEdit(
            "FREQ=DAILY", sql(day(2020, 0, 10)), sql(day(2020, 2, 6)),
            "FREQ=MONTHLY", sql(day(2020, 0, 10)), day(2020, 2, 5)
        )
        assertEquals(sql(day(2020, 2, 10)), resumed)
    }

    @Test
    fun changingOnlyTheStartDateAlsoMovesThePointer() {
        val resumed = RecurrenceSetting.resumeAfterEdit(
            "FREQ=MONTHLY", sql(day(2020, 0, 10)), sql(day(2020, 2, 10)),
            "FREQ=MONTHLY", sql(day(2020, 0, 20)), day(2020, 2, 5)
        )
        assertEquals(sql(day(2020, 2, 20)), resumed)
    }

    @Test
    fun changingTheRuleDropsAnOwedPointer() {
        val resumed = RecurrenceSetting.resumeAfterEdit(
            "FREQ=DAILY", sql(day(2020, 0, 10)), sql(day(2020, 1, 10)),
            "FREQ=MONTHLY", sql(day(2020, 0, 10)), day(2020, 2, 5)
        )
        assertEquals(sql(day(2020, 2, 10)), resumed)
    }

    @Test
    fun editingOntoAnAlreadyExhaustedRuleLeavesNoNextOccurrence() {
        val resumed = RecurrenceSetting.resumeAfterEdit(
            "FREQ=DAILY", sql(day(2020, 0, 10)), sql(day(2020, 2, 6)),
            "FREQ=DAILY;UNTIL=20200201", sql(day(2020, 0, 10)), day(2020, 2, 5)
        )
        assertNull(resumed)
    }

    @Test
    fun editingAScheduleThatHasNotStartedResumesOnItsStartDate() {
        val resumed = RecurrenceSetting.resumeAfterEdit(
            "FREQ=DAILY", sql(day(2020, 0, 10)), sql(day(2020, 4, 2)),
            "FREQ=MONTHLY", sql(day(2020, 5, 10)), day(2020, 4, 1)
        )
        assertEquals(sql(day(2020, 5, 10)), resumed)
    }

    @Test
    fun aMonthlyPeriodRunsFromItsAnchorDayToTheDayBeforeTheNextOne() {
        assertDay(RecurrenceSetting.periodEnd("FREQ=MONTHLY;BYMONTHDAY=15", day(2026, 0, 15), day(2026, 0, 15)), 2026, 1, 14)
    }

    @Test
    fun aMonthlyPeriodFollowsTheCalendarInsteadOfAFixedDayCount() {
        assertDay(RecurrenceSetting.periodEnd("FREQ=MONTHLY;BYMONTHDAY=15", day(2026, 1, 15), day(2026, 1, 15)), 2026, 2, 14)
    }

    @Test
    fun aWeeklyPeriodIsSevenDaysLong() {
        assertDay(RecurrenceSetting.periodEnd("FREQ=WEEKLY;BYDAY=MO", day(2026, 0, 5), day(2026, 0, 5)), 2026, 0, 11)
    }

    @Test
    fun anIntervalIsPartOfThePeriodLength() {
        assertDay(RecurrenceSetting.periodEnd("FREQ=MONTHLY;INTERVAL=3;BYMONTHDAY=1", day(2026, 0, 1), day(2026, 0, 1)), 2026, 2, 31)
    }

    @Test
    fun aPeriodEndsTheDayBeforeTheNextInstanceEvenAcrossAYear() {
        assertDay(RecurrenceSetting.periodEnd("FREQ=YEARLY", day(2026, 5, 30), day(2026, 5, 30)), 2027, 5, 29)
    }

    @Test
    fun aRuleThatNeverRepeatsSetsNoPeriodLength() {
        assertNull(RecurrenceSetting.periodEnd("FREQ=MONTHLY;COUNT=1", day(2026, 0, 15), day(2026, 0, 15)))
    }

    @Test
    fun anUnparseableRuleSetsNoPeriodLength() {
        assertNull(RecurrenceSetting.periodEnd("this is not a rule", day(2026, 0, 15), day(2026, 0, 15)))
    }

    @Test
    fun theNextInstanceIsStrictlyAfterTheOneItIsWalkedFrom() {
        assertDay(RecurrenceSetting.nextInstanceAfter("FREQ=MONTHLY;BYMONTHDAY=15", day(2026, 0, 15), day(2026, 0, 15)), 2026, 1, 15)
    }

    @Test
    fun aMonthDayThatSomeMonthsDoNotHaveFallsBackToTheLastDayOfTheMonth() {
        val rule = monthlyOn(day(2026, 0, 31))
        assertDay(RecurrenceSetting.nextInstanceAfter(rule, day(2026, 0, 31), day(2026, 0, 31)), 2026, 1, 28)
        assertDay(RecurrenceSetting.periodEnd(rule, day(2026, 0, 31), day(2026, 0, 31)), 2026, 1, 27)
    }

    @Test
    fun everyMonthOfAYearIsCoveredWhenTheAnchorIsTheThirtyFirst() {
        val rule = monthlyOn(day(2026, 0, 31))
        var instance: Date? = day(2026, 0, 31)
        val months = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
        for (month in months) {
            instance = RecurrenceSetting.nextInstanceAfter(rule, day(2026, 0, 31), instance)
            assertNotNull(instance)
            val nonNullInstance = instance ?: return
            val calendar = Calendar.getInstance()
            calendar.time = nonNullInstance
            assertEquals(month, calendar.get(Calendar.MONTH))
        }
    }

    @Test
    fun anAnchorTheMonthAlwaysHasIsUnchanged() {
        val rule = monthlyOn(day(2026, 0, 15))
        assertDay(RecurrenceSetting.nextInstanceAfter(rule, day(2026, 0, 15), day(2026, 0, 15)), 2026, 1, 15)
        assertDay(RecurrenceSetting.periodEnd(rule, day(2026, 0, 15), day(2026, 0, 15)), 2026, 1, 14)
    }

    @Test
    fun theFirstAndTheTwentyEighthAreUnchangedToo() {
        assertDay(RecurrenceSetting.nextInstanceAfter(monthlyOn(day(2026, 0, 1)), day(2026, 0, 1), day(2026, 0, 1)), 2026, 1, 1)
        assertDay(RecurrenceSetting.nextInstanceAfter(monthlyOn(day(2026, 0, 28)), day(2026, 0, 28), day(2026, 0, 28)), 2026, 1, 28)
        assertDay(RecurrenceSetting.nextInstanceAfter(monthlyOn(day(2026, 1, 28)), day(2026, 1, 28), day(2026, 1, 28)), 2026, 2, 28)
    }

    @Test
    fun anAnchorEveryMonthHasBuildsTheRuleItAlwaysBuilt() {
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=1", monthlyOn(day(2026, 0, 1)))
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=15", monthlyOn(day(2026, 0, 15)))
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=28", monthlyOn(day(2026, 0, 28)))
    }

    @Test
    fun anAnchorSomeMonthsLackAsksForTheLastDayAsWell() {
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=29,-1;BYSETPOS=1", monthlyOn(day(2026, 0, 29)))
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=30,-1;BYSETPOS=1", monthlyOn(day(2026, 0, 30)))
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=31,-1;BYSETPOS=1", monthlyOn(day(2026, 0, 31)))
    }

    @Test
    fun aYearlyScheduleAnchoredToALeapDayComesRoundEveryYear() {
        val rule = yearlyOn(day(2024, 1, 29))
        assertEquals("FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=29,-1;BYSETPOS=1", rule)
        assertDay(RecurrenceSetting.nextInstanceAfter(rule, day(2024, 1, 29), day(2024, 1, 29)), 2025, 1, 28)
        assertDay(RecurrenceSetting.periodEnd(rule, day(2024, 1, 29), day(2024, 1, 29)), 2025, 1, 27)
    }

    @Test
    fun aYearlyScheduleAnchoredAnywhereElseBuildsTheRuleItAlwaysBuilt() {
        assertEquals("FREQ=YEARLY", yearlyOn(day(2026, 5, 30)))
        assertEquals("FREQ=YEARLY", yearlyOn(day(2026, 1, 28)))
        assertEquals("FREQ=YEARLY", yearlyOn(day(2026, 0, 31)))
    }

    @Test
    fun aYearlyPeriodIsAYearEvenAcrossALeapDay() {
        assertDay(RecurrenceSetting.periodEnd(yearlyOn(day(2027, 1, 28)), day(2027, 1, 28), day(2027, 1, 28)), 2028, 1, 27)
    }

    @Test
    fun aWeeklyScheduleKeepsItsWeekdayWhateverDayThePeriodStartsOn() {
        val rule = "FREQ=WEEKLY"
        assertDay(RecurrenceSetting.nextInstanceAfter(rule, day(2026, 7, 26), day(2026, 7, 3)), 2026, 7, 26)
        assertDay(RecurrenceSetting.nextInstanceAfter(rule, day(2026, 7, 26), day(2026, 7, 26)), 2026, 8, 2)
        assertDay(RecurrenceSetting.nextInstanceAfter(rule, day(2026, 7, 26), day(2026, 8, 2)), 2026, 8, 9)
    }

    @Test
    fun aScheduleThatRepeatsEveryOtherMonthKeepsItsMonths() {
        val rule = "FREQ=MONTHLY;INTERVAL=2;BYMONTHDAY=15"
        assertDay(RecurrenceSetting.nextInstanceAfter(rule, day(2026, 0, 15), day(2026, 5, 3)), 2026, 6, 15)
        assertDay(RecurrenceSetting.nextInstanceAfter(rule, day(2026, 0, 15), day(2026, 6, 15)), 2026, 8, 15)
    }

    @Test
    fun aYearlyScheduleKeepsItsDayWhateverDayThePeriodStartsOn() {
        val rule = "FREQ=YEARLY"
        assertDay(RecurrenceSetting.nextInstanceAfter(rule, day(2027, 0, 1), day(2026, 7, 3)), 2027, 0, 1)
        assertDay(RecurrenceSetting.nextInstanceAfter(rule, day(2027, 0, 1), day(2027, 0, 1)), 2028, 0, 1)
    }

    @Test
    fun aPeriodStartingOffTheScheduleStillEndsWhereTheScheduleSays() {
        assertDay(RecurrenceSetting.periodEnd("FREQ=MONTHLY;BYMONTHDAY=15", day(2026, 0, 15), day(2026, 7, 3)), 2026, 7, 14)
    }

    @Test
    fun aScheduleAnchoredInThePastLandsOnThePeriodItIsInToday() {
        val period = RecurrenceSetting.periodOn("FREQ=MONTHLY;BYMONTHDAY=15", day(2025, 5, 15), day(2026, 7, 24))
        assertNotNull(period)
        val nonNullPeriod = period ?: return
        assertDay(nonNullPeriod[0], 2026, 7, 15)
        assertDay(nonNullPeriod[1], 2026, 8, 14)
    }

    @Test
    fun aScheduleAnchoredTodayStaysOnItsFirstPeriod() {
        val period = RecurrenceSetting.periodOn("FREQ=MONTHLY;BYMONTHDAY=24", day(2026, 7, 24), day(2026, 7, 24))
        assertNotNull(period)
        val nonNullPeriod = period ?: return
        assertDay(nonNullPeriod[0], 2026, 7, 24)
        assertDay(nonNullPeriod[1], 2026, 8, 23)
    }

    @Test
    fun aScheduleAnchoredInTheFutureIsNotDraggedForward() {
        val period = RecurrenceSetting.periodOn("FREQ=MONTHLY;BYMONTHDAY=1", day(2026, 11, 1), day(2026, 7, 24))
        assertNotNull(period)
        val nonNullPeriod = period ?: return
        assertDay(nonNullPeriod[0], 2026, 11, 1)
        assertDay(nonNullPeriod[1], 2026, 11, 31)
    }

    @Test
    fun thePeriodEndingTodayIsStillTheCurrentOne() {
        val period = RecurrenceSetting.periodOn("FREQ=MONTHLY;BYMONTHDAY=25", day(2026, 6, 25), day(2026, 7, 24))
        assertNotNull(period)
        val nonNullPeriod = period ?: return
        assertDay(nonNullPeriod[0], 2026, 6, 25)
        assertDay(nonNullPeriod[1], 2026, 7, 24)
    }

    @Test
    fun thePeriodThatEndedYesterdayIsNotTheCurrentOne() {
        val period = RecurrenceSetting.periodOn("FREQ=MONTHLY;BYMONTHDAY=24", day(2026, 6, 24), day(2026, 7, 24))
        assertNotNull(period)
        val nonNullPeriod = period ?: return
        assertDay(nonNullPeriod[0], 2026, 7, 24)
        assertDay(nonNullPeriod[1], 2026, 8, 23)
    }

    @Test
    fun aRuleThatRunsOutBeforeTodayHasNoCurrentPeriod() {
        assertNull(RecurrenceSetting.periodOn("FREQ=MONTHLY;COUNT=2", day(2025, 0, 15), day(2026, 7, 24)))
    }

    @Test
    fun aRuleThatNeverRepeatsHasNoCurrentPeriod() {
        assertNull(RecurrenceSetting.periodOn("FREQ=MONTHLY;COUNT=1", day(2026, 7, 24), day(2026, 7, 24)))
    }

    @Test
    fun anUnparseableRuleHasNoCurrentPeriod() {
        assertNull(RecurrenceSetting.periodOn("this is not a rule", day(2026, 7, 24), day(2026, 7, 24)))
    }

    companion object {
        private fun day(year: Int, month: Int, dayOfMonth: Int): Date {
            return DateUtils.getDate(year, month, dayOfMonth)
        }

        private fun assertDay(date: Date?, year: Int, month: Int, dayOfMonth: Int) {
            assertNotNull(date)
            val nonNullDate = date ?: return
            val calendar = Calendar.getInstance()
            calendar.time = nonNullDate
            assertEquals(year, calendar.get(Calendar.YEAR))
            assertEquals(month, calendar.get(Calendar.MONTH))
            assertEquals(dayOfMonth, calendar.get(Calendar.DAY_OF_MONTH))
        }

        private fun sql(date: Date): String {
            return DateUtils.getSQLDateString(date)
        }

        private fun yearlyOn(startDate: Date): String {
            val builder = RecurrenceSetting.Builder(startDate, RecurrenceSetting.TYPE_YEARLY)
            builder.setRepeatSameYearDay()
            return builder.build().rule
        }

        private fun monthlyOn(startDate: Date): String {
            val builder = RecurrenceSetting.Builder(startDate, RecurrenceSetting.TYPE_MONTHLY)
            builder.setRepeatSameMonthDay()
            return builder.build().rule
        }
    }
}
