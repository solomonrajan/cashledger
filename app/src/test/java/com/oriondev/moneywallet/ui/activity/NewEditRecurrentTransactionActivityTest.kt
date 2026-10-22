package com.oriondev.moneywallet.ui.activity

import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.RecurrenceSetting
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.utils.IconLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Date

@RunWith(RobolectricTestRunner::class)
class NewEditRecurrentTransactionActivityTest {

    @Test
    fun testRecurrentTransactionCompanionConstants() {
        assertEquals("NewEditItemActivity::Mode", NewEditRecurrentTransactionActivity.MODE)
        assertEquals("NewEditItemActivity::Id", NewEditRecurrentTransactionActivity.ID)
        assertEquals(NewEditItemActivity.Mode.NEW_ITEM, NewEditRecurrentTransactionActivity.Mode.NEW_ITEM)
        assertEquals(NewEditItemActivity.Mode.EDIT_ITEM, NewEditRecurrentTransactionActivity.Mode.EDIT_ITEM)
    }

    @Test
    fun testCategoryAssignment() {
        var category: Category? = null
        assertNull(category)

        category = Category(
            20L,
            "Rent & Utilities",
            IconLoader.parse(""),
            Contract.CategoryType.EXPENSE,
            "tag_housing"
        )
        assertNotNull(category)
        assertEquals(20L, category.id)
        assertEquals("Rent & Utilities", category.name)
        assertEquals(Contract.CategoryType.EXPENSE, category.type)
        assertEquals(Contract.Direction.EXPENSE, category.direction)
    }

    @Test
    fun testWalletAssignment() {
        val currency = CurrencyUnit("USD", "US Dollar", "$", 2)
        val wallet = Wallet(
            50L,
            "Primary Checking",
            IconLoader.parse(""),
            currency,
            10000L,
            50000L
        )
        assertNotNull(wallet)
        assertEquals(50L, wallet.id)
        assertEquals("Primary Checking", wallet.name)
        assertEquals("$", wallet.currency?.symbol)
    }

    @Test
    fun testRecurrenceSettingAssignment() {
        val now = Date()
        val setting = RecurrenceSetting(now, RecurrenceSetting.TYPE_MONTHLY)
        assertNotNull(setting)
        assertEquals(now, setting.startDate)
        assertNotNull(setting.rule)
    }

    @Test
    fun testContractColumnNames() {
        assertEquals("money", Contract.RecurrentTransaction.MONEY)
        assertEquals("description", Contract.RecurrentTransaction.DESCRIPTION)
        assertEquals("category", Contract.RecurrentTransaction.CATEGORY_ID)
        assertEquals("wallet", Contract.RecurrentTransaction.WALLET_ID)
        assertEquals("start_date", Contract.RecurrentTransaction.START_DATE)
        assertEquals("rule", Contract.RecurrentTransaction.RULE)
        assertEquals("confirmed", Contract.RecurrentTransaction.CONFIRMED)
        assertEquals("count_in_total", Contract.RecurrentTransaction.COUNT_IN_TOTAL)
    }
}
