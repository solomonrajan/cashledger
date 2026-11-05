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

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.DateUtils
import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.Weekday
import org.dmfs.rfc5545.recur.Freq
import org.dmfs.rfc5545.recur.InvalidRecurrenceRuleException
import org.dmfs.rfc5545.recur.RecurrenceRule
import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import java.util.Objects

/**
 * Created by andrea on 07/11/18.
 */
class RecurrenceSetting : Parcelable {

    private val mStartDate: Date
    private val mRecurrenceRule: RecurrenceRule

    constructor(startDate: Date, type: Int) {
        mStartDate = startDate
        mRecurrenceRule = RecurrenceRule(getFreq(type))
    }

    constructor(startDate: Date, rule: String) {
        mStartDate = startDate
        mRecurrenceRule = try {
            RecurrenceRule(rule)
        } catch (ignore: InvalidRecurrenceRuleException) {
            RecurrenceRule(Freq.DAILY)
        }
    }

    private constructor(startDate: Date, recurrenceRule: RecurrenceRule) {
        mStartDate = startDate
        mRecurrenceRule = recurrenceRule
    }

    private constructor(parcel: Parcel) {
        mStartDate = (parcel.readSerializable() as? Date) ?: Date()
        mRecurrenceRule = try {
            RecurrenceRule(parcel.readString() ?: "")
        } catch (e: InvalidRecurrenceRuleException) {
            throw RuntimeException(e)
        }
    }

    fun getType(): Int {
        return when (mRecurrenceRule.freq) {
            Freq.DAILY -> TYPE_DAILY
            Freq.WEEKLY -> TYPE_WEEKLY
            Freq.MONTHLY -> TYPE_MONTHLY
            Freq.YEARLY -> TYPE_YEARLY
            else -> TYPE_DAILY
        }
    }

    fun getStartDate(): Date = mStartDate

    fun getOffsetValue(): Int = mRecurrenceRule.interval

    fun getWeekDays(): BooleanArray {
        val weekDays = booleanArrayOf(false, false, false, false, false, false, false)
        val weekdayNumList = mRecurrenceRule.byDayPart
        if (weekdayNumList != null && weekdayNumList.isNotEmpty()) {
            for (weekdayNum in weekdayNumList) {
                when (weekdayNum.weekday) {
                    Weekday.SU -> weekDays[0] = true
                    Weekday.MO -> weekDays[1] = true
                    Weekday.TU -> weekDays[2] = true
                    Weekday.WE -> weekDays[3] = true
                    Weekday.TH -> weekDays[4] = true
                    Weekday.FR -> weekDays[5] = true
                    Weekday.SA -> weekDays[6] = true
                    else -> Unit
                }
            }
        } else {
            val calendar = Calendar.getInstance()
            calendar.time = mStartDate
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            weekDays[dayOfWeek - 1] = true
        }
        return weekDays
    }

    fun getMonthDay(): Int = FLAG_MONTHLY_SAME_DAY

    fun getEndType(): Int {
        return if (mRecurrenceRule.isInfinite) {
            END_FOREVER
        } else if (mRecurrenceRule.until != null) {
            END_UNTIL
        } else {
            END_FOR
        }
    }

    fun getEndDate(): Date {
        val dateTime = mRecurrenceRule.until
        return if (dateTime != null) Date(dateTime.timestamp) else mStartDate
    }

    fun getOccurrenceValue(): Int {
        val count = mRecurrenceRule.count
        return count ?: 1
    }

