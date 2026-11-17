/*
 * Copyright (c) 2018.
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

package com.oriondev.moneywallet.background

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Money
import com.oriondev.moneywallet.model.PeriodDetailSummaryData
import com.oriondev.moneywallet.model.PeriodMoney
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateUtils
import java.util.Calendar
import java.util.Date

/**
 * Created by andrea on 14/08/18.
 */
class PeriodDetailSummaryLoader(
    context: Context,
    private val mStartDate: Date,
    private val mEndDate: Date,
    private val mGroupType: Int
) : AbstractGenericLoader<PeriodDetailSummaryData>(context) {

    override fun getObservedUri(): Uri = DataContentProvider.CONTENT_ALL

    @SuppressLint("Range")
    override fun loadInBackground(): PeriodDetailSummaryData {
        val totalNetIncomes = Money()
        val periodMoneyList = ArrayList<PeriodMoney>()
        val uri = DataContentProvider.CONTENT_TRANSACTIONS
        val projection = arrayOf(
            Contract.Transaction.DATE,
            Contract.Transaction.DIRECTION,
            Contract.Transaction.WALLET_CURRENCY,
            Contract.Transaction.MONEY
        )
        var selection: String
        val selectionArgs: Array<String>?
        val currentWallet = PreferenceManager.getCurrentWallet()
        if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
            selection = Contract.Transaction.WALLET_COUNT_IN_TOTAL + " = 1"
            selectionArgs = null
        } else {
            selection = Contract.Transaction.WALLET_ID + " = ?"
            selectionArgs = arrayOf(currentWallet.toString())
        }
        selection += " AND " + Contract.Transaction.CONFIRMED + " = '1' AND " + Contract.Transaction.COUNT_IN_TOTAL + " = '1'"
        selection += " AND " + Contract.Transaction.REPORT_FILTER
        selection += " AND DATETIME(" + Contract.Transaction.DATE + ") <= DATETIME('now', 'localtime')"
        selection += " AND DATETIME(" + Contract.Transaction.DATE + ") >= DATETIME('" + DateUtils.getSQLDateTimeString(mStartDate) + "')"
        selection += " AND DATETIME(" + Contract.Transaction.DATE + ") <= DATETIME('" + DateUtils.getSQLDateTimeString(mEndDate) + "')"
        val sortOrder = Contract.Transaction.DATE + " ASC"
        val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        cursor?.use {
            it.moveToFirst()
            var currentPeriod: PeriodMoney? = null
            while (isAnotherPeriodNeeded(currentPeriod)) {
                val period = getNextPeriod(currentPeriod)
                currentPeriod = period
                if (!it.isAfterLast) {
                    do {
                        val dateString = it.getString(it.getColumnIndex(Contract.Transaction.DATE)) ?: ""
                        val date = DateUtils.getDateFromSQLDateTimeString(dateString)
                        if (belongToPeriod(period, date)) {
                            val direction = it.getInt(it.getColumnIndex(Contract.Transaction.DIRECTION))
                            val currency = it.getString(it.getColumnIndex(Contract.Transaction.WALLET_CURRENCY)) ?: ""
                            val money = it.getLong(it.getColumnIndex(Contract.Transaction.MONEY))
                            if (direction == Contract.Direction.INCOME) {
                                period.addIncome(currency, money)
                                totalNetIncomes.addMoney(currency, money)
                            } else if (direction == Contract.Direction.EXPENSE) {
                                period.addExpense(currency, money)
                                totalNetIncomes.removeMoney(currency, money)
                            }
                        } else {
                            break
                        }
                    } while (it.moveToNext())
                }
                periodMoneyList.add(period)
            }
        }
        // the total net income contains all the available currencies
        val barDataList = ArrayList<BarData>()
        val barDataCurrencies = ArrayList<CurrencyUnit?>()
        val theme = ThemeEngine.getTheme()
        val incomeColor = theme.getVisibleColor(
            PreferenceManager.getCurrentIncomeColor(),
            PreferenceManager.getDefaultColorIncome()
        )
        val expenseColor = theme.getVisibleColor(
            PreferenceManager.getCurrentExpenseColor(),
            PreferenceManager.getDefaultColorExpense()
        )
        for (currency in totalNetIncomes.currencies) {
            // for each currency we have to iterate the period money items and generate
            // the chart data set composed by two sub data sets:
            val currencyUnit = CurrencyManager.getCurrency(currency)
            val incomeBarEntries = ArrayList<BarEntry>()
            val expenseBarEntries = ArrayList<BarEntry>()
            for (i in periodMoneyList.indices) {
                val periodMoney = periodMoneyList[i]
                val incomes = periodMoney.incomes.getMoney(currency)
                incomeBarEntries.add(BarEntry(i.toFloat(), incomes.toFloat()))
                val expenses = periodMoney.expenses.getMoney(currency)
                expenseBarEntries.add(BarEntry(i.toFloat(), expenses.toFloat()))
            }
            val incomeDataSet = BarDataSet(incomeBarEntries, context.getString(R.string.hint_incomes))
            val expenseDataSet = BarDataSet(expenseBarEntries, context.getString(R.string.hint_expenses))
            incomeDataSet.color = incomeColor
            expenseDataSet.color = expenseColor
            barDataList.add(BarData(incomeDataSet, expenseDataSet))
            barDataCurrencies.add(currencyUnit)
        }
        return PeriodDetailSummaryData(totalNetIncomes, barDataList, barDataCurrencies, periodMoneyList)
    }

    /**
     * This method is used to determine if another period must be added to the period list.
     * @param periodMoney is the last period analyzed.
     * @return true if the end date has not already been reached.
     */
    private fun isAnotherPeriodNeeded(periodMoney: PeriodMoney?): Boolean {
        return periodMoney == null || DateUtils.isBefore(periodMoney.endDate, mEndDate)
    }

    private fun getNextPeriod(lastPeriod: PeriodMoney?): PeriodMoney {
        val startCalendar = Calendar.getInstance()
        if (lastPeriod != null) {
            startCalendar.time = lastPeriod.endDate
            startCalendar.add(Calendar.MILLISECOND, 1)
        } else {
            startCalendar.time = mStartDate
            startCalendar.set(Calendar.HOUR_OF_DAY, 0)
            startCalendar.set(Calendar.MINUTE, 0)
            startCalendar.set(Calendar.SECOND, 0)
            startCalendar.set(Calendar.MILLISECOND, 0)
        }
        val startDate = startCalendar.time
        val endCalendar = Calendar.getInstance()
        endCalendar.time = startDate
        when (mGroupType) {
            GROUP_BY_YEAR -> {
                // the end date is the last day of the same year of the start date
                endCalendar.set(Calendar.MONTH, Calendar.DECEMBER)
                endCalendar.set(Calendar.DAY_OF_MONTH, 31)
            }
            GROUP_BY_MONTH -> {
                // the end date is generally the last day of the same month of the start month
                // but the user could have specified a different 'first day of month' so the
                // calculation is a bit more tricky:
                // step 1: we check if the start day is after the first day of month
                val firstDayOfMonth = PreferenceManager.getFirstDayOfMonth()
                val currentDayOfMonth = endCalendar.get(Calendar.DAY_OF_MONTH)
                if (currentDayOfMonth >= firstDayOfMonth) {
                    // step 2: we set as 1 the current day of the same month
                    endCalendar.set(Calendar.DAY_OF_MONTH, 1)
                    // step 3: we move to the next month
                    endCalendar.add(Calendar.MONTH, 1)
                }
                // step 4: we move to the first day of month and then we move one day first
                endCalendar.set(Calendar.DAY_OF_MONTH, firstDayOfMonth)
                endCalendar.add(Calendar.DAY_OF_MONTH, -1)
            }
            GROUP_BY_WEEK -> {
                // we can check which day of week is the start date and count how many days are
                // needed to reach the last day of the week that can also be personalized by the
                // user so the calculation is a bit more tricky:
                // step 1: obtain the day of week of the start day and the user preferred one
                val firstDayOfWeek = PreferenceManager.getFirstDayOfWeek()
                val currentDayOfWeek = endCalendar.get(Calendar.DAY_OF_WEEK)
                // step 2: calculate how many days are needed to reach the last day of week
                var offset = firstDayOfWeek - currentDayOfWeek
                if (offset <= 0) {
                    offset += 7
                }
                val requiredDays = offset - 1
                // step 3: add those days to the current start date
                endCalendar.add(Calendar.DAY_OF_MONTH, requiredDays)
            }
            GROUP_BY_DAY -> {
                // no special calculation is needed
            }
            GROUP_BY_HOUR -> {
                // no special calculation is needed
            }
        }
        if (mGroupType != GROUP_BY_HOUR) {
            endCalendar.set(Calendar.HOUR_OF_DAY, 23)
        }
        endCalendar.set(Calendar.MINUTE, 59)
        endCalendar.set(Calendar.SECOND, 59)
        endCalendar.set(Calendar.MILLISECOND, 999)
        if (endCalendar.timeInMillis > mEndDate.time) {
            endCalendar.time = mEndDate
        }
        return PeriodMoney(startDate, endCalendar.time)
    }

    private fun belongToPeriod(periodMoney: PeriodMoney, date: Date): Boolean {
        return DateUtils.isAfterEqual(date, periodMoney.startDate) && DateUtils.isBeforeEqual(date, periodMoney.endDate)
    }

    companion object {
        const val GROUP_BY_YEAR = 1
        const val GROUP_BY_MONTH = 2
        const val GROUP_BY_WEEK = 3
        const val GROUP_BY_DAY = 4
        const val GROUP_BY_HOUR = 5
    }
}
