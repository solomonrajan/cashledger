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

package com.oriondev.moneywallet.storage.database

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Collections

/**
 * Parts written through the provider with a transaction, over [SplitLedger]. Every figure
 * expected below is worked out from the ledger's rows and the parts each case writes.
 */
@RunWith(RobolectricTestRunner::class)
class SplitWriteTest {

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

    @Test
    fun anInsertWithPartsStoresThemAndReadsAsSplit() {
        val values = newExpense(9000L)
        values.put(
            Contract.Transaction.SPLIT_PARTS,
            parts(part(SplitLedger.HOUSEHOLD, 2000, "soap"), part(SplitLedger.FUEL, 1500, null))
        )
        val uri = resolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values)
        val id = ContentUris.parseId(requireNotNull(uri))
        assertEquals(sorted(SplitLedger.HOUSEHOLD.toString() + ":2000:soap", SplitLedger.FUEL.toString() + ":1500:null"), partsOf(id))
        assertEquals(
            sorted(
                SplitLedger.GROCERIES.toString() + ":" + (9000 - 2000 - 1500),
                SplitLedger.HOUSEHOLD.toString() + ":2000",
                SplitLedger.FUEL.toString() + ":1500"
            ),
            linesOf(id)
        )
        // Groceries for what is left, Household and Fuel
        assertEquals(3L, splitCategoriesOf(id))
    }

    /**
     * A part in the main category itself, with money left over: the lines are Groceries for what
     * is left, Groceries for the part and Household, which is two categories.
     */
    @Test
    fun aPartInTheMainCategoryIsCountedOnce() {
        val values = newExpense(9000L)
        values.put(
            Contract.Transaction.SPLIT_PARTS,
            parts(part(SplitLedger.GROCERIES, 1000, null), part(SplitLedger.HOUSEHOLD, 2000, null))
        )
        val uri = resolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values)
        val id = ContentUris.parseId(requireNotNull(uri))
        assertEquals(
            sorted(
                SplitLedger.GROCERIES.toString() + ":" + (9000 - 1000 - 2000),
                SplitLedger.GROCERIES.toString() + ":1000",
                SplitLedger.HOUSEHOLD.toString() + ":2000"
            ),
            linesOf(id)
        )
        assertEquals(2L, splitCategoriesOf(id))
    }

    /** The per category list reads the shares view, which carries the count too. */
    @Test
    fun theSharesCarryTheCountOfCategories() {
        val cursor = resolver.query(
            DataContentProvider.CONTENT_CATEGORY_SHARES,
            arrayOf(Contract.Transaction.ID, Contract.Transaction.SPLIT_CATEGORIES),
            Contract.Transaction.CATEGORY_ID + " = ?",
            arrayOf(SplitLedger.HOUSEHOLD.toString()),
            Contract.Transaction.ID
        )
        cursor?.use { c ->
            val rows = ArrayList<String>()
            while (c.moveToNext()) {
                rows.add(c.getLong(0).toString() + ":" + c.getLong(1))
            }
            // the rows with a Household line. CROSSED and PLAIN_BANK read as unsplit, WITH_HIDDEN
            // as Household and Hidden. OVER is filed under Pharmacy, and its parts are not read
            assertEquals(
                listOf(
                    SplitLedger.RECEIPT.toString() + ":3",
                    SplitLedger.CROSSED.toString() + ":0",
                    SplitLedger.WITH_HIDDEN.toString() + ":2",
                    SplitLedger.TWICE.toString() + ":2",
                    SplitLedger.PLAIN_BANK.toString() + ":0"
                ),
                rows
            )
        } ?: fail("query returned null cursor")
    }

    @Test
    fun anUpdateWithPartsReplacesEveryPartTheRowHad() {
        val values = ContentValues()
        values.put(Contract.Transaction.SPLIT_PARTS, parts(part(SplitLedger.PHARMACY, 4000, "drops")))
        assertEquals(1, update(SplitLedger.RECEIPT, values))
        // the part flagged deleted goes too, the row holds exactly the list
        assertEquals(sorted(SplitLedger.PHARMACY.toString() + ":4000:drops:0"), allPartsOf(SplitLedger.RECEIPT))
        assertEquals(
            sorted(SplitLedger.GROCERIES.toString() + ":" + (10000 - 4000), SplitLedger.PHARMACY.toString() + ":4000"),
            linesOf(SplitLedger.RECEIPT)
        )
    }

    @Test
    fun anUpdateWithoutThePartsLeavesThemAlone() {
        val before = allPartsOf(SplitLedger.RECEIPT)
        val values = ContentValues()
        values.put(Contract.Transaction.DESCRIPTION, "edited")
        values.put(Contract.Transaction.MONEY, 12000L)
        assertEquals(1, update(SplitLedger.RECEIPT, values))
        assertEquals(before, allPartsOf(SplitLedger.RECEIPT))
        assertEquals(
            sorted(
                SplitLedger.GROCERIES.toString() + ":" + (12000 - 2000 - 3000),
                SplitLedger.HOUSEHOLD.toString() + ":2000",
                SplitLedger.FUEL.toString() + ":3000"
            ),
            linesOf(SplitLedger.RECEIPT)
        )
    }

    @Test
    fun anEmptyListLeavesTheRowUnsplit() {
        val values = ContentValues()
        values.put(Contract.Transaction.SPLIT_PARTS, "[]")
        assertEquals(1, update(SplitLedger.RECEIPT, values))
        assertEquals(emptyList<String>(), allPartsOf(SplitLedger.RECEIPT))
        assertEquals(sorted(SplitLedger.GROCERIES.toString() + ":10000"), linesOf(SplitLedger.RECEIPT))
        assertEquals(0L, splitCategoriesOf(SplitLedger.RECEIPT))
    }

    @Test
    fun anEmptyListIsTakenOnARowThatCannotBeSplit() {
        val values = ContentValues()
        values.put(Contract.Transaction.SPLIT_PARTS, "[]")
        assertEquals(1, update(SplitLedger.DEBT_ROW, values))
        assertEquals(emptyList<String>(), allPartsOf(SplitLedger.DEBT_ROW))
    }

    @Test
    fun aPartOfZeroIsRefused() {
        assertUpdateRefused(
            SplitLedger.RECEIPT,
            parts(part(SplitLedger.HOUSEHOLD, 2000, null), part(SplitLedger.FUEL, 0, null))
        )
    }

    @Test
    fun aPartBelowZeroIsRefused() {
        assertUpdateRefused(SplitLedger.RECEIPT, parts(part(SplitLedger.HOUSEHOLD, -1, null)))
    }

    @Test
    fun partsAboveTheTotalAreRefused() {
        assertUpdateRefused(
            SplitLedger.RECEIPT,
            parts(part(SplitLedger.HOUSEHOLD, 6000, null), part(SplitLedger.FUEL, 4001, null))
        )
    }

    /** The same parts are refused once the total of the same write drops under them. */
    @Test
    fun aTotalLoweredUnderThePartsIsRefused() {
        val values = ContentValues()
        values.put(Contract.Transaction.MONEY, 4999L)
        values.put(
            Contract.Transaction.SPLIT_PARTS,
            parts(part(SplitLedger.HOUSEHOLD, 2000, null), part(SplitLedger.FUEL, 3000, null))
        )
        assertRefusedAndUnchanged(SplitLedger.RECEIPT, values)
    }

    @Test
    fun aPartOfTheOtherDirectionIsRefused() {
        assertUpdateRefused(SplitLedger.RECEIPT, parts(part(SplitLedger.SALARY, 1000, null)))
    }

    /** Turning the row to income keeps the expense parts, which are then of the other direction. */
    @Test
    fun aDirectionFlipUnderExpensePartsIsRefused() {
        val values = ContentValues()
        values.put(Contract.Transaction.CATEGORY_ID, SplitLedger.SALARY)
        values.put(Contract.Transaction.DIRECTION, Contract.Direction.INCOME)
        values.put(Contract.Transaction.SPLIT_PARTS, parts(part(SplitLedger.HOUSEHOLD, 2000, null)))
        assertRefusedAndUnchanged(SplitLedger.RECEIPT, values)
    }

    @Test
    fun aPartInADeletedCategoryIsRefused() {
        SplitLedger.database(context).execSQL(
            "UPDATE categories SET deleted = 1 WHERE category_id = " + SplitLedger.SPARE
        )
        assertUpdateRefused(SplitLedger.RECEIPT, parts(part(SplitLedger.SPARE, 1000, null)))
    }

    /** Added plainly, two halves of the largest long wrap below zero and pass the total of 10000. */
    @Test
    fun partsAddingUpPastTheLargestLongAreRefused() {
        val half = Long.MAX_VALUE / 2 + 1
        assertUpdateRefused(
            SplitLedger.RECEIPT,
            parts(part(SplitLedger.HOUSEHOLD, half, null), part(SplitLedger.FUEL, half, null))
        )
    }

    @Test
    fun aPartInASystemCategoryIsRefused() {
        val system: Long
        val cursor = SplitLedger.database(context).rawQuery(
            "SELECT category_id FROM categories WHERE category_type = " + Schema.CategoryType.SYSTEM,
            null
        )
        cursor.use { c ->
            assertTrue("no system category", c.moveToFirst())
            system = c.getLong(0)
        }
        assertUpdateRefused(SplitLedger.RECEIPT, parts(part(system, 1000, null)))
    }

    @Test
    fun partsOnARowThatIsNotStandardAreRefused() {
        assertUpdateRefused(SplitLedger.DEBT_ROW, parts(part(SplitLedger.GROCERIES, 1000, null)))
    }

    /** A refused insert leaves neither the row nor any part behind. */
    @Test
    fun aRefusedInsertTakesTheRowBackOut() {
        val transactions = count("transactions", "1")
        val parts = count("transaction_splits", "1")
        val values = newExpense(3000L)
        values.put(
            Contract.Transaction.SPLIT_PARTS,
            parts(part(SplitLedger.HOUSEHOLD, 2000, null), part(SplitLedger.FUEL, 1001, null))
        )
        try {
            resolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values)
            fail("parts above the total were stored")
        } catch (expected: SQLiteDataException) {
            assertEquals(Contract.ErrorCode.SPLIT_NOT_VALID, expected.errorCode)
        }
        assertEquals(transactions, count("transactions", "1"))
        assertEquals(parts, count("transaction_splits", "1"))
    }

    // fixtures

    private fun newExpense(money: Long): ContentValues {
        val values = ContentValues()
        values.put(Contract.Transaction.MONEY, money)
        values.put(Contract.Transaction.DATE, SplitLedger.DATE)
        values.put(Contract.Transaction.DESCRIPTION, "new")
        values.put(Contract.Transaction.CATEGORY_ID, SplitLedger.GROCERIES)
        values.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE)
        values.put(Contract.Transaction.TYPE, Contract.TransactionType.STANDARD)
        values.put(Contract.Transaction.WALLET_ID, SplitLedger.CASH)
        values.put(Contract.Transaction.CONFIRMED, true)
        values.put(Contract.Transaction.COUNT_IN_TOTAL, true)
        return values
    }

    private fun update(id: Long, values: ContentValues): Int {
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, id)
        return resolver.update(uri, values, null, null)
    }

    private fun assertUpdateRefused(id: Long, parts: String) {
        val values = ContentValues()
        // a change to the row in the same write, which the refusal has to take back too
        values.put(Contract.Transaction.DESCRIPTION, "refused")
        values.put(Contract.Transaction.SPLIT_PARTS, parts)
        assertRefusedAndUnchanged(id, values)
    }

    private fun assertRefusedAndUnchanged(id: Long, values: ContentValues) {
        val parts = allPartsOf(id)
        val lines = linesOf(id)
        val row = SplitLedger.transaction(id)
        try {
            update(id, values)
            fail("the parts were taken")
        } catch (expected: SQLiteDataException) {
            assertEquals(Contract.ErrorCode.SPLIT_NOT_VALID, expected.errorCode)
        }
        assertEquals(parts, allPartsOf(id))
        assertEquals(lines, linesOf(id))
        assertEquals(
            1L,
            count(
                "transactions",
                "transaction_id = " + id + " AND transaction_money = " +
                    row[5] + " AND transaction_category = " + row[2] + " AND transaction_direction = " +
                    row[3] + " AND transaction_description = '" + SplitLedger.description(id) + "'"
            )
        )
    }

    // reading back

    /** The live parts of a row as category:money:note. */
    private fun partsOf(transaction: Long): List<String> {
        return rows(
            "SELECT split_category || ':' || split_money || ':' || IFNULL(split_note, 'null') " +
                "FROM transaction_splits WHERE deleted = 0 AND _transaction = $transaction"
        )
    }

    /** Every part a row has, flagged deleted or not. */
    private fun allPartsOf(transaction: Long): List<String> {
        return rows(
            "SELECT split_category || ':' || split_money || ':' || IFNULL(split_note, 'null') || ':' || " +
                "deleted FROM transaction_splits WHERE _transaction = $transaction"
        )
    }

    private fun rows(sql: String): List<String> {
        val rowsList = ArrayList<String>()
        val cursor = SplitLedger.database(context).rawQuery(sql, null)
        cursor.use { c ->
            while (c.moveToNext()) {
                rowsList.add(c.getString(0))
            }
        }
        Collections.sort(rowsList)
        return rowsList
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

    private fun splitCategoriesOf(transaction: Long): Long {
        val cursor = resolver.query(
            DataContentProvider.CONTENT_TRANSACTIONS,
            arrayOf(Contract.Transaction.SPLIT_CATEGORIES),
            Contract.Transaction.ID + " = ?",
            arrayOf(transaction.toString()),
            null
        )
        return cursor?.use { c ->
            assertTrue(c.moveToFirst())
            c.getLong(0)
        } ?: throw AssertionError("query returned null cursor")
    }

    private fun count(table: String, selection: String): Long {
        val cursor = SplitLedger.database(context).rawQuery(
            "SELECT COUNT(*) FROM $table WHERE $selection",
            null
        )
        return cursor.use { c ->
            assertTrue(c.moveToFirst())
            c.getLong(0)
        }
    }

    companion object {
        private fun part(category: Long, money: Long, note: String?): JSONObject {
            val part = JSONObject()
            part.put(Contract.TransactionSplit.PART_CATEGORY, category)
            part.put(Contract.TransactionSplit.PART_MONEY, money)
            part.put(Contract.TransactionSplit.PART_NOTE, note ?: JSONObject.NULL)
            return part
        }

        private fun parts(vararg parts: JSONObject): String {
            val array = JSONArray()
            for (p in parts) {
                array.put(p)
            }
            return array.toString()
        }

        private fun sorted(vararg rows: String): List<String> {
            val list = ArrayList<String>()
            Collections.addAll(list, *rows)
            Collections.sort(list)
            return list
        }
    }
}
