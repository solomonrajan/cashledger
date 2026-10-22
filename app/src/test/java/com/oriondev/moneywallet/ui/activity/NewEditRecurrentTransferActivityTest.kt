package com.oriondev.moneywallet.ui.activity

import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.RecurrenceSetting
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.utils.IconLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Date

@RunWith(RobolectricTestRunner::class)
class NewEditRecurrentTransferActivityTest {

    @Test
    fun testRecurrentTransferCompanionConstants() {
        assertEquals("NewEditItemActivity::Mode", NewEditRecurrentTransferActivity.MODE)
        assertEquals("NewEditItemActivity::Id", NewEditRecurrentTransferActivity.ID)
        assertEquals(NewEditItemActivity.Mode.NEW_ITEM, NewEditRecurrentTransferActivity.Mode.NEW_ITEM)
        assertEquals(NewEditItemActivity.Mode.EDIT_ITEM, NewEditRecurrentTransferActivity.Mode.EDIT_ITEM)
    }

    @Test
    fun testWalletFromAndToAssignmentAndSwap() {
        val currency = CurrencyUnit("GBP", "British Pound", "£", 2)
        val walletA = Wallet(10L, "Main Current", IconLoader.parse(""), currency, 2000L, 8000L)
        val walletB = Wallet(20L, "Savings Pot", IconLoader.parse(""), currency, 5000L, 25000L)

        var from: Wallet? = walletA
        var to: Wallet? = walletB

        assertNotNull(from)
        assertNotNull(to)
        assertEquals(10L, from?.id)
        assertEquals(20L, to?.id)

        // Swap wallets
        val temp = from
        from = to
        to = temp

        assertEquals(20L, from?.id)
        assertEquals(10L, to?.id)
    }

    @Test
    fun testRecurrenceSettingAssignment() {
        val now = Date()
        val setting = RecurrenceSetting(now, RecurrenceSetting.TYPE_WEEKLY)
        assertNotNull(setting)
        assertEquals(now, setting.startDate)
        assertNotNull(setting.rule)
    }

    @Test
    fun testContractColumnNames() {
        assertEquals("recurrent_transfer_description", Contract.RecurrentTransfer.DESCRIPTION)
        assertEquals("recurrent_transfer_from_wallet", Contract.RecurrentTransfer.WALLET_FROM_ID)
        assertEquals("recurrent_transfer_to_wallet", Contract.RecurrentTransfer.WALLET_TO_ID)
        assertEquals("recurrent_transfer_from_money", Contract.RecurrentTransfer.MONEY_FROM)
        assertEquals("recurrent_transfer_to_money", Contract.RecurrentTransfer.MONEY_TO)
        assertEquals("recurrent_transfer_tax_money", Contract.RecurrentTransfer.MONEY_TAX)
        assertEquals("recurrent_transfer_start_date", Contract.RecurrentTransfer.START_DATE)
        assertEquals("recurrent_transfer_rule", Contract.RecurrentTransfer.RULE)
        assertEquals("recurrent_transfer_confirmed", Contract.RecurrentTransfer.CONFIRMED)
        assertEquals("recurrent_transfer_count_in_total", Contract.RecurrentTransfer.COUNT_IN_TOTAL)
    }
}
