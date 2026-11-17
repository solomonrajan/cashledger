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
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.res.Resources
import android.database.Cursor
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.RefusingDataContentProvider
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.database.TransferContentValuesBuilder
import com.oriondev.moneywallet.utils.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.util.Collections
import java.util.Date

/**
 * The bulk delete, move and category loops against the real provider and database.
 */
@RunWith(RobolectricTestRunner::class)
class TransactionSelectionModeTest {

    private lateinit var mResolver: ContentResolver

    companion object {
        private const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"
    }

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        TestDatabases.useFreshDatabase(context)
        mResolver = context.contentResolver
    }

    @Test
    fun aRefusedRowIsCountedAndTheRowsAfterItAreStillDeleted() {
        val cash = insertWallet("Cash", "EUR")
        val bank = insertWallet("Bank", "EUR")
        val category = insertCategory(Contract.CategoryType.EXPENSE)
        val before = insertTransaction(cash, category, 1250L)
        insertTransfer(cash, bank)
        val leg = idOfType(Contract.TransactionType.TRANSFER)
        val after = insertTransaction(cash, category, 1250L)
        val refused = TransactionSelectionMode.deleteTransactions(mResolver, longArrayOf(before, leg, after))
        assertEquals(Collections.singleton(leg), refused)
        assertFalse(rowExists(before))
        assertTrue(rowExists(leg))
        assertFalse(rowExists(after))
    }

    @Test
    fun aMoveBetweenCurrenciesWithTheSameDecimalsWritesOnlyTheWallet() {
        val euro = insertWallet("Cash", "EUR")
        val dollar = insertWallet("Bank", "USD")
        val category = insertCategory(Contract.CategoryType.EXPENSE)
        val id = insertTransaction(euro, category, 1250L)
        assertEquals(0, TransactionSelectionMode.moveTransactions(mResolver, longArrayOf(id), dollar, "USD"))
        assertEquals(dollar, longOf(id, Contract.Transaction.WALLET_ID))
        assertEquals(1250L, longOf(id, Contract.Transaction.MONEY))
        assertEquals(category, longOf(id, Contract.Transaction.CATEGORY_ID))
        assertEquals(Contract.Direction.EXPENSE.toLong(), longOf(id, Contract.Transaction.DIRECTION))
    }

    @Test
    fun aMoveFromTwoDecimalsToNoneKeepsTheNumberShownAndTruncates() {
        val dollar = insertWallet("Bank", "USD")
        val yen = insertWallet("Tokyo", "JPY")
        val id = insertTransaction(dollar, insertCategory(Contract.CategoryType.EXPENSE), 50055L)
        assertEquals(0, TransactionSelectionMode.moveTransactions(mResolver, longArrayOf(id), yen, "JPY"))
        assertEquals(yen, longOf(id, Contract.Transaction.WALLET_ID))
        assertEquals(500L, longOf(id, Contract.Transaction.MONEY))
    }

    @Test
    fun aMoveFromNoDecimalsToTwoKeepsTheNumberShown() {
        val yen = insertWallet("Tokyo", "JPY")
        val dollar = insertWallet("Bank", "USD")
        val id = insertTransaction(yen, insertCategory(Contract.CategoryType.EXPENSE), 500L)
        assertEquals(0, TransactionSelectionMode.moveTransactions(mResolver, longArrayOf(id), dollar, "USD"))
        assertEquals(dollar, longOf(id, Contract.Transaction.WALLET_ID))
        assertEquals(50000L, longOf(id, Contract.Transaction.MONEY))
    }

    @Test
    fun aCategoryChangeWritesTheCategoryAndTheDirectionOfIt() {
        val cash = insertWallet("Cash", "EUR")
        val id = insertTransaction(cash, insertCategory(Contract.CategoryType.EXPENSE), 1250L)
        val salary = insertCategory(Contract.CategoryType.INCOME)
        assertEquals(0, TransactionSelectionMode.changeCategory(mResolver, longArrayOf(id),
                Category(salary, "Salary", null, Contract.CategoryType.INCOME)))
        assertEquals(salary, longOf(id, Contract.Transaction.CATEGORY_ID))
        assertEquals(Contract.Direction.INCOME.toLong(), longOf(id, Contract.Transaction.DIRECTION))
        assertEquals(1250L, longOf(id, Contract.Transaction.MONEY))
        assertEquals(cash, longOf(id, Contract.Transaction.WALLET_ID))
    }

    @Test
    fun transferDebtAndSavingRowsAreNotMovedAndAreCounted() {
        val cash = insertWallet("Cash", "EUR")
        val bank = insertWallet("Bank", "EUR")
        val category = insertCategory(Contract.CategoryType.EXPENSE)
        val others = insertTransferDebtAndSaving(cash, bank)
        val plain = insertTransaction(cash, category, 1250L)
        val already = insertTransaction(bank, category, 1250L)
        val ids = longArrayOf(others[0], others[1], others[2], plain, already)
        assertEquals(3, TransactionSelectionMode.moveTransactions(mResolver, ids, bank, "EUR"))
        assertEquals(cash, longOf(others[0], Contract.Transaction.WALLET_ID))
        assertEquals(cash, longOf(others[1], Contract.Transaction.WALLET_ID))
        assertEquals(cash, longOf(others[2], Contract.Transaction.WALLET_ID))
        assertEquals(bank, longOf(plain, Contract.Transaction.WALLET_ID))
    }

    @Test
    fun transferDebtAndSavingRowsKeepTheirCategoryAndAreCounted() {
        val cash = insertWallet("Cash", "EUR")
        val bank = insertWallet("Bank", "EUR")
        val others = insertTransferDebtAndSaving(cash, bank)
        val plain = insertTransaction(cash, insertCategory(Contract.CategoryType.EXPENSE), 1250L)
        val target = insertCategory(Contract.CategoryType.EXPENSE)
        val debtCategory = longOf(others[1], Contract.Transaction.CATEGORY_ID)
        val savingCategory = longOf(others[2], Contract.Transaction.CATEGORY_ID)
        val ids = longArrayOf(others[0], others[1], others[2], plain)
        assertEquals(3, TransactionSelectionMode.changeCategory(mResolver, ids,
                Category(target, "Groceries", null, Contract.CategoryType.EXPENSE)))
        assertEquals(debtCategory, longOf(others[1], Contract.Transaction.CATEGORY_ID))
        assertEquals(savingCategory, longOf(others[2], Contract.Transaction.CATEGORY_ID))
        assertEquals(target, longOf(plain, Contract.Transaction.CATEGORY_ID))
    }

    @Test
    fun aRefusedMoveIsCountedAndTheRowsAfterItStillMove() {
        val provider = Robolectric.setupContentProvider(
                RefusingDataContentProvider::class.java, RefusingDataContentProvider.authority())
        val cash = insertWallet("Cash", "EUR")
        val bank = insertWallet("Bank", "EUR")
        val category = insertCategory(Contract.CategoryType.EXPENSE)
        val before = insertTransaction(cash, category, 1250L)
        val refused = insertTransaction(cash, category, 1250L)
        val after = insertTransaction(cash, category, 1250L)
        RefusingDataContentProvider.sRefusedId = refused
        try {
            assertEquals(1, TransactionSelectionMode.moveTransactions(mResolver, longArrayOf(before, refused, after), bank, "EUR"))
        } finally {
            RefusingDataContentProvider.sRefusedId = 0L
            provider.shutdown()
        }
        assertEquals(bank, longOf(before, Contract.Transaction.WALLET_ID))
        assertEquals(cash, longOf(refused, Contract.Transaction.WALLET_ID))
        assertEquals(bank, longOf(after, Contract.Transaction.WALLET_ID))
    }

    @Test
    fun theMoveMessageCountsOnlyRowsThatMoveAndNamesTheOtherCurrencyOnes() {
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        val cash = insertWallet("Cash", "EUR")
        val dollar = insertWallet("Dollar", "USD")
        val bank = insertWallet("Bank", "EUR")
        val category = insertCategory(Contract.CategoryType.EXPENSE)
        val euroRow = insertTransaction(cash, category, 1250L)
        val dollarRow = insertTransaction(dollar, category, 1250L)
        val others = insertTransferDebtAndSaving(cash, dollar)
        val already = insertTransaction(bank, category, 1250L)
        insertTransaction(dollar, category, 1250L)
        assertEquals("Move 2 Transactions To Bank? 1 Of Them Is In Another Currency. Its Amount Will Be Kept As Shown, Not Converted.",
                TransactionSelectionMode.buildMoveMessage(resources, mResolver,
                        longArrayOf(euroRow, dollarRow, others[0], others[1], others[2], already), bank, "Bank", "EUR"))
        assertEquals("Move 1 Transaction To Bank?",
                TransactionSelectionMode.buildMoveMessage(resources, mResolver,
                        longArrayOf(euroRow, others[1], already), bank, "Bank", "EUR"))
        assertEquals("Move 0 Transactions To Bank?",
                TransactionSelectionMode.buildMoveMessage(resources, mResolver,
                        longArrayOf(euroRow, dollarRow), bank, "Bank", null))
    }

    @Test
    fun theCategoryMessageCountsOnlyRowsThatChange() {
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        val cash = insertWallet("Cash", "EUR")
        val food = insertCategory(Contract.CategoryType.EXPENSE)
        val salary = insertCategory(Contract.CategoryType.INCOME)
        val first = insertTransaction(cash, food, 1250L)
        val second = insertTransaction(cash, food, 1250L)
        val already = insertTransaction(cash, salary, 1250L)
        val others = insertTransferDebtAndSaving(cash, insertWallet("Bank", "EUR"))
        insertTransaction(cash, food, 1250L)
        assertEquals("Change The Category Of 2 Transactions To Salary?",
                TransactionSelectionMode.buildCategoryMessage(resources, mResolver,
                        longArrayOf(first, second, already, others[0], others[1], others[2]), salary, "Salary"))
    }

    @Test
    fun aSnapshotMatchesOnlyTheSameNonEmptySelectionInAnyOrder() {
        assertTrue(TransactionSelectionMode.isSameSelection(longArrayOf(3L, 1L, 2L), longArrayOf(1L, 2L, 3L)))
        assertFalse(TransactionSelectionMode.isSameSelection(longArrayOf(1L, 2L, 4L), longArrayOf(1L, 2L, 3L)))
        assertFalse(TransactionSelectionMode.isSameSelection(longArrayOf(1L, 2L), longArrayOf(1L, 2L, 3L)))
        assertFalse(TransactionSelectionMode.isSameSelection(longArrayOf(), longArrayOf(1L)))
        assertFalse(TransactionSelectionMode.isSameSelection(longArrayOf(), longArrayOf()))
    }

    private fun insertTransferDebtAndSaving(fromWalletId: Long, toWalletId: Long): LongArray {
        insertTransfer(fromWalletId, toWalletId)
        val leg = idOfType(Contract.TransactionType.TRANSFER)
        insertDebt(fromWalletId)
        val debt = idOfType(Contract.TransactionType.DEBT)
        val saving = insertSavingTransaction(fromWalletId)
        return longArrayOf(leg, debt, saving)
    }

    private fun rowExists(id: Long): Boolean {
        val cursor: Cursor? = mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS,
                arrayOf(Contract.Transaction.ID), Contract.Transaction.ID + " = " + id, null, null)
        return cursor?.use { it.count > 0 } ?: false
    }

    private fun longOf(id: Long, column: String): Long {
        val cursor: Cursor? = mResolver.query(ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, id),
                arrayOf(column), null, null, null)
        return cursor?.use {
            assertTrue("no row $id", it.moveToFirst())
            it.getLong(0)
        } ?: throw AssertionError("Cursor was null for row $id")
    }

    private fun idOfType(type: Int): Long {
        val cursor: Cursor? = mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS,
                arrayOf(Contract.Transaction.ID),
                Contract.Transaction.TYPE + " = " + type, null, null)
        return cursor?.use {
            assertTrue("no row of type $type", it.moveToFirst())
            it.getLong(0)
        } ?: throw AssertionError("Cursor was null for type $type")
    }

    private fun insertWallet(name: String, currency: String): Long {
        val values = ContentValues()
        values.put(Contract.Wallet.NAME, name)
        values.put(Contract.Wallet.ICON, ICON)
        values.put(Contract.Wallet.CURRENCY, currency)
        values.put(Contract.Wallet.START_MONEY, 0L)
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true)
        values.put(Contract.Wallet.ARCHIVED, false)
        val uri = mResolver.insert(DataContentProvider.CONTENT_WALLETS, values)
        return if (uri != null) ContentUris.parseId(uri) else -1L
    }

    private fun insertCategory(type: Contract.CategoryType): Long {
        val values = ContentValues()
        values.put(Contract.Category.NAME, "Groceries")
        values.put(Contract.Category.ICON, ICON)
        values.put(Contract.Category.TYPE, type.value)
        values.put(Contract.Category.SHOW_REPORT, true)
        val uri = mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, values)
        return if (uri != null) ContentUris.parseId(uri) else -1L
    }

    private fun insertTransaction(walletId: Long, categoryId: Long, money: Long): Long {
        val values = ContentValues()
        values.put(Contract.Transaction.MONEY, money)
        values.put(Contract.Transaction.DATE, DateUtils.getSQLDateTimeString(Date()))
        values.put(Contract.Transaction.DESCRIPTION, "Bread")
        values.put(Contract.Transaction.CATEGORY_ID, categoryId)
        values.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE)
        values.put(Contract.Transaction.TYPE, Contract.TransactionType.STANDARD)
        values.put(Contract.Transaction.WALLET_ID, walletId)
        values.put(Contract.Transaction.CONFIRMED, true)
        values.put(Contract.Transaction.COUNT_IN_TOTAL, true)
        val uri = mResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values)
        return if (uri != null) ContentUris.parseId(uri) else -1L
    }

    private fun insertSavingTransaction(walletId: Long): Long {
        val saving = ContentValues()
        saving.put(Contract.Saving.DESCRIPTION, "Trip")
        saving.put(Contract.Saving.ICON, ICON)
        saving.put(Contract.Saving.START_MONEY, 0L)
        saving.put(Contract.Saving.END_MONEY, 10000L)
        saving.put(Contract.Saving.WALLET_ID, walletId)
        saving.put(Contract.Saving.COMPLETE, false)
        val savingUri = mResolver.insert(DataContentProvider.CONTENT_SAVINGS, saving)
        val savingId = if (savingUri != null) ContentUris.parseId(savingUri) else -1L

        val values = ContentValues()
        values.put(Contract.Transaction.MONEY, 300L)
        values.put(Contract.Transaction.DATE, DateUtils.getSQLDateTimeString(Date()))
        values.put(Contract.Transaction.DESCRIPTION, "Deposit")
        values.put(Contract.Transaction.CATEGORY_ID, insertCategory(Contract.CategoryType.EXPENSE))
        values.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE)
        values.put(Contract.Transaction.TYPE, Contract.TransactionType.SAVING)
        values.put(Contract.Transaction.WALLET_ID, walletId)
        values.put(Contract.Transaction.SAVING_ID, savingId)
        values.put(Contract.Transaction.CONFIRMED, true)
        values.put(Contract.Transaction.COUNT_IN_TOTAL, true)
        val uri = mResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values)
        return if (uri != null) ContentUris.parseId(uri) else -1L
    }

    private fun insertDebt(walletId: Long) {
        val values = ContentValues()
        values.put(Contract.Debt.TYPE, Contract.DebtType.DEBT.value)
        values.put(Contract.Debt.ICON, ICON)
        values.put(Contract.Debt.DESCRIPTION, "Loan")
        values.put(Contract.Debt.DATE, DateUtils.getSQLDateString(Date()))
        values.put(Contract.Debt.WALLET_ID, walletId)
        values.put(Contract.Debt.MONEY, 20000L)
        values.put(Contract.Debt.ARCHIVED, false)
        values.put(Contract.Debt.INSERT_MASTER_TRANSACTION, true)
        mResolver.insert(DataContentProvider.CONTENT_DEBTS, values)
    }

    private fun insertTransfer(fromWalletId: Long, toWalletId: Long) {
        val values = TransferContentValuesBuilder()
                .description("Move")
                .date(DateUtils.getSQLDateTimeString(Date()))
                .fromWalletId(fromWalletId)
                .toWalletId(toWalletId)
                .taxWalletId(fromWalletId)
                .fromMoney(500L)
                .toMoney(500L)
                .taxMoney(0L)
                .note("")
                .confirmed(true)
                .countInTotal(true)
                .build()
        mResolver.insert(DataContentProvider.CONTENT_TRANSFERS, values)
    }
}
