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
import android.graphics.Color
import android.net.Uri
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.RadarData
import com.github.mikephil.charting.data.RadarDataSet
import com.github.mikephil.charting.data.RadarEntry
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.interfaces.datasets.IRadarDataSet
import com.oriondev.moneywallet.model.Group
import com.oriondev.moneywallet.model.Money
import com.oriondev.moneywallet.model.MoneyScale
import com.oriondev.moneywallet.model.OverviewData
import com.oriondev.moneywallet.model.OverviewSetting
import com.oriondev.moneywallet.model.PeriodMoney
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateUtils
import java.util.Calendar
import java.util.Date

/**
 * Created by andrea on 17/08/18.
 */
class OverviewDataLoader(
    context: Context,
    private val mOverviewSetting: OverviewSetting
) : AbstractGenericLoader<OverviewData>(context) {

    override fun getObservedUri(): Uri = DataContentProvider.CONTENT_ALL

    @SuppressLint("Range")
    override fun loadInBackground(): OverviewData {
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
        var selectionArgs: Array<String>?
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
        selection += " AND DATETIME(" + Contract.Transaction.DATE + ") >= DATETIME('" + DateUtils.getSQLDateTimeString(mOverviewSetting.startDate) + "')"
        selection += " AND DATETIME(" + Contract.Transaction.DATE + ") <= DATETIME('" + DateUtils.getSQLDateTimeString(mOverviewSetting.endDate) + "')"
        when (mOverviewSetting.type) {
            OverviewSetting.Type.CASH_FLOW -> {
                when (mOverviewSetting.cashFlow) {
                    OverviewSetting.CashFlow.INCOMES -> {
                        selection += " AND " + Contract.Transaction.DIRECTION + " = ?"
                        selectionArgs = appendSelectionArgs(selectionArgs, Contract.Direction.INCOME.toString())
                    }
                    OverviewSetting.CashFlow.EXPENSES -> {
                        selection += " AND " + Contract.Transaction.DIRECTION + " = ?"
                        selectionArgs = appendSelectionArgs(selectionArgs, Contract.Direction.EXPENSE.toString())
                    }
                    OverviewSetting.CashFlow.NET_INCOMES -> {
                        // no direction filter
                    }
                }
            }
            OverviewSetting.Type.CATEGORY -> {
                selection += " AND (" + Contract.Transaction.CATEGORY_ID + " = ? OR " + Contract.Transaction.CATEGORY_PARENT_ID + " = ?)"
                selectionArgs = appendSelectionArgs(
                    selectionArgs,
                    mOverviewSetting.categoryId.toString(),
                    mOverviewSetting.categoryId.toString()
                )
            }
        }
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
        // generate chart data
        val barDataSets = ArrayList<IBarDataSet>()
        val lineDataSets = ArrayList<ILineDataSet>()
        val radarDataSets = ArrayList<IRadarDataSet>()
        for (currency in totalNetIncomes.currencies) {
            // for each currency we have to iterate the list of period money and generate
            // a valid chart data set for each chart types.
            val barEntryList = ArrayList<BarEntry>()
            val lineEntryList = ArrayList<Entry>()
            val radarEntryList = ArrayList<RadarEntry>()
            // iterate period money items
            val currencyUnit = CurrencyManager.getCurrency(currency)
            for (i in periodMoneyList.indices) {
                val periodMoney = periodMoneyList[i]
                val money = periodMoney.netIncomes.getMoney(currency)
                // getDecimals and not getDecimals() on the unit. A row can name a currency this
                // installation does not have, which a restore from another one leaves behind, and
                // getCurrency answers null for it
                val value = MoneyScale.toHumanAmount(money, CurrencyManager.getDecimals(currencyUnit)).toFloat()
                barEntryList.add(BarEntry(i.toFloat(), value))
                lineEntryList.add(Entry(i.toFloat(), value))
                radarEntryList.add(RadarEntry(value))
            }
            // generate one data set for each chart
            val barDataSet = BarDataSet(barEntryList, currency)
            barDataSet.color = COLOR_PALETTE[barDataSets.size % COLOR_PALETTE.size]
            barDataSets.add(barDataSet)
            val lineDataSet = LineDataSet(lineEntryList, currency)
            lineDataSet.color = COLOR_PALETTE[barDataSets.size % COLOR_PALETTE.size]
            lineDataSet.setCircleColor(COLOR_PALETTE[barDataSets.size % COLOR_PALETTE.size])
            lineDataSet.circleRadius = 0.3f
            lineDataSet.lineWidth = 1f
            lineDataSets.add(lineDataSet)
            val radarDataSet = RadarDataSet(radarEntryList, currency)
            radarDataSet.color = COLOR_PALETTE[barDataSets.size % COLOR_PALETTE.size]
            radarDataSet.fillColor = COLOR_PALETTE[barDataSets.size % COLOR_PALETTE.size]
            radarDataSet.setDrawFilled(true)
            radarDataSets.add(radarDataSet)
        }
        // buildMaterialDialog the bar data and personalize it
        var barData: BarData? = null
        if (barDataSets.isNotEmpty()) {
            barData = BarData(barDataSets)
            val groupSize = barData.dataSetCount
            if (groupSize > 1) {
                barData.barWidth = 0.8f / groupSize
                barData.groupBars(0f, 0.08f, 0.12f / groupSize)
            }
        }
        val lineData = if (lineDataSets.isNotEmpty()) LineData(lineDataSets) else null
        // the chart library has a bug when the radar data set is empty
        val radarData = if (radarDataSets.isNotEmpty()) RadarData(radarDataSets) else null
        // return generated data
        return OverviewData(barData, lineData, radarData, periodMoneyList)
    }

    private fun appendSelectionArgs(oldArgs: Array<String>?, vararg newArgs: String): Array<String> {
        val oldLength = oldArgs?.size ?: 0
        val finalArgs = Array(oldLength + newArgs.size) { index ->
            if (oldArgs != null && index < oldLength) {
                oldArgs[index]
            } else {
                newArgs[index - oldLength]
            }
        }
        return finalArgs
    }

    /**
     * This method is used to determine if another period must be added to the period list.
     * @param periodMoney is the last period analyzed.
     * @return true if the end date has not already been reached.
     */
    private fun isAnotherPeriodNeeded(periodMoney: PeriodMoney?): Boolean {
        return periodMoney == null || DateUtils.isBefore(periodMoney.endDate, mOverviewSetting.endDate)
    }

    private fun getNextPeriod(lastPeriod: PeriodMoney?): PeriodMoney {
        val startCalendar = Calendar.getInstance()
        if (lastPeriod != null) {
            startCalendar.time = lastPeriod.endDate
            startCalendar.add(Calendar.MILLISECOND, 1)
        } else {
            startCalendar.time = mOverviewSetting.startDate
            startCalendar.set(Calendar.HOUR_OF_DAY, 0)
            startCalendar.set(Calendar.MINUTE, 0)
            startCalendar.set(Calendar.SECOND, 0)
            startCalendar.set(Calendar.MILLISECOND, 0)
        }
        val startDate = startCalendar.time
        val endCalendar = Calendar.getInstance()
        endCalendar.time = startDate
        when (mOverviewSetting.groupType) {
            Group.YEARLY -> {
                // the end date is the last day of the same year of the start date
                endCalendar.set(Calendar.MONTH, Calendar.DECEMBER)
                endCalendar.set(Calendar.DAY_OF_MONTH, 31)
            }
            Group.MONTHLY -> {
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
            Group.WEEKLY -> {
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
            Group.DAILY -> {
                // no special calculation is needed
            }
        }
        endCalendar.set(Calendar.HOUR_OF_DAY, 23)
        endCalendar.set(Calendar.MINUTE, 59)
        endCalendar.set(Calendar.SECOND, 59)
        endCalendar.set(Calendar.MILLISECOND, 999)
        if (endCalendar.timeInMillis > mOverviewSetting.endDate.time) {
            endCalendar.time = mOverviewSetting.endDate
        }
        return PeriodMoney(startDate, endCalendar.time)
    }

    private fun belongToPeriod(periodMoney: PeriodMoney, date: Date): Boolean {
        return DateUtils.isAfterEqual(date, periodMoney.startDate) && DateUtils.isBeforeEqual(date, periodMoney.endDate)
    }

    companion object {
        private val COLOR_PALETTE = intArrayOf(
            Color.parseColor("#D32F2F"),
            Color.parseColor("#FF5722"),
            Color.parseColor("#FDD835"),
            Color.parseColor("#4CAF50"),
            Color.parseColor("#039BE5"),
            Color.parseColor("#673AB7")
        )
    }
}
