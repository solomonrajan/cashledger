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

package com.oriondev.moneywallet.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DateUtilsTest {

    private val systemTimeZone: TimeZone = TimeZone.getDefault()

    @After
    fun restoreSystemTimeZone() {
        TimeZone.setDefault(systemTimeZone)
    }

    @Test
    fun countsThePositionThatRendersTheDayAsked() {
        for (zone in ZONES) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            val day = Calendar.getInstance()
            for (position in 0..LAST_POSITION) {
                day.set(1900, Calendar.JANUARY, 1, 1, 0, 0)
                day.add(Calendar.DAY_OF_YEAR, position)
                assertEquals(
                    zone,
                    position,
                    DateUtils.getCalendarDaysBetween(
                        1900,
                        Calendar.JANUARY,
                        1,
                        day.get(Calendar.YEAR),
                        day.get(Calendar.MONTH),
                        day.get(Calendar.DAY_OF_MONTH)
                    )
                )
            }
        }
    }

    @Test
    fun countsWholeDays() {
        assertEquals(130, DateUtils.getCalendarDaysBetween(2026, Calendar.AUGUST, 24, 2027, Calendar.JANUARY, 1))
        assertEquals(0, DateUtils.getCalendarDaysBetween(2027, Calendar.JANUARY, 1, 2027, Calendar.JANUARY, 1))
        assertEquals(1, DateUtils.getCalendarDaysBetween(2026, Calendar.DECEMBER, 31, 2027, Calendar.JANUARY, 1))
        assertEquals(-1, DateUtils.getCalendarDaysBetween(2027, Calendar.JANUARY, 1, 2026, Calendar.DECEMBER, 31))
        assertEquals(365, DateUtils.getCalendarDaysBetween(2026, Calendar.JANUARY, 1, 2027, Calendar.JANUARY, 1))
        assertEquals(366, DateUtils.getCalendarDaysBetween(2024, Calendar.JANUARY, 1, 2025, Calendar.JANUARY, 1))
        assertEquals(127 * 365 + ((2024 - 1904) / 4 + 1), DateUtils.getCalendarDaysBetween(1900, Calendar.JANUARY, 1, 2027, Calendar.JANUARY, 1))
    }

    companion object {
        private val ZONES = arrayOf(
            "Australia/Sydney", "Europe/Paris", "Europe/Madrid", "America/Chicago", "UTC"
        )
        private const val LAST_POSITION = 73413
    }
}
