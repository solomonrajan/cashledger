package com.oriondev.moneywallet.ui.activity

import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.MoneyScale
import com.oriondev.moneywallet.storage.database.Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NewEditCurrencyActivityTest {

    @Test
    fun testCurrencyCompanionConstants() {
        assertEquals("NewEditCurrencyActivity::Mode", NewEditCurrencyActivity.MODE)
        assertEquals("NewEditCurrencyActivity::Iso", NewEditCurrencyActivity.ISO)
        assertEquals(NewEditCurrencyActivity.Mode.NEW_ITEM, NewEditCurrencyActivity.Mode.valueOf("NEW_ITEM"))
        assertEquals(NewEditCurrencyActivity.Mode.EDIT_ITEM, NewEditCurrencyActivity.Mode.valueOf("EDIT_ITEM"))
    }

    @Test
    fun testCurrencyContractColumnNames() {
        assertEquals("currency_name", Contract.Currency.NAME)
        assertEquals("currency_iso", Contract.Currency.ISO)
        assertEquals("currency_symbol", Contract.Currency.SYMBOL)
        assertEquals("currency_decimals", Contract.Currency.DECIMALS)
        assertEquals("currency_favourite", Contract.Currency.FAVOURITE)
        assertEquals("action_fix_money_decimals", Contract.Currency.FIX_MONEY_DECIMALS)
    }

    @Test
    fun testCurrencyUnitModelCreation() {
        val currency = CurrencyUnit("EUR", "Euro", "€", 2)
        assertNotNull(currency)
        assertEquals("EUR", currency.iso)
        assertEquals("Euro", currency.name)
        assertEquals("€", currency.symbol)
        assertEquals(2, currency.decimals)
    }

    @Test
    fun testMoneyScaleRescaling() {
        val rescaled2 = MoneyScale.rescale(1L, 2)
        assertEquals(100L, rescaled2)

        val rescaled4 = MoneyScale.rescale(1L, 4)
        assertEquals(10000L, rescaled4)
    }
}
