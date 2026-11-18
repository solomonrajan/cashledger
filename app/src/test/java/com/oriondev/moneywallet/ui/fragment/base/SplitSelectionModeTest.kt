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

package com.oriondev.moneywallet.ui.fragment.base

import android.content.ContentResolver
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.SplitLedger
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Collections

/**
 * The bulk actions on the selected rows of a list, over [SplitLedger].
 */
@RunWith(RobolectricTestRunner::class)
class SplitSelectionModeTest {

    private lateinit var context: Context
    private lateinit var resolver: ContentResolver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.initialize(context)
        TestDatabases.useFreshDatabase(context)
        resolver = context.contentResolver
        SplitLedger.build(context)
    }

    /**
     * From two decimals to none the total and each part lose their two last digits together, so
     * the lines still add up to the total.
     */
    @Test
    fun aMoveAcrossDecimalsRescalesThePartsWithTheTotal() {
        assertEquals(0, TransactionSelectionMode.moveTransactions(resolver, longArrayOf(SplitLedger.RECEIPT), SplitLedger.YEN, "JPY"))
        assertEquals(
            sorted(
                SplitLedger.GROCERIES.toString() + ":" + (10000 - 2000 - 3000) / 100,
                SplitLedger.HOUSEHOLD.toString() + ":" + 2000 / 100,
                SplitLedger.FUEL.toString() + ":" + 3000 / 100
            ),
            linesOf(SplitLedger.RECEIPT)
        )
        assertEquals(10000L / 100, totalOf(SplitLedger.RECEIPT))
        // a split left in the euro wallet keeps its parts as they were
        assertEquals(
            sorted(SplitLedger.PHARMACY.toString() + ":" + 1500, SplitLedger.FUEL.toString() + ":" + 2500),
            linesOf(SplitLedger.EXACT)
        )
    }

    /** Within the same decimals the parts are not touched. */
    @Test
    fun aMoveWithinTheSameDecimalsKeepsTheParts() {
        assertEquals(0, TransactionSelectionMode.moveTransactions(resolver, longArrayOf(SplitLedger.RECEIPT), SplitLedger.BANK, "EUR"))
        assertEquals(
            sorted(
                SplitLedger.GROCERIES.toString() + ":" + (10000 - 2000 - 3000),
                SplitLedger.HOUSEHOLD.toString() + ":" + 2000,
                SplitLedger.FUEL.toString() + ":" + 3000
            ),
            linesOf(SplitLedger.RECEIPT)
        )
    }

    /**
     * A split row whose direction the new category would turn is skipped and counted, like a row
     * of a transfer, and the confirmation counts only the rows that change.
     */
    @Test
    fun aSplitRowIsNotMovedToACategoryOfTheOtherDirection() {
        val salary = Category(SplitLedger.SALARY, "Salary", null, Contract.CategoryType.INCOME)
        val ids = longArrayOf(SplitLedger.RECEIPT, SplitLedger.PLAIN_CASH)
        assertEquals(
            context.resources.getQuantityString(
                R.plurals.message_change_category_selected_transactions,
                1,
                1,
                "Salary"
            ),
            TransactionSelectionMode.buildCategoryMessage(context.resources, resolver, ids, salary)
        )
        val skipped = TransactionSelectionMode.changeCategory(resolver, ids, salary)
        assertEquals(0, skipped.refused)
        assertEquals(1, skipped.split)
        assertEquals(SplitLedger.GROCERIES, longOf(SplitLedger.RECEIPT, Contract.Transaction.CATEGORY_ID))
        assertEquals(Contract.Direction.EXPENSE.toLong(), longOf(SplitLedger.RECEIPT, Contract.Transaction.DIRECTION))
        assertEquals(SplitLedger.SALARY, longOf(SplitLedger.PLAIN_CASH, Contract.Transaction.CATEGORY_ID))
        assertEquals(Contract.Direction.INCOME.toLong(), longOf(SplitLedger.PLAIN_CASH, Contract.Transaction.DIRECTION))
    }

    /**
     * CROSSED is an expense whose one part sits in Salary, so the part is invalid and the row
     * reads as one Household line. Turned to Bonus, an income, the part would become valid and
     * the row would read Bonus and Salary. It is skipped as a split row is.
     */
    @Test
    fun aRowWithOnlyInvalidPartsIsNotMovedToACategoryOfTheOtherDirection() {
        val bonus = Category(SplitLedger.BONUS, "Bonus", null, Contract.CategoryType.INCOME)
        val ids = longArrayOf(SplitLedger.CROSSED)
        assertEquals(
            context.resources.getQuantityString(
                R.plurals.message_change_category_selected_transactions,
                0,
                0,
                "Bonus"
            ),
            TransactionSelectionMode.buildCategoryMessage(context.resources, resolver, ids, bonus)
        )
        val skipped = TransactionSelectionMode.changeCategory(resolver, ids, bonus)
        assertEquals(0, skipped.refused)
        assertEquals(1, skipped.split)
        assertEquals(SplitLedger.HOUSEHOLD, longOf(SplitLedger.CROSSED, Contract.Transaction.CATEGORY_ID))
        assertEquals(Contract.Direction.EXPENSE.toLong(), longOf(SplitLedger.CROSSED, Contract.Transaction.DIRECTION))
        assertEquals(sorted(SplitLedger.HOUSEHOLD.toString() + ":2000"), linesOf(SplitLedger.CROSSED))
    }

    /**
     * A row whose only parts are flagged deleted is not split, so it moves to a category of the
     * other direction like any plain row.
     */
    @Test
    fun aRowWithOnlyDeletedPartsIsMovedToACategoryOfTheOtherDirection() {
        val row = 962L
        val db = SplitLedger.database(context)
        db.execSQL(
            "INSERT INTO transactions (transaction_id, transaction_money, transaction_date, " +
                "transaction_description, transaction_category, transaction_direction, " +
                "transaction_type, transaction_wallet, transaction_confirmed, " +
                "transaction_count_in_total, uuid, last_edit, deleted) VALUES (?, 1500, ?, " +
                "'deleted-parts', ?, ?, ?, ?, 1, 1, 'transaction-962', 1, 0)",
            arrayOf<Any>(
                row, SplitLedger.DATE,
                SplitLedger.GROCERIES, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, SplitLedger.CASH
            )
        )
        db.execSQL(
            "INSERT INTO transaction_splits (split_id, _transaction, split_category, " +
                "split_money, uuid, last_edit, deleted) VALUES (963, ?, ?, 500, 'split-963', 1, 1)",
            arrayOf<Any>(row, SplitLedger.HOUSEHOLD)
        )
        val salary = Category(SplitLedger.SALARY, "Salary", null, Contract.CategoryType.INCOME)
        val ids = longArrayOf(row)
        assertEquals(
            context.resources.getQuantityString(
                R.plurals.message_change_category_selected_transactions,
                1,
                1,
                "Salary"
            ),
            TransactionSelectionMode.buildCategoryMessage(context.resources, resolver, ids, salary)
        )
        val skipped = TransactionSelectionMode.changeCategory(resolver, ids, salary)
        assertEquals(0, skipped.refused)
        assertEquals(0, skipped.split)
        assertEquals(SplitLedger.SALARY, longOf(row, Contract.Transaction.CATEGORY_ID))
        assertEquals(Contract.Direction.INCOME.toLong(), longOf(row, Contract.Transaction.DIRECTION))
    }

    /** Within the same direction a split row takes the new main category and keeps its parts. */
    @Test
    fun aSplitRowTakesAMainCategoryOfTheSameDirection() {
        val pharmacy = Category(SplitLedger.PHARMACY, "Pharmacy", null, Contract.CategoryType.EXPENSE)
        val skipped = TransactionSelectionMode.changeCategory(resolver, longArrayOf(SplitLedger.RECEIPT), pharmacy)
        assertEquals(0, skipped.refused)
        assertEquals(0, skipped.split)
        assertEquals(
            sorted(
                SplitLedger.PHARMACY.toString() + ":" + (10000 - 2000 - 3000),
                SplitLedger.HOUSEHOLD.toString() + ":2000",
                SplitLedger.FUEL.toString() + ":3000"
            ),
            linesOf(SplitLedger.RECEIPT)
        )
    }

    /**
     * DEBT_ROW is skipped as a debt and RECEIPT as a split row Salary would turn to income, and
     * each reason gets its own sentence. PLAIN_CASH moves.
     */
    @Test
    fun theSkipMessageNamesASplitRowApartFromADebtRow() {
        val salary = Category(SplitLedger.SALARY, "Salary", null, Contract.CategoryType.INCOME)
        val skipped = TransactionSelectionMode.changeCategory(
            resolver,
            longArrayOf(SplitLedger.DEBT_ROW, SplitLedger.RECEIPT, SplitLedger.PLAIN_CASH),
            salary
        )
        assertEquals(1, skipped.refused)
        assertEquals(1, skipped.split)
        assertEquals(
            context.resources.getQuantityString(R.plurals.message_error_change_selected_transactions, 1, 1) +
                "\n\n" + context.resources.getQuantityString(R.plurals.message_error_change_split_transactions, 1, 1),
            TransactionSelectionMode.buildSkippedMessage(context.resources, skipped)
        )
        assertEquals(
            context.resources.getQuantityString(R.plurals.message_error_change_split_transactions, 2, 2),
            TransactionSelectionMode.buildSkippedMessage(
                context.resources,
                TransactionSelectionMode.SkippedRows(0, 2)
            )
        )
        assertEquals(
            null,
            TransactionSelectionMode.buildSkippedMessage(
                context.resources,
                TransactionSelectionMode.SkippedRows(0, 0)
            )
        )
    }

    private fun linesOf(transaction: Long): List<String> {
        val lines = ArrayList<String>()
        val cursor = resolver.query(
            DataContentProvider.CONTENT_CATEGORY_LINES,
            arrayOf(Contract.Transaction.CATEGORY_ID, Contract.Transaction.MONEY),
            Contract.Transaction.ID + " = ?",
            arrayOf(transaction.toString()),
            null
        )
        cursor?.use { c ->
            while (c.moveToNext()) {
                lines.add(c.getLong(0).toString() + ":" + c.getLong(1))
            }
        } ?: fail("query returned null cursor")
        Collections.sort(lines)
        return lines
    }

    private fun totalOf(transaction: Long): Long {
        return longOf(transaction, Contract.Transaction.MONEY)
    }

    private fun longOf(transaction: Long, column: String): Long {
        val cursor = resolver.query(
            DataContentProvider.CONTENT_TRANSACTIONS,
            arrayOf(column),
            Contract.Transaction.ID + " = ?",
            arrayOf(transaction.toString()),
            null
        )
        return cursor?.use { c ->
            c.moveToFirst()
            c.getLong(0)
        } ?: throw AssertionError("query returned null cursor")
    }

    companion object {
        private fun sorted(vararg lines: String): List<String> {
            val list = ArrayList<String>()
            Collections.addAll(list, *lines)
            Collections.sort(list)
            return list
        }
    }
}
