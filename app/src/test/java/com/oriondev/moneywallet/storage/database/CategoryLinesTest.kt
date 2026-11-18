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
import com.oriondev.moneywallet.model.Group
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.storage.wrapper.TransactionHeaderCursor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Collections

/**
 * The category lines over [SplitLedger], and the readers inside the database that group or
 * filter by category, which are a category budget's progress and list, the list of one category,
 * and the guards on deleting a category or changing its type.
 */
@RunWith(RobolectricTestRunner::class)
class CategoryLinesTest {

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
     * Every transaction reads as the lines worked out by hand, which covers each invalid split
     * reading as one line for its whole total, and its lines add up to its total.
     */
    @Test
    fun everyTransactionReadsAsItsLinesAndTheyAddUpToItsTotal() {
        assertLinesAreTheLedgers()
    }

    private fun assertLinesAreTheLedgers() {
        val read = HashMap<Long, ArrayList<String>>()
        val sums = HashMap<Long, Long>()
        val keys = HashSet<Long>()
        var rows = 0
        val cursor = resolver.query(
            DataContentProvider.CONTENT_CATEGORY_LINES,
            arrayOf(
                Contract.Transaction.ID,
                Contract.Transaction.CATEGORY_ID,
                Contract.Transaction.MONEY,
                Contract.Transaction.LINE_ID
            ),
            null,
            null,
            null
        )
        cursor?.use { c ->
            while (c.moveToNext()) {
                rows++
                val id = c.getLong(0)
                val money = c.getLong(2)
                assertTrue("a line below zero on $id", money >= 0)
                assertTrue("a key read twice", keys.add(c.getLong(3)))
                read.getOrPut(id) { ArrayList() }.add(c.getLong(1).toString() + ":" + money)
                sums[id] = (sums[id] ?: 0L) + money
            }
        } ?: fail("query returned null cursor")

        var expectedRows = 0
        for (transaction in SplitLedger.TRANSACTIONS) {
            val expected = ArrayList<String>()
            val linesForTx = SplitLedger.LINES[transaction[0]] ?: error("no lines for ${transaction[0]}")
            for (line in linesForTx) {
                expected.add(line[0].toString() + ":" + line[1])
            }
            expectedRows += expected.size
            Collections.sort(expected)
            val actual = read[transaction[0]] ?: ArrayList()
            Collections.sort(actual)
            assertEquals("lines of " + transaction[0], expected, actual)
            assertEquals("sum of " + transaction[0], transaction[5], sums[transaction[0]])
        }
        assertEquals(expectedRows, rows)
    }

