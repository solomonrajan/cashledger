package com.oriondev.moneywallet.ui.activity

import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.utils.IconLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NewEditTransactionModelActivityTest {

    @Test
    fun testTransactionModelCompanionConstants() {
        assertEquals("NewEditItemActivity::Mode", NewEditTransactionModelActivity.MODE)
        assertEquals("NewEditItemActivity::Id", NewEditTransactionModelActivity.ID)
        assertEquals(NewEditItemActivity.Mode.NEW_ITEM, NewEditTransactionModelActivity.Mode.NEW_ITEM)
        assertEquals(NewEditItemActivity.Mode.EDIT_ITEM, NewEditTransactionModelActivity.Mode.EDIT_ITEM)
    }

    @Test
    fun testCategoryAssignment() {
        var category: Category? = null
        assertNull(category)

        category = Category(
            15L,
            "Dining",
            IconLoader.parse(""),
            Contract.CategoryType.EXPENSE,
            "tag_dining"
        )
        assertNotNull(category)
        assertEquals(15L, category.id)
        assertEquals("Dining", category.name)
        assertEquals(Contract.CategoryType.EXPENSE, category.type)
        assertEquals(Contract.Direction.EXPENSE, category.direction)
    }

    @Test
    fun testWalletAssignment() {
        val currency = CurrencyUnit("USD", "US Dollar", "$", 2)
        val wallet = Wallet(
            100L,
            "Main Account",
            IconLoader.parse(""),
            currency,
            5000L,
            25000L
        )
        assertNotNull(wallet)
        assertEquals(100L, wallet.id)
        assertEquals("Main Account", wallet.name)
        assertEquals("$", wallet.currency?.symbol)
    }

    @Test
    fun testContractColumnNames() {
        assertEquals("money", Contract.TransactionModel.MONEY)
        assertEquals("description", Contract.TransactionModel.DESCRIPTION)
        assertEquals("category", Contract.TransactionModel.CATEGORY_ID)
        assertEquals("wallet", Contract.TransactionModel.WALLET_ID)
        assertEquals("confirmed", Contract.TransactionModel.CONFIRMED)
        assertEquals("count_in_total", Contract.TransactionModel.COUNT_IN_TOTAL)
    }
}
