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

package com.oriondev.moneywallet.storage.database.legacy

import android.content.Context
import android.content.SharedPreferences
import com.oriondev.moneywallet.model.Group
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.utils.DateFormatter
import java.util.Calendar

/**
 * Created by andrea on 12/11/18.
 */
@Suppress("DEPRECATION")
class LegacyUserPreferences(context: Context) {

    private val mPreferences: SharedPreferences =
        android.preference.PreferenceManager.getDefaultSharedPreferences(context)

    fun getDateFormat(): Int {
        return when (mPreferences.getString(DATE_PATTERN, "EEE dd MMM yyyy")) {
            DateFormatter.DATE_PATTERN_0 -> PreferenceManager.DATE_FORMAT_TYPE_0
            DateFormatter.DATE_PATTERN_1 -> PreferenceManager.DATE_FORMAT_TYPE_1
            DateFormatter.DATE_PATTERN_2 -> PreferenceManager.DATE_FORMAT_TYPE_2
            DateFormatter.DATE_PATTERN_3 -> PreferenceManager.DATE_FORMAT_TYPE_3
            DateFormatter.DATE_PATTERN_4 -> PreferenceManager.DATE_FORMAT_TYPE_4
            DateFormatter.DATE_PATTERN_5 -> PreferenceManager.DATE_FORMAT_TYPE_5
            DateFormatter.DATE_PATTERN_6 -> PreferenceManager.DATE_FORMAT_TYPE_6
            DateFormatter.DATE_PATTERN_7 -> PreferenceManager.DATE_FORMAT_TYPE_7
            DateFormatter.DATE_PATTERN_8 -> PreferenceManager.DATE_FORMAT_TYPE_8
            else -> PreferenceManager.DATE_FORMAT_TYPE_0
        }
    }

    fun getColorIn(): Int {
        // The importer writes this in as a chosen color, so a legacy user who never chose one
        // has to land on the same default as everybody else.
        return mPreferences.getInt(COLOR_INFLOW, PreferenceManager.getDefaultColorIncome())
    }

    fun getColorOut(): Int {
        return mPreferences.getInt(COLOR_OUTFLOW, PreferenceManager.getDefaultColorExpense())
    }

    fun getGroupType(): Group {
        return when (mPreferences.getInt(GROUP_TYPE, GROUP_MONTHLY)) {
            GROUP_DAILY -> Group.DAILY
            GROUP_WEEKLY -> Group.WEEKLY
            GROUP_MONTHLY -> Group.MONTHLY
            GROUP_YEARLY -> Group.YEARLY
            else -> Group.MONTHLY
        }
    }

    fun getFirstDayOfMonth(): Int {
        return mPreferences.getInt(FIRST_DAY_OF_MONTH, 1)
    }

    fun getFirstDayOfWeek(): Int {
        return mPreferences.getInt(FIRST_DAY_OF_WEEK, Calendar.MONDAY)
    }

    fun isReminderEnabled(): Boolean {
        return mPreferences.getBoolean(REMINDER_STATUS, false)
    }

    fun getReminderHour(): Int {
        return mPreferences.getInt(REMINDER_HOUR, 20)
    }

    fun isCurrencySymbolEnabled(): Boolean {
        return mPreferences.getBoolean(CURRENCY_ENABLED, true)
    }

    fun isGroupDigitsEnabled(): Boolean {
        val pattern = mPreferences.getString(AMOUNT_PATTERN, GROUPED_DIGITS_PATTERN)
        return GROUPED_DIGITS_PATTERN == pattern || GROUPED_DIGITS_PATTERN_ROUNDED == pattern
    }

    fun isRoundDecimalsEnabled(): Boolean {
        val pattern = mPreferences.getString(AMOUNT_PATTERN, GROUPED_DIGITS_PATTERN)
        return GROUPED_DIGITS_PATTERN_ROUNDED == pattern || NOT_GROUPED_DIGITS_PATTERN_ROUNDED == pattern
    }

    fun destroy() {
        mPreferences.edit().clear().apply()
    }

    companion object {
        private const val DATE_PATTERN = "DateType"
        private const val COLOR_INFLOW = "ColorInflow"
        private const val COLOR_OUTFLOW = "ColorOutflow"
        private const val GROUP_TYPE = "GroupType"
        private const val FIRST_DAY_OF_MONTH = "FirstDayOfMonth"
        private const val FIRST_DAY_OF_WEEK = "FirstDayOfWeek"
        private const val REMINDER_STATUS = "ReminderStatus"
        private const val REMINDER_HOUR = "ReminderHour"
        private const val CURRENCY_ENABLED = "CurrencySymbolEnabled"
        private const val AMOUNT_PATTERN = "AmountDecimalFormatPattern"

        private const val GROUP_DAILY = 0
        private const val GROUP_WEEKLY = 1
        private const val GROUP_MONTHLY = 2
        private const val GROUP_YEARLY = 3

        private const val GROUPED_DIGITS_PATTERN = "###,###,###,##0.00"
        private const val NOT_GROUPED_DIGITS_PATTERN = "##0.00"
        private const val GROUPED_DIGITS_PATTERN_ROUNDED = "###,###,###,##0"
        private const val NOT_GROUPED_DIGITS_PATTERN_ROUNDED = "##0"
        const val CALCULATOR_PATTERN: String = "###,###,###,##0.00"
    }
}