    /** A line carries its transaction's own columns, and the category columns of its line. */
    @Test
    fun aLineCarriesItsTransactionAndItsOwnCategory() {
        val cursor = resolver.query(
            DataContentProvider.CONTENT_CATEGORY_LINES,
            null,
            Contract.Transaction.ID + " = ?",
            arrayOf(SplitLedger.WITH_HIDDEN.toString()),
            Contract.Transaction.LINE_ID
        )
        cursor?.use { c ->
            assertEquals(2, c.count)
            assertTrue(c.moveToFirst())
            // the main line first, since its key is the negated id
            assertEquals(SplitLedger.HOUSEHOLD, c.getLong(c.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_ID)))
            assertTrue(c.isNull(c.getColumnIndexOrThrow(Contract.Transaction.SPLIT_ID)))
            assertTrue(c.moveToNext())
            assertEquals(SplitLedger.HIDDEN, c.getLong(c.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_ID)))
            assertEquals(940L, c.getLong(c.getColumnIndexOrThrow(Contract.Transaction.SPLIT_ID)))
            assertEquals("Hidden", c.getString(c.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_NAME)))
            assertEquals(SplitLedger.HOUSEHOLD, c.getLong(c.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_PARENT_ID)))
            assertEquals(0, c.getInt(c.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_SHOW_REPORT)))
            assertEquals(1, c.getInt(c.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_PARENT_SHOW_REPORT)))
            assertEquals(Schema.CategoryType.EXPENSE, c.getInt(c.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_TYPE)))
            assertEquals(SplitLedger.DATE, c.getString(c.getColumnIndexOrThrow(Contract.Transaction.DATE)))
            assertEquals(SplitLedger.CASH, c.getLong(c.getColumnIndexOrThrow(Contract.Transaction.WALLET_ID)))
            assertEquals("EUR", c.getString(c.getColumnIndexOrThrow(Contract.Transaction.WALLET_CURRENCY)))
            assertEquals(SplitLedger.description(SplitLedger.WITH_HIDDEN), c.getString(c.getColumnIndexOrThrow(Contract.Transaction.DESCRIPTION)))
            assertEquals(Schema.Direction.EXPENSE, c.getInt(c.getColumnIndexOrThrow(Contract.Transaction.DIRECTION)))
            assertEquals(Contract.TransactionType.STANDARD, c.getInt(c.getColumnIndexOrThrow(Contract.Transaction.TYPE)))
            assertEquals(1, c.getInt(c.getColumnIndexOrThrow(Contract.Transaction.CONFIRMED)))
            assertEquals(1, c.getInt(c.getColumnIndexOrThrow(Contract.Transaction.COUNT_IN_TOTAL)))
        } ?: fail("query returned null cursor")
    }

    /** The transactions themselves still read with their totals and main categories. */
    @Test
    fun theTransactionsStillReadWhole() {
        val cursor = resolver.query(
            DataContentProvider.CONTENT_TRANSACTIONS,
            arrayOf(Contract.Transaction.ID, Contract.Transaction.CATEGORY_ID, Contract.Transaction.MONEY),
            null,
            null,
            null
        )
        cursor?.use { c ->
            assertEquals(SplitLedger.TRANSACTIONS.size, c.count)
            while (c.moveToNext()) {
                val transaction = SplitLedger.transaction(c.getLong(0))
                assertEquals(transaction[2], c.getLong(1))
                assertEquals(transaction[5], c.getLong(2))
            }
        } ?: fail("query returned null cursor")
    }

    /**
     * The progress of a Household budget is every Household line and every line of its hidden
     * child, which a budget covers and the reports do not. Lines of invalid splits are not here.
     */
    @Test
    fun aCategoryBudgetAddsUpItsLines() {
        val budget = SplitLedger.insertCategoryBudget(context, 960, SplitLedger.HOUSEHOLD)
        val household = 2000L + 2000 + (5000 - 1200) + 1200 + 1000 + 1500 + 2200
        assertEquals(-household, progressOf(budget))
    }

    /**
     * The list of a Household budget holds each transaction once, carrying its Household money,
     * and adds up to the progress.
     */
    @Test
    fun theListOfACategoryBudgetCarriesEachRowsShareAndAddsUpToItsProgress() {
        val budget = SplitLedger.insertCategoryBudget(context, 960, SplitLedger.HOUSEHOLD)
        val expected = HashMap<Long, Long>()
        expected[SplitLedger.RECEIPT] = 2000L
        expected[SplitLedger.CROSSED] = 2000L
        expected[SplitLedger.WITH_HIDDEN] = 5000L
        expected[SplitLedger.TWICE] = 1000L + 1500L
        expected[SplitLedger.PLAIN_BANK] = 2200L
        val rows = HashMap<Long, Long>()
        var signed = 0L
        val uri = Uri.withAppendedPath(ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budget), "transactions")
        val cursor = resolver.query(uri, null, null, null, null)
        cursor?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow(Contract.Transaction.ID))
                val money = c.getLong(c.getColumnIndexOrThrow(Contract.Transaction.MONEY))
                assertEquals("each row once", null, rows.put(id, money))
                assertEquals(SplitLedger.HOUSEHOLD, c.getLong(c.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_ID)))
                signed += if (c.getInt(c.getColumnIndexOrThrow(Contract.Transaction.DIRECTION)) == Schema.Direction.INCOME) money else -money
            }
        } ?: fail("query returned null cursor")
        assertEquals(expected, rows)
        assertEquals(progressOf(budget), signed)
    }

    /**
     * A row matching on two parts and not on its main line takes the category of the part with
     * the lower id, and one matching on its main line takes the main category.
     */
    @Test
    fun aRowMatchingOnPartsTakesTheCategoryOfItsFirstPart() {
        val budget = SplitLedger.insertCategoryBudget(context, 961, SplitLedger.FUEL, SplitLedger.PHARMACY)
        val rows = HashMap<Long, String>()
        val uri = Uri.withAppendedPath(ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budget), "transactions")
        val cursor = resolver.query(uri, null, null, null, null)
        cursor?.use { c ->
            while (c.moveToNext()) {
                rows[c.getLong(c.getColumnIndexOrThrow(Contract.Transaction.ID))] =
                    c.getLong(c.getColumnIndexOrThrow(Contract.Transaction.CATEGORY_ID)).toString() + ":" +
                        c.getLong(c.getColumnIndexOrThrow(Contract.Transaction.MONEY))
            }
        } ?: fail("query returned null cursor")
        val expected = HashMap<Long, String>()
        expected[SplitLedger.RECEIPT] = SplitLedger.FUEL.toString() + ":" + 3000
        // parts 932 in Pharmacy and 933 in Fuel, no main line
        expected[SplitLedger.EXACT] = SplitLedger.PHARMACY.toString() + ":" + (1500 + 2500)
        expected[SplitLedger.OVER] = SplitLedger.PHARMACY.toString() + ":" + 1000
        expected[SplitLedger.NEGATIVE] = SplitLedger.FUEL.toString() + ":" + 3000
        expected[SplitLedger.DEBT_ROW] = SplitLedger.FUEL.toString() + ":" + 6000
        assertEquals(expected, rows)
        assertEquals(-(3000L + 4000 + 1000 + 3000 + 6000), progressOf(budget))
    }

    /**
     * A category flagged deleted, which only a restore leaves behind, still named by a budget.
     * Its lines leave the progress, so they leave the list too. OVER read as one Pharmacy line,
     * and EXACT loses its valid parts since one sits in a deleted category, so it reads as one
     * Groceries line.
     */
    @Test
    fun theListOfACategoryBudgetLeavesOutLinesInADeletedCategory() {
        SplitLedger.database(context).execSQL(
            "UPDATE categories SET deleted = 1 WHERE category_id = " + SplitLedger.PHARMACY
        )
        val budget = SplitLedger.insertCategoryBudget(context, 962, SplitLedger.FUEL, SplitLedger.PHARMACY)
        val rows = HashMap<Long, Long>()
        var signed = 0L
        val uri = Uri.withAppendedPath(ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budget), "transactions")
        val cursor = resolver.query(uri, null, null, null, null)
        cursor?.use { c ->
            while (c.moveToNext()) {
                val money = c.getLong(c.getColumnIndexOrThrow(Contract.Transaction.MONEY))
                rows[c.getLong(c.getColumnIndexOrThrow(Contract.Transaction.ID))] = money
                signed += if (c.getInt(c.getColumnIndexOrThrow(Contract.Transaction.DIRECTION)) == Schema.Direction.INCOME) money else -money
            }
        } ?: fail("query returned null cursor")
        val expected = HashMap<Long, Long>()
        expected[SplitLedger.RECEIPT] = 3000L
        expected[SplitLedger.NEGATIVE] = 3000L
        expected[SplitLedger.DEBT_ROW] = 6000L
        assertEquals(expected, rows)
        assertEquals(-(3000L + 3000 + 6000), progressOf(budget))
        assertEquals(progressOf(budget), signed)
    }

    /**
     * The list of one category shows each row once with its share, and the header over it adds
     * up those shares.
     */
    @Test
    fun theListOfACategoryCarriesEachRowsShareAndSoDoesItsHeader() {
        val uri = Uri.withAppendedPath(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, SplitLedger.HOUSEHOLD), "transactions")
        val rows = HashMap<Long, Long>()
        val cursor = resolver.query(uri, null, null, null, Contract.Transaction.DATE + " DESC")
        cursor?.use { c ->
            while (c.moveToNext()) {
                assertEquals(
                    "each row once",
                    null,
                    rows.put(
                        c.getLong(c.getColumnIndexOrThrow(Contract.Transaction.ID)),
                        c.getLong(c.getColumnIndexOrThrow(Contract.Transaction.MONEY))
                    )
                )
            }
        } ?: fail("query returned null cursor")
        val expected = HashMap<Long, Long>()
        expected[SplitLedger.RECEIPT] = 2000L
        expected[SplitLedger.CROSSED] = 2000L
        // the hidden child is a child of Household, so its part is in this list
        expected[SplitLedger.WITH_HIDDEN] = 5000L
        expected[SplitLedger.TWICE] = 1000L + 1500L
        expected[SplitLedger.PLAIN_BANK] = 2200L
        assertEquals(expected, rows)

        val headerCursor = resolver.query(uri, null, null, null, Contract.Transaction.DATE + " DESC")
        headerCursor?.use { raw ->
            val headers = TransactionHeaderCursor(raw, Group.MONTHLY, null, null)
            headers.use { h ->
                assertTrue(h.moveToFirst())
                assertTrue(h.isHeaderAt(0))
                val household = 2000L + 2000 + 5000 + 2500 + 2200
                assertEquals("EUR $household", h.getString(h.getColumnIndexOrThrow(TransactionHeaderCursor.COLUMN_HEADER_EXPENSE)))
                assertEquals("EUR -$household", h.getString(h.getColumnIndexOrThrow(TransactionHeaderCursor.COLUMN_HEADER_MONEY)))
            }
        } ?: fail("query returned null cursor")
    }

    /** A category only a live part uses is refused the same way one a transaction uses is. */
    @Test
    fun aCategoryALivePartUsesCannotBeDeleted() {
        try {
            resolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, SplitLedger.BONUS), null, null)
            fail("a category a part uses was deleted")
        } catch (expected: SQLiteDataException) {
            assertEquals(Contract.ErrorCode.CATEGORY_IN_SPLIT, expected.errorCode)
            // the one part in Bonus is the one of PAY_SPLIT
            assertArrayEquals(longArrayOf(SplitLedger.PAY_SPLIT), expected.itemIds)
        }
        assertEquals(1L, count("categories", "category_id = " + SplitLedger.BONUS))
        assertEquals(1L, count("transaction_splits", "split_category = " + SplitLedger.BONUS))
    }

    /** A part flagged deleted does not hold a category, and leaves with it. */
    @Test
    fun aCategoryOnlyADeletedPartNamesCanBeDeleted() {
        assertEquals(1, resolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, SplitLedger.GIFTS), null, null))
        assertEquals(0L, count("categories", "category_id = " + SplitLedger.GIFTS))
        assertEquals(0L, count("transaction_splits", "split_category = " + SplitLedger.GIFTS))
    }

    /**
     * A part no screen shows, here one that takes the parts of OVER further past its total, does
     * not hold its category. It leaves with it, and every line still reads as before.
     */
    @Test
    fun aCategoryOnlyAnInvalidPartUsesCanBeDeletedAndNoLineMoves() {
        insertToolsPart(SplitLedger.OVER, 50)
        assertLinesAreTheLedgers()
        assertEquals(1, resolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, TOOLS), null, null))
        assertEquals(0L, count("categories", "category_id = $TOOLS"))
        assertEquals(0L, count("transaction_splits", "split_category = $TOOLS"))
        assertEquals(2L, count("transaction_splits", "_transaction = " + SplitLedger.OVER))
        assertLinesAreTheLedgers()
    }

    /**
     * A part that alone takes the parts of EXACT past its total keeps the others from being read,
     * so deleting its category would make them valid and move their money off the main category.
     */
    @Test
    fun aCategoryWhosePartAloneKeepsTheOthersInvalidCannotBeDeleted() {
        insertToolsPart(SplitLedger.EXACT, 100)
        try {
            resolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, TOOLS), null, null)
            fail("deleting the category made the other parts valid")
        } catch (expected: SQLiteDataException) {
            assertEquals(Contract.ErrorCode.CATEGORY_IN_SPLIT, expected.errorCode)
            assertArrayEquals(longArrayOf(SplitLedger.EXACT), expected.itemIds)
        }
        assertEquals(1L, count("transaction_splits", "split_category = $TOOLS"))
    }

    /**
     * The refusal lists the newest transaction first by its date, and on a tie the higher id first.
     * WITH_HIDDEN is moved after the rest, and RECEIPT and TWICE share the fixture's date.
     */
    @Test
    fun aDeleteRefusedListsTheNewestByDateFirst() {
        insertToolsPart(SplitLedger.RECEIPT, 100)
        val db = SplitLedger.database(context)
        db.execSQL(
            "INSERT INTO transaction_splits (split_id, _transaction, split_category, " +
                "split_money, uuid, last_edit, deleted) VALUES (972, ?, ?, 100, 'split-tools-twice', 1, 0)",
            arrayOf<Any>(SplitLedger.TWICE, TOOLS)
        )
        db.execSQL(
            "INSERT INTO transaction_splits (split_id, _transaction, split_category, " +
                "split_money, uuid, last_edit, deleted) VALUES (973, ?, ?, 100, 'split-tools-hidden', 1, 0)",
            arrayOf<Any>(SplitLedger.WITH_HIDDEN, TOOLS)
        )
        db.execSQL(
            "UPDATE transactions SET transaction_date = '2026-03-01 10:00:00' WHERE transaction_id = ?",
            arrayOf<Any>(SplitLedger.WITH_HIDDEN)
        )
        try {
            resolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, TOOLS), null, null)
            fail("a category three parts use was deleted")
        } catch (expected: SQLiteDataException) {
            assertEquals(Contract.ErrorCode.CATEGORY_IN_SPLIT, expected.errorCode)
            assertArrayEquals(longArrayOf(SplitLedger.WITH_HIDDEN, SplitLedger.TWICE, SplitLedger.RECEIPT), expected.itemIds)
        }
    }

    private fun insertToolsPart(transaction: Long, money: Long) {
        val db = SplitLedger.database(context)
        db.execSQL(
            "INSERT INTO categories (category_id, category_name, category_icon, category_type, " +
                "category_show_report, uuid, last_edit, deleted) VALUES (?, 'Tools', ?, ?, 1, " +
                "'category-tools', 1, 0)",
            arrayOf(TOOLS, SplitLedger.ICON, Schema.CategoryType.EXPENSE)
        )
        db.execSQL(
            "INSERT INTO transaction_splits (split_id, _transaction, split_category, " +
                "split_money, uuid, last_edit, deleted) VALUES (971, ?, ?, ?, 'split-tools', 1, 0)",
            arrayOf(transaction, TOOLS, money)
        )
    }

    /** Turning the type of a category a live part uses would leave the part in the other direction. */
    @Test
    fun theTypeOfACategoryALivePartUsesCannotChange() {
        assertTypeChangeRefused(SplitLedger.BONUS, Schema.CategoryType.EXPENSE)
    }

    /** Turning the type of the main category of a split would turn its direction away from its parts. */
    @Test
    fun theTypeOfTheMainCategoryOfASplitCannotChange() {
        assertTypeChangeRefused(SplitLedger.SALARY, Schema.CategoryType.EXPENSE)
    }

    /** A category no live part depends on changes type as before, and so does a rename. */
    @Test
    fun aCategoryNoLivePartDependsOnChangesType() {
        var values = ContentValues()
        values.put(Contract.Category.TYPE, Schema.CategoryType.INCOME)
        assertEquals(1, resolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, SplitLedger.GIFTS), values, null, null))
        assertEquals(1L, count("categories", "category_id = " + SplitLedger.GIFTS + " AND category_type = " + Schema.CategoryType.INCOME))
        values = ContentValues()
        values.put(Contract.Category.NAME, "Extra")
        values.put(Contract.Category.TYPE, Schema.CategoryType.INCOME)
        assertEquals(1, resolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, SplitLedger.BONUS), values, null, null))
        assertEquals(1L, count("categories", "category_id = " + SplitLedger.BONUS + " AND category_name = 'Extra'"))
    }

    private fun assertTypeChangeRefused(category: Long, type: Int) {
        val values = ContentValues()
        values.put(Contract.Category.TYPE, type)
        try {
            resolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, category), values, null, null)
            fail("the type changed under a split")
        } catch (expected: SQLiteDataException) {
            assertEquals(Contract.ErrorCode.CATEGORY_IN_USE, expected.errorCode)
        }
        assertFalse(count("categories", "category_id = $category AND category_type = $type") > 0)
        assertEquals(
            0L,
            count(
                "transactions",
                "transaction_category = " + category +
                    " AND transaction_direction = " + (if (type == Schema.CategoryType.INCOME) Schema.Direction.INCOME else Schema.Direction.EXPENSE)
            )
        )
    }

    private fun progressOf(budget: Long): Long {
        val cursor = resolver.query(
            DataContentProvider.CONTENT_BUDGETS,
            arrayOf(Contract.Budget.PROGRESS),
            Contract.Budget.ID + " = ?",
            arrayOf(budget.toString()),
            null
        )
        return cursor?.use { c ->
            assertTrue(c.moveToFirst())
            c.getLong(0)
        } ?: throw AssertionError("query returned null cursor")
    }

    private fun count(table: String, selection: String): Long {
        val cursor = SQLDatabase.getShared(context).readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $table WHERE $selection",
            null
        )
        return cursor.use { c ->
            assertTrue(c.moveToFirst())
            c.getLong(0)
        }
    }

    companion object {
        private const val TOOLS = 970L
    }
}
