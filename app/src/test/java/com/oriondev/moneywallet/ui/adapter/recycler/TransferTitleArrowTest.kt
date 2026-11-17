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

package com.oriondev.moneywallet.ui.adapter.recycler

import android.database.MatrixCursor
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The model and recurrence transfer rows title a transfer the way the transfer list does, with an
 * ASCII arrow.
 */
@RunWith(RobolectricTestRunner::class)
class TransferTitleArrowTest {

    @Test
    fun aTransferModelIsTitledWithTheAsciiArrow() {
        val cursor = MatrixCursor(
            arrayOf(
                Contract.TransferModel.ID,
                Contract.TransferModel.WALLET_FROM_NAME,
                Contract.TransferModel.WALLET_FROM_CURRENCY,
                Contract.TransferModel.WALLET_TO_NAME,
                Contract.TransferModel.WALLET_TO_ICON,
                Contract.TransferModel.MONEY_FROM,
                Contract.TransferModel.MONEY_TAX
            )
        )
        cursor.addRow(arrayOf(1L, FROM, "EUR", TO, ICON, 1000L, 0L))
        val adapter = TransferModelCursorAdapter(null)
        adapter.changeCursor(cursor)
        assertEquals("$FROM -> $TO", titleOf(adapter))
    }

    @Test
    fun aRecurrentTransferIsTitledWithTheAsciiArrow() {
        val cursor = MatrixCursor(
            arrayOf(
                Contract.RecurrentTransfer.ID,
                Contract.RecurrentTransfer.WALLET_FROM_NAME,
                Contract.RecurrentTransfer.WALLET_FROM_CURRENCY,
                Contract.RecurrentTransfer.WALLET_TO_NAME,
                Contract.RecurrentTransfer.WALLET_TO_ICON,
                Contract.RecurrentTransfer.MONEY_FROM,
                Contract.RecurrentTransfer.NEXT_OCCURRENCE
            )
        )
        cursor.addRow(arrayOf(1L, FROM, "EUR", TO, ICON, 1000L, null))
        val adapter = RecurrentTransferCursorAdapter(null)
        adapter.changeCursor(cursor)
        assertEquals("$FROM -> $TO", titleOf(adapter))
    }

    companion object {
        private const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"
        private const val FROM = "Cash"
        private const val TO = "Bank"

        private fun titleOf(adapter: RecyclerView.Adapter<*>): String {
            val recyclerView = RecyclerView(
                ContextThemeWrapper(
                    ApplicationProvider.getApplicationContext(),
                    R.style.MoneyWalletAppTheme
                )
            )
            recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
            recyclerView.adapter = adapter
            recyclerView.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY)
            )
            recyclerView.layout(0, 0, 1080, 2400)
            val layoutManager = recyclerView.layoutManager
            assertNotNull("recyclerView has no layoutManager", layoutManager)
            val row = layoutManager?.findViewByPosition(0)
            assertNotNull("nothing was laid out where the row belongs", row)
            val title = row?.findViewById<TextView>(R.id.secondary_text_view)
            return title?.text?.toString() ?: ""
        }
    }
}
