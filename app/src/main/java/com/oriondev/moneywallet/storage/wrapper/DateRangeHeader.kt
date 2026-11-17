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

package com.oriondev.moneywallet.storage.wrapper

import com.oriondev.moneywallet.model.Group
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.utils.DateUtils
import java.util.Calendar
import java.util.Date

/**
 * Created by andrea on 16/08/18.
 */
open class DateRangeHeader(
    group: Group,
    lowerBound: Date?,
    upperBound: Date?,
    date: Date
) {

    val startDate: Date
    val endDate: Date

    init {
        val calendar = Calendar.getInstance().apply {
            time = date
        }
        var start: Date
        var end: Date
        when (group) {
            Group.DAILY -> {
                // the start date and the end dates are the same day.
                start = calendar.time
                end = calendar.time
            }
            Group.WEEKLY -> {
                // the start date is the first day of the week where the current day is included.
                // step 1: we can start checking if the current day is before or after the
                // 'first day of week' preferred by the user.
                val firstDayOfWeek = PreferenceManager.getFirstDayOfWeek()
                val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                // step 2: we move the current day of week to the first day of week
                calendar.firstDayOfWeek = Calendar.SUNDAY
                calendar.set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                // step 3: if the current day of week was before the first day of week we must
                // move one week before to not chose the incorrect week.
                if (currentDayOfWeek < firstDayOfWeek) {
                    calendar.add(Calendar.DAY_OF_MONTH, -7)
                }
                // step 4: the last day of week is exactly 6 days later.
                start = calendar.time
                end = DateUtils.addDays(calendar, 6)
            }
            Group.MONTHLY -> {
                // the start date is the first day of the month where the current day is included.
                // step 1: we can start checking if the current day is before or after the
                // 'first day of month' preferred by the user.
                val firstDayOfMonth = PreferenceManager.getFirstDayOfMonth()
                val currentDayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
                // step 2: we move the current day of week to the first day of month
                calendar.set(Calendar.DAY_OF_MONTH, firstDayOfMonth)
                // step 3: if the current day of month was before the first day of month we must
                // move one month before to not chose the incorrect month.
                if (currentDayOfMonth < firstDayOfMonth) {
                    calendar.add(Calendar.MONTH, -1)
                }
                // step 4: the last day of month is exactly 1 month later (and 1 day before).
                start = calendar.time
                end = DateUtils.addMonthAndDay(calendar, 1, -1)
            }
            Group.YEARLY -> {
                // the start date and the end dates are the first and the last day of the year.
                start = DateUtils.setDayAndMonth(calendar, 1, Calendar.JANUARY)
                end = DateUtils.setDayAndMonth(calendar, 31, Calendar.DECEMBER)
            }
        }
        start = DateUtils.setTime(start, 0, 0, 0, 0)
        end = DateUtils.setTime(end, 23, 59, 59, 999)
        // we have to ensure that if a lower bound or an upper bound is provided we are not
        // going outside the limits of range:
        if (lowerBound != null && DateUtils.isBefore(start, lowerBound)) {
            start = lowerBound
        }
        if (upperBound != null && DateUtils.isAfter(end, upperBound)) {
            end = upperBound
        }
        startDate = start
        endDate = end
    }

    fun isInBounds(date: Date): Boolean {
        return DateUtils.isAfterEqual(date, startDate) && DateUtils.isBeforeEqual(date, endDate)
    }
}
