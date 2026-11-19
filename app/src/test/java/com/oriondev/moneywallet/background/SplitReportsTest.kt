/*
 * Copyright (c) 2026.
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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.model.Group
import com.oriondev.moneywallet.model.OverviewData
import com.oriondev.moneywallet.model.OverviewSetting
import com.oriondev.moneywallet.model.PeriodDetailSummaryData
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.SplitLedger
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar
import java.util.Date

/**
 * The reports and the category shares/lines over [SplitLedger]. Every figure is worked out by hand from
 * its rows. The expenses of the month are 10000 + 4000 + 1000 + 3000 + 2000 + 6000 + 5000 + 7000
 * + 1100 + 2200, of which the 1200 part in Hidden is kept out of every report, and the incomes
 * are 30000 + 8000.
 */
@RunWith(RobolectricTestRunner::class)
class SplitReportsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.initialize(context)
        TestDatabases.useFreshDatabase(context)
        PreferenceManager.setCurrentWallet(context, PreferenceManager.TOTAL_WALLET_ID)
        SplitLedger.build(context)
    }

    @Test
    fun theOverviewCashFlowLeavesOutAPartInAHiddenCategory() {
        val loader = OverviewDataLoader(
            context,
            OverviewSetting(start(), end(), Group.YEARLY, OverviewSetting.CashFlow.NET_INCOMES)
        )
        val data = loader.loadInBackground() ?: throw AssertionError("loader returned null")
        assertEquals(REPORTED_EXPENSES, expenses(data))
        assertEquals(INCOMES, incomes(data))
    }

    @Test
    fun theOverviewOfOneCategoryAddsUpItsLines() {
        var loader = OverviewDataLoader(
            context,
            OverviewSetting(start(), end(), Group.YEARLY, SplitLedger.HOUSEHOLD)
        )
        var data = loader.loadInBackground() ?: throw AssertionError("loader returned null")
        assertEquals(HOUSEHOLD_REPORTED, expenses(data))

        loader = OverviewDataLoader(
            context,
            OverviewSetting(start(), end(), Group.YEARLY, SplitLedger.FUEL)
        )
        data = loader.loadInBackground() ?: throw AssertionError("loader returned null")
        assertEquals(3000L + 2500 + 3000 + 6000, expenses(data))

        loader = OverviewDataLoader(
            context,
            OverviewSetting(start(), end(), Group.YEARLY, SplitLedger.BONUS)
        )
        data = loader.loadInBackground() ?: throw AssertionError("loader returned null")
        assertEquals(3000L, incomes(data))
    }

    @Test
    fun thePeriodSummaryLeavesOutAPartInAHiddenCategory() {
        val loader = PeriodDetailSummaryLoader(
            context,
            start(),
            end(),
            PeriodDetailSummaryLoader.GROUP_BY_YEAR
        )
        val data: PeriodDetailSummaryData = loader.loadInBackground() ?: throw AssertionError("loader returned null")
        var expenses = 0L
        var incomes = 0L
        for (i in 0 until data.getPeriodCount()) {
            expenses += data.getPeriodMoney(i).expenses.getMoney("EUR")
            incomes += data.getPeriodMoney(i).incomes.getMoney("EUR")
        }
        assertEquals(REPORTED_EXPENSES, expenses)
        assertEquals(INCOMES, incomes)
        assertEquals(INCOMES - REPORTED_EXPENSES, data.netIncomes.getMoney("EUR"))
    }

    @Test
    fun thePieGivesEachCategoryItsLines() {
        val expected = HashMap<Long, Long>()
        expected[SplitLedger.GROCERIES] = (10000L - 2000 - 3000) + (7000 - 1000 - 1500) + 1100
        expected[SplitLedger.HOUSEHOLD] = HOUSEHOLD_REPORTED
        expected[SplitLedger.FUEL] = 3000L + 2500 + 3000 + 6000
        expected[SplitLedger.PHARMACY] = 1500L + 1000
        assertEquals(expected, pie(false))
        val incomes = HashMap<Long, Long>()
        incomes[SplitLedger.SALARY] = 30000L + (8000 - 3000)
        incomes[SplitLedger.BONUS] = 3000L
        assertEquals(incomes, pie(true))
    }

    @Test
    fun categorySharesWithACategorySpecifiedListsEachRowOnceWithItsShare() {
        val selection = "${Contract.Transaction.CATEGORY_ID} = ?"
        val selectionArgs = arrayOf(SplitLedger.HOUSEHOLD.toString())
        val cursor = context.contentResolver.query(
            DataContentProvider.CONTENT_CATEGORY_SHARES,
            arrayOf(Contract.Transaction.ID, Contract.Transaction.MONEY),
            selection,
            selectionArgs,
            null
        ) ?: throw AssertionError("query returned null cursor")
        val expected = HashMap<Long, Long>()
        expected[SplitLedger.RECEIPT] = 2000L
        expected[SplitLedger.CROSSED] = 2000L
        expected[SplitLedger.WITH_HIDDEN] = 5000L - 1200
        expected[SplitLedger.TWICE] = 1000L + 1500
        expected[SplitLedger.PLAIN_BANK] = 2200L
        cursor.use { c ->
            val rows = HashMap<Long, Long>()
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow(Contract.Transaction.ID))
                val money = c.getLong(c.getColumnIndexOrThrow(Contract.Transaction.MONEY))
                assertNull("each row once", rows.put(id, money))
            }
            assertEquals(expected, rows)
        }
    }

    @Test
    fun categorySharesWithMultipleCategoriesListsEachRowOnceWithCombinedShare() {
        val selection = "${Contract.Transaction.CATEGORY_ID} IN (?, ?)"
        val selectionArgs = arrayOf(SplitLedger.HOUSEHOLD.toString(), SplitLedger.HIDDEN.toString())
        val cursor = context.contentResolver.query(
            DataContentProvider.CONTENT_CATEGORY_SHARES,
            arrayOf(Contract.Transaction.ID, Contract.Transaction.MONEY),
            selection,
            selectionArgs,
            null
        ) ?: throw AssertionError("query returned null cursor")
        val expected = HashMap<Long, Long>()
        expected[SplitLedger.RECEIPT] = 2000L
        expected[SplitLedger.CROSSED] = 2000L
        expected[SplitLedger.WITH_HIDDEN] = 5000L
        expected[SplitLedger.TWICE] = 1000L + 1500
        expected[SplitLedger.PLAIN_BANK] = 2200L
        cursor.use { c ->
            val rows = HashMap<Long, Long>()
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow(Contract.Transaction.ID))
                val money = c.getLong(c.getColumnIndexOrThrow(Contract.Transaction.MONEY))
                assertNull("each row once", rows.put(id, money))
            }
            assertEquals(expected, rows)
        }
    }

    @Test
    fun categoryLinesListsEachPartUnderItsCategory() {
        val cursor = context.contentResolver.query(
            DataContentProvider.CONTENT_CATEGORY_LINES,
            arrayOf(Contract.Transaction.ID, Contract.Transaction.CATEGORY_ID, Contract.Transaction.MONEY),
            null,
            null,
            null
        ) ?: throw AssertionError("query returned null cursor")
        val counts = HashMap<Long, Int>()
        cursor.use { c ->
            while (c.moveToNext()) {
                val catId = c.getLong(c.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_ID))
                counts[catId] = (counts[catId] ?: 0) + 1
            }
        }
        // Groceries: Receipt, Twice, Plain Cash
        assertEquals(3, counts[SplitLedger.GROCERIES])
        // Household: Receipt, Crossed, With Hidden, Twice (x2), Plain Bank
        assertEquals(6, counts[SplitLedger.HOUSEHOLD])
        // Hidden: With Hidden (1200 part)
        assertEquals(1, counts[SplitLedger.HIDDEN])
        // Fuel: Receipt, Exact, Negative, Debt row
        assertEquals(4, counts[SplitLedger.FUEL])
        // Pharmacy: Exact, Over
        assertEquals(2, counts[SplitLedger.PHARMACY])
        // Salary: Pay, Pay Split
        assertEquals(2, counts[SplitLedger.SALARY])
        // Bonus: Pay Split
        assertEquals(1, counts[SplitLedger.BONUS])
    }

    private fun pie(incomes: Boolean): Map<Long, Long> {
        val loader = PeriodDetailFlowLoader(context, start(), end(), incomes)
        val data = loader.loadInBackground() ?: throw AssertionError("flow data was null")
        val money = HashMap<Long, Long>()
        for (i in 0 until data.getCategoryCount()) {
            val category = data.getCategory(i)
            money[category.id] = category.money.getMoney("EUR")
        }
        return money
    }

    companion object {
        private const val EXPENSES = 10000L + 4000 + 1000 + 3000 + 2000 + 6000 + 5000 + 7000 + 1100 + 2200
        private const val REPORTED_EXPENSES = EXPENSES - 1200
        private const val INCOMES = 30000L + 8000
        /** Every Household line, the Hidden one left out by the reports. */
        private const val HOUSEHOLD_REPORTED = 2000L + 2000 + (5000 - 1200) + 1000 + 1500 + 2200

        private fun expenses(data: OverviewData): Long {
            var money = 0L
            for (i in 0 until data.getPeriodCount()) {
                money += data.getPeriodMoney(i).expenses.getMoney("EUR")
            }
            return money
        }

        private fun incomes(data: OverviewData): Long {
            var money = 0L
            for (i in 0 until data.getPeriodCount()) {
                money += data.getPeriodMoney(i).incomes.getMoney("EUR")
            }
            return money
        }

        private fun start(): Date {
            val calendar = Calendar.getInstance()
            calendar.clear()
            calendar.set(2026, Calendar.JANUARY, 1, 0, 0, 0)
            return calendar.time
        }

        private fun end(): Date {
            val calendar = Calendar.getInstance()
            calendar.clear()
            calendar.set(2026, Calendar.JANUARY, 31, 23, 59, 59)
            return calendar.time
        }
    }
}
