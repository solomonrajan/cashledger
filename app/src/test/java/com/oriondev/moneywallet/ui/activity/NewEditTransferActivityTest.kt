package com.oriondev.moneywallet.ui.activity

import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.utils.IconLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Date

@RunWith(RobolectricTestRunner::class)
class NewEditTransferActivityTest {

    @Test
    fun testCompanionConstants() {
        assertEquals("NewEditTransferActivity::Type", NewEditTransferActivity.TYPE)
        assertEquals("NewEditTransferActivity::ModelId", NewEditTransferActivity.MODEL_ID)
        assertEquals(0, NewEditTransferActivity.TYPE_STANDARD)
        assertEquals(1, NewEditTransferActivity.TYPE_MODEL)
    }

    @Test
    fun testTransferScreenStateDefaults() {
        val state = TransferScreenState()
        assertEquals(0L, state.money)
        assertEquals(0L, state.tax)
        assertEquals("", state.description)
        assertEquals("", state.note)
        assertTrue(state.confirmed)
        assertTrue(state.countInTotal)
        assertTrue(state.people.isEmpty())
        assertTrue(state.attachments.isEmpty())
        assertEquals(NewEditItemActivity.Mode.NEW_ITEM, state.mode)
    }

    @Test
    fun testTransferWalletSwapping() {
        val currency = CurrencyUnit("EUR", "Euro", "€", 2)
        val walletA = Wallet(1L, "Checking", IconLoader.parse(""), currency, 1000L, 5000L)
        val walletB = Wallet(2L, "Savings", IconLoader.parse(""), currency, 2000L, 10000L)

        var state = TransferScreenState(
            walletFrom = walletA,
            walletTo = walletB,
            money = 50000L
        )

        assertEquals(1L, state.walletFrom?.id)
        assertEquals(2L, state.walletTo?.id)

        // Swap wallets
        val swapped = state.copy(
            walletFrom = state.walletTo,
            walletTo = state.walletFrom
        )

        assertEquals(2L, swapped.walletFrom?.id)
        assertEquals(1L, swapped.walletTo?.id)
        assertNotEquals(swapped.walletFrom?.id, swapped.walletTo?.id)
    }
}