    fun getRule(): String = mRecurrenceRule.toString()

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeSerializable(mStartDate)
        dest.writeString(mRecurrenceRule.toString())
    }

    fun getUserReadableString(context: Context): String {
        val builder = StringBuilder()
        val offset = mRecurrenceRule.interval
        when (mRecurrenceRule.freq) {
            Freq.DAILY -> {
                if (offset <= 1) {
                    builder.append(context.getString(R.string.recurrence_hint_repeat_every_day))
                } else {
                    builder.append(context.getString(R.string.recurrence_hint_repeat_every_n_days, offset))
                }
            }
            Freq.WEEKLY -> {
                if (offset <= 1) {
                    builder.append(context.getString(R.string.recurrence_hint_repeat_every_week))
                } else {
                    builder.append(context.getString(R.string.recurrence_hint_repeat_every_n_weeks, offset))
                }
                val weekDays = getWeekDays()
                if (weekDays[0]) { builder.append(context.getString(R.string.recurrence_hint_repeat_weekly_sunday)) }
                if (weekDays[1]) { builder.append(context.getString(R.string.recurrence_hint_repeat_weekly_monday)) }
                if (weekDays[2]) { builder.append(context.getString(R.string.recurrence_hint_repeat_weekly_tuesday)) }
                if (weekDays[3]) { builder.append(context.getString(R.string.recurrence_hint_repeat_weekly_wednesday)) }
                if (weekDays[4]) { builder.append(context.getString(R.string.recurrence_hint_repeat_weekly_thursday)) }
                if (weekDays[5]) { builder.append(context.getString(R.string.recurrence_hint_repeat_weekly_friday)) }
                if (weekDays[6]) { builder.append(context.getString(R.string.recurrence_hint_repeat_weekly_saturday)) }
            }
            Freq.MONTHLY -> {
                if (offset <= 1) {
                    builder.append(context.getString(R.string.recurrence_hint_repeat_every_month))
                } else {
                    builder.append(context.getString(R.string.recurrence_hint_repeat_every_n_months, offset))
                }
            }
            Freq.YEARLY -> {
                if (offset <= 1) {
                    builder.append(context.getString(R.string.recurrence_hint_repeat_every_year))
                } else {
                    builder.append(context.getString(R.string.recurrence_hint_repeat_every_n_years, offset))
                }
            }
            else -> Unit
        }
        val formattedStartDate = DateFormatter.getFormattedDate(mStartDate)
        builder.append(context.getString(R.string.recurrence_hint_repeat_starting_from, formattedStartDate))
        if (mRecurrenceRule.isInfinite) {
            builder.append(context.getString(R.string.recurrence_hint_repeat_forever))
        } else if (mRecurrenceRule.until != null) {
            val endDate = Date(mRecurrenceRule.until.timestamp)
            val formattedEndDate = DateFormatter.getFormattedDate(endDate)
            builder.append(context.getString(R.string.recurrence_hint_repeat_until, formattedEndDate))
        } else {
            val occurrences = mRecurrenceRule.count
            if (occurrences <= 1) {
                builder.append(context.getString(R.string.recurrence_hint_repeat_for_one_occurrence))
            } else {
                builder.append(context.getString(R.string.recurrence_hint_repeat_for_n_occurrence, occurrences))
            }
        }
        return builder.toString()
    }

    fun getNextOccurrence(lastOccurrence: Date): Date? {
        val startDateTime = DateUtils.getFixedDateTime(mStartDate)
        val lastOccurrenceDateTime = DateUtils.getFixedDateTime(lastOccurrence)
        var nextOccurrenceDateTime: DateTime? = null
        val iterator = mRecurrenceRule.iterator(startDateTime)
        while (iterator.hasNext()) {
            val nextInstance = iterator.nextDateTime()
            if (nextInstance.after(lastOccurrenceDateTime)) {
                nextOccurrenceDateTime = nextInstance
                break
            }
        }
        return if (nextOccurrenceDateTime != null) DateUtils.getFixedDate(nextOccurrenceDateTime) else null
    }

    class OccurrenceUpdate(
        val occurrenceDates: List<Date>,
        val lastOccurrence: Date,
        val nextOccurrence: Date?
    )

    class Builder(startDate: Date, type: Int) {
        private val mStartDate: Date = startDate
        private val mRecurrenceRule: RecurrenceRule = RecurrenceRule(getFreq(type))

        fun setOffset(offset: Int) {
            mRecurrenceRule.interval = offset
        }

        fun setRepeatWeekDay(weekDays: BooleanArray) {
            val weekdayNumList: MutableList<RecurrenceRule.WeekdayNum> = ArrayList()
            if (weekDays[0]) { weekdayNumList.add(RecurrenceRule.WeekdayNum(0, Weekday.SU)) }
            if (weekDays[1]) { weekdayNumList.add(RecurrenceRule.WeekdayNum(0, Weekday.MO)) }
            if (weekDays[2]) { weekdayNumList.add(RecurrenceRule.WeekdayNum(0, Weekday.TU)) }
            if (weekDays[3]) { weekdayNumList.add(RecurrenceRule.WeekdayNum(0, Weekday.WE)) }
            if (weekDays[4]) { weekdayNumList.add(RecurrenceRule.WeekdayNum(0, Weekday.TH)) }
            if (weekDays[5]) { weekdayNumList.add(RecurrenceRule.WeekdayNum(0, Weekday.FR)) }
            if (weekDays[6]) { weekdayNumList.add(RecurrenceRule.WeekdayNum(0, Weekday.SA)) }
            mRecurrenceRule.byDayPart = weekdayNumList
        }

        fun setRepeatSameMonthDay() {
            try {
                val calendar = Calendar.getInstance()
                calendar.time = mStartDate
                val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
                if (dayOfMonth > LAST_DAY_EVERY_MONTH_HAS) {
                    mRecurrenceRule.setByPart(RecurrenceRule.Part.BYMONTHDAY, dayOfMonth, -1)
                    mRecurrenceRule.setByPart(RecurrenceRule.Part.BYSETPOS, 1)
                } else {
                    mRecurrenceRule.setByPart(RecurrenceRule.Part.BYMONTHDAY, dayOfMonth)
                }
            } catch (e: InvalidRecurrenceRuleException) {
                throw RuntimeException(e)
            }
        }

        fun setRepeatSameYearDay() {
            val calendar = Calendar.getInstance()
            calendar.time = mStartDate
            if (calendar.get(Calendar.MONTH) != Calendar.FEBRUARY ||
                calendar.get(Calendar.DAY_OF_MONTH) <= LAST_DAY_EVERY_MONTH_HAS
            ) {
                return
            }
            try {
                mRecurrenceRule.setByPart(RecurrenceRule.Part.BYMONTH, Calendar.FEBRUARY)
                mRecurrenceRule.setByPart(RecurrenceRule.Part.BYMONTHDAY, calendar.get(Calendar.DAY_OF_MONTH), -1)
                mRecurrenceRule.setByPart(RecurrenceRule.Part.BYSETPOS, 1)
            } catch (e: InvalidRecurrenceRuleException) {
                throw RuntimeException(e)
            }
        }

        fun setEndFor(occurrences: Int) {
            mRecurrenceRule.count = occurrences
        }

        fun setEndUntil(endDate: Date) {
            mRecurrenceRule.until = DateUtils.getFixedDateTime(endDate)
        }

        fun build(): RecurrenceSetting {
            return RecurrenceSetting(mStartDate, mRecurrenceRule)
        }
    }

    companion object {
        const val TYPE_DAILY: Int = 1
        const val TYPE_WEEKLY: Int = 2
        const val TYPE_MONTHLY: Int = 3
        const val TYPE_YEARLY: Int = 4

        const val FLAG_MONTHLY_SAME_DAY: Int = 1
        private const val LAST_DAY_EVERY_MONTH_HAS = 28

        const val END_FOREVER: Int = 1
        const val END_UNTIL: Int = 2
        const val END_FOR: Int = 3

        @JvmStatic
        @Throws(InvalidRecurrenceRuleException::class)
        fun build(startDate: Date, rule: String): RecurrenceSetting {
            val recurrenceRule = RecurrenceRule(rule)
            return RecurrenceSetting(startDate, recurrenceRule)
        }

        @JvmStatic
        fun computeOccurrences(rule: String, seedDate: Date, now: Date): OccurrenceUpdate {
            val occurrenceDates: MutableList<Date> = ArrayList()
            val currentDateTime = DateUtils.getFixedDateTime(now)
            val startDateTime = DateUtils.getFixedDateTime(seedDate)
            var lastOccurrence = DateUtils.getFixedDateTime(seedDate)
            var nextOccurrence: DateTime? = null
            try {
                val recurrenceRule = RecurrenceRule(rule)
                val iterator = recurrenceRule.iterator(startDateTime)
                while (iterator.hasNext()) {
                    val nextInstance = iterator.nextDateTime()
                    if (!nextInstance.after(currentDateTime)) {
                        occurrenceDates.add(DateUtils.getFixedDate(nextInstance))
                        lastOccurrence = nextInstance
                    } else {
                        nextOccurrence = nextInstance
                        break
                    }
                }
            } catch (ignore: InvalidRecurrenceRuleException) {
                // leave nextOccurrence null so the row is not processed again
            }
            return OccurrenceUpdate(
                occurrenceDates,
                DateUtils.getFixedDate(lastOccurrence),
                if (nextOccurrence != null) DateUtils.getFixedDate(nextOccurrence) else null
            )
        }

        @JvmStatic
        fun resumeAfterEdit(
            oldRule: String?,
            oldStartDate: String?,
            oldNextOccurrence: String?,
            newRule: String,
            newStartDate: String,
            now: Date
        ): String? {
            if (Objects.equals(oldRule, newRule) && Objects.equals(oldStartDate, newStartDate)) {
                return oldNextOccurrence
            }
            val startDate = DateUtils.getDateFromSQLDateString(newStartDate)
            val nextOccurrence = computeOccurrences(newRule, startDate, now).nextOccurrence
            return if (nextOccurrence != null) DateUtils.getSQLDateString(nextOccurrence) else null
        }

        @JvmStatic
        fun nextInstanceAfter(rule: String, anchor: Date, after: Date): Date? {
            val afterDateTime = DateUtils.getFixedDateTime(after)
            try {
                val iterator = RecurrenceRule(rule).iterator(DateUtils.getFixedDateTime(anchor))
                while (iterator.hasNext()) {
                    val instance = iterator.nextDateTime()
                    if (instance.after(afterDateTime)) {
                        return DateUtils.getFixedDate(instance)
                    }
                }
            } catch (ignore: InvalidRecurrenceRuleException) {
                // unparseable rule has no further instance
            }
            return null
        }

        @JvmStatic
        fun periodEnd(rule: String, anchor: Date, start: Date): Date? {
            val next = nextInstanceAfter(rule, anchor, start)
            return if (next != null) DateUtils.addDays(DateUtils.getCalendar(next), -1) else null
        }

        @JvmStatic
        fun periodOn(rule: String, start: Date, now: Date): Array<Date>? {
            val nowDateTime = DateUtils.getFixedDateTime(now)
            try {
                val iterator = RecurrenceRule(rule).iterator(DateUtils.getFixedDateTime(start))
                if (!iterator.hasNext()) {
                    return null
                }
                var periodStart = iterator.nextDateTime()
                while (iterator.hasNext()) {
                    val nextStart = iterator.nextDateTime()
                    if (nextStart.after(nowDateTime)) {
                        val end = DateUtils.addDays(DateUtils.getCalendar(DateUtils.getFixedDate(nextStart)), -1)
                        return arrayOf(DateUtils.getFixedDate(periodStart), end)
                    }
                    periodStart = nextStart
                }
            } catch (ignore: InvalidRecurrenceRuleException) {
                // rule does not parse
            }
            return null
        }

        @JvmStatic
        private fun getFreq(type: Int): Freq {
            return when (type) {
                TYPE_DAILY -> Freq.DAILY
                TYPE_WEEKLY -> Freq.WEEKLY
                TYPE_MONTHLY -> Freq.MONTHLY
                TYPE_YEARLY -> Freq.YEARLY
                else -> Freq.DAILY
            }
        }

        @JvmField
        val CREATOR: Parcelable.Creator<RecurrenceSetting> = object : Parcelable.Creator<RecurrenceSetting> {
            override fun createFromParcel(parcel: Parcel): RecurrenceSetting {
                return RecurrenceSetting(parcel)
            }

            override fun newArray(size: Int): Array<RecurrenceSetting?> {
                return arrayOfNulls(size)
            }
        }
    }
}
