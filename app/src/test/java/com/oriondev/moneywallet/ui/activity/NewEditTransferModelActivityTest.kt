package com.oriondev.moneywallet.ui.activity

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
class NewEditTransferModelActivityTest {

    @Test
    fun testTransferModelCompanionConstants() {
        assertEquals("NewEditItemActivity::Mode", NewEditTransferModelActivity.MODE)
        assertEquals("NewEditItemActivity::Id", NewEditTransferModelActivity.ID)
        assertEquals(NewEditItemActivity.Mode.NEW_ITEM, NewEditTransferModelActivity.Mode.NEW_ITEM)
        assertEquals(NewEditItemActivity.Mode.EDIT_ITEM, NewEditTransferModelActivity.Mode.EDIT_ITEM)
    }

    @Test
    fun testWalletFromAndToAssignment() {
        val currency = CurrencyUnit("EUR", "Euro", "€", 2)
        val walletA = Wallet(1L, "Checking", IconLoader.parse(""), currency, 1000L, 5000L)
        val walletB = Wallet(2L, "Savings", IconLoader.parse(""), currency, 2000L, 10000L)

        var from: Wallet? = walletA
        var to: Wallet? = walletB

        assertNotNull(from)
        assertNotNull(to)
        assertEquals(1L, from?.id)
        assertEquals(2L, to?.id)

        // Swap wallets
        val temp = from
        from = to
        to = temp

        assertEquals(2L, from?.id)
        assertEquals(1L, to?.id)
    }

    @Test
    fun testContractColumnNames() {
        assertEquals("model_transfer_description", Contract.TransferModel.DESCRIPTION)
        assertEquals("model_transfer_from_wallet", Contract.TransferModel.WALLET_FROM_ID)
        assertEquals("model_transfer_to_wallet", Contract.TransferModel.WALLET_TO_ID)
        assertEquals("model_transfer_from_money", Contract.TransferModel.MONEY_FROM)
        assertEquals("model_transfer_to_money", Contract.TransferModel.MONEY_TO)
        assertEquals("model_transfer_tax_money", Contract.TransferModel.MONEY_TAX)
        assertEquals("model_transfer_confirmed", Contract.TransferModel.CONFIRMED)
        assertEquals("model_transfer_count_in_total", Contract.TransferModel.COUNT_IN_TOTAL)
    }
}
