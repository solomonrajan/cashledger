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

import android.database.Cursor
import android.database.MatrixCursor
import android.view.ContextThemeWrapper
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Group
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.storage.wrapper.TransactionHeaderCursor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Collections
import java.util.Date
import java.util.HashSet

/**
 * Selection in the transaction lists.
 */
@RunWith(RobolectricTestRunner::class)
class TransactionSelectionTest {

    @Before
    fun clearTheStoredPeriods() {
        PreferenceManager.setCollapsedPeriods(emptySet())
    }

    @Test
    fun aLongPressSelectsARowAndATapThenTogglesInsteadOfOpeningIt() {
        val listener = Records()
        val adapter = adapter(listener, twoDays())
        val list = listWith(adapter)
        assertTrue(childAt(list, 1).performLongClick())
        assertEquals(ids(1L), selected(adapter))
        assertEquals(1, listener.selectionCount)
        layOut(list)
        assertTrue(childAt(list, 1).isActivated)
        assertFalse(childAt(list, 2).isActivated)
        childAt(list, 2).performClick()
        assertEquals(ids(1L, 2L), selected(adapter))
        layOut(list)
        childAt(list, 1).performClick()
        assertEquals(ids(2L), selected(adapter))
        assertEquals(-1L, listener.openedId)
        layOut(list)
        childAt(list, 2).performClick()
        assertTrue(selected(adapter).isEmpty())
        assertEquals(0, listener.selectionCount)
        layOut(list)
        childAt(list, 1).performClick()
        assertEquals(1L, listener.openedId)
    }

    @Test
    fun aTransferLegCannotBeSelectedByLongPressOrByTap() {
        val listener = Records()
        val adapter = adapter(listener, twoDays())
        val list = listWith(adapter)
        assertFalse(childAt(list, 3).performLongClick())
        assertTrue(selected(adapter).isEmpty())
        childAt(list, 1).performLongClick()
        layOut(list)
        childAt(list, 3).performClick()
        assertEquals(ids(1L), selected(adapter))
        assertEquals(-1L, listener.openedId)
    }

    @Test
    fun aHeaderCannotBeSelectedAndItsTapStillOpensTheReport() {
        val listener = Records()
        val adapter = adapter(listener, twoDays())
        val list = listWith(adapter)
        assertFalse(childAt(list, 0).performLongClick())
        assertTrue(selected(adapter).isEmpty())
        assertEquals(-1, listener.selectionCount)
        childAt(list, 1).performLongClick()
        layOut(list)
        childAt(list, 4).performClick()
        assertTrue(listener.headerClicked)
        assertEquals(ids(1L), selected(adapter))
    }

    @Test
    fun selectAllSkipsRowsInACollapsedPeriodAndTransferLegs() {
        val listener = Records()
        val adapter = adapter(listener, twoDays())
        adapter.selectAll()
        assertEquals(ids(1L, 2L, 4L, 5L), selected(adapter))
        assertEquals(4, listener.selectionCount)
        adapter.clearSelection()
        PreferenceManager.setCollapsedPeriods(Collections.singleton(FIRST_DAY))
        adapter.reloadCollapsedPeriods()
        adapter.selectAll()
        assertEquals(ids(4L, 5L), selected(adapter))
    }

    @Test
    fun withoutHeadersSelectAllTakesEveryRowButTheTransferLegs() {
        val adapter = adapter(
            Records(),
            bareCursor(
                longArrayOf(1L, 2L, 3L),
                intArrayOf(STANDARD, TRANSFER, STANDARD),
                arrayOf(DAY_15, DAY_15, DAY_14)
            )
        )
        adapter.selectAll()
        assertEquals(ids(1L, 3L), selected(adapter))
    }

    @Test
    fun theSelectionIsKeptByIdAcrossACursorSwapAndDropsIdsThatAreGone() {
        val listener = Records()
        val adapter = adapter(listener, twoDays())
        adapter.setSelectedIds(longArrayOf(2L, 4L))
        assertEquals(2, listener.selectionCount)
        adapter.changeCursor(
            TransactionHeaderCursor(
                bareCursor(
                    longArrayOf(2L, 7L, 5L),
                    intArrayOf(STANDARD, STANDARD, STANDARD),
                    arrayOf(DAY_15, DAY_15, DAY_14)
                ),
                Group.DAILY,
                null,
                null
            )
        )
        assertEquals(ids(2L), selected(adapter))
        assertEquals(1, listener.selectionCount)
        val list = listWith(adapter)
        assertTrue(childAt(list, 1).isActivated)
        assertFalse(childAt(list, 2).isActivated)
    }

    @Test
    fun aSelectedRowIsDroppedWhenItsPeriodIsCollapsedAndExpandingDoesNotBringItBack() {
        val listener = Records()
        val adapter = adapter(listener, twoDays())
        adapter.setSelectedIds(longArrayOf(1L, 4L))
        assertEquals(2, listener.selectionCount)
        adapter.togglePeriod(FIRST_DAY)
        assertEquals(ids(4L), selected(adapter))
        assertEquals(1, listener.selectionCount)
        adapter.togglePeriod(FIRST_DAY)
        assertEquals(ids(4L), selected(adapter))
        assertEquals(1, listener.selectionCount)
    }

    @Test
    fun aPeriodCollapsedInTheOtherListDropsItsSelectedRowsHereOnReload() {
        val listener = Records()
        val adapter = adapter(listener, twoDays())
        adapter.setSelectedIds(longArrayOf(1L, 4L))
        PreferenceManager.setCollapsedPeriods(Collections.singleton(FIRST_DAY))
        adapter.reloadCollapsedPeriods()
        assertEquals(ids(4L), selected(adapter))
        assertEquals(1, listener.selectionCount)
    }

    @Test
    fun aRestoredSelectionWaitsForTheFirstLoadWhenThePeriodsAreReloadedBeforeIt() {
        val listener = Records()
        val adapter = TransactionCursorAdapter(listener, true)
        adapter.setSelectedIds(longArrayOf(1L, 4L))
        PreferenceManager.setCollapsedPeriods(Collections.singleton(FIRST_DAY))
        adapter.reloadCollapsedPeriods()
        assertEquals(ids(1L, 4L), selected(adapter))
        adapter.changeCursor(twoDays())
        assertEquals(ids(4L), selected(adapter))
        assertEquals(1, listener.selectionCount)
    }

    @Test
    fun withoutHeadersACursorSwapDropsASelectedIdThatIsGoneAndKeepsTheOneStillThere() {
        val listener = Records()
        val adapter = adapter(
            listener,
            bareCursor(
                longArrayOf(1L, 2L, 3L),
                intArrayOf(STANDARD, STANDARD, STANDARD),
                arrayOf(DAY_15, DAY_15, DAY_14)
            )
        )
        adapter.setSelectedIds(longArrayOf(1L, 3L))
        adapter.changeCursor(
            bareCursor(
                longArrayOf(3L, 6L),
                intArrayOf(STANDARD, STANDARD),
                arrayOf(DAY_15, DAY_14)
            )
        )
        assertEquals(ids(3L), selected(adapter))
        assertEquals(1, listener.selectionCount)
    }

    private class Records : TransactionCursorAdapter.ActionListener {
        var openedId = -1L
        var selectionCount = -1
        var headerClicked = false

        override fun onHeaderClick(startDate: Date, endDate: Date) {
            headerClicked = true
        }

        override fun onTransactionClick(id: Long) {
            openedId = id
        }

        override fun onSelectionChanged(count: Int) {
            selectionCount = count
        }
    }

    companion object {
        private const val FIRST_DAY = "0:2019-03-15 00:00:00"
        private const val DAY_15 = "2019-03-15 10:00:00"
        private const val DAY_14 = "2019-03-14 10:00:00"

        private const val STANDARD = Contract.TransactionType.STANDARD
        private const val TRANSFER = Contract.TransactionType.TRANSFER

        private val COLUMNS = arrayOf(
            Contract.Transaction.ID,
            Contract.Transaction.TYPE,
            Contract.Transaction.DATE,
            Contract.Transaction.DIRECTION,
            Contract.Transaction.MONEY,
            Contract.Transaction.WALLET_CURRENCY,
            Contract.Transaction.CONFIRMED,
            Contract.Transaction.COUNT_IN_TOTAL,
            Contract.Transaction.CATEGORY_NAME,
            Contract.Transaction.CATEGORY_ICON,
            Contract.Transaction.DESCRIPTION
        )

        private fun bareCursor(ids: LongArray, types: IntArray, dates: Array<String>): MatrixCursor {
            val cursor = MatrixCursor(COLUMNS)
            for (row in ids.indices) {
                cursor.addRow(
                    arrayOf(
                        ids[row], types[row], dates[row], Contract.Direction.EXPENSE, 1000L, "EUR",
                        1, 1, "Category", null, "Description"
                    )
                )
            }
            return cursor
        }

        private fun twoDays(): TransactionHeaderCursor {
            return TransactionHeaderCursor(
                bareCursor(
                    longArrayOf(1L, 2L, 3L, 4L, 5L),
                    intArrayOf(STANDARD, STANDARD, TRANSFER, STANDARD, STANDARD),
                    arrayOf(DAY_15, DAY_15, DAY_15, DAY_14, DAY_14)
                ),
                Group.DAILY,
                null,
                null
            )
        }

        private fun adapter(listener: Records, cursor: Cursor): TransactionCursorAdapter {
            val adapter = TransactionCursorAdapter(listener, true)
            adapter.changeCursor(cursor)
            return adapter
        }

        private fun listWith(adapter: TransactionCursorAdapter): RecyclerView {
            val recyclerView = RecyclerView(
                ContextThemeWrapper(
                    ApplicationProvider.getApplicationContext(),
                    R.style.MoneyWalletAppTheme
                )
            )
            recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
            recyclerView.adapter = adapter
            layOut(recyclerView)
            return recyclerView
        }

        private fun layOut(recyclerView: RecyclerView) {
            recyclerView.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY)
            )
            recyclerView.layout(0, 0, 1080, 2400)
        }

        private fun childAt(recyclerView: RecyclerView, position: Int): View {
            val layoutManager = recyclerView.layoutManager
            assertNotNull("recyclerView has no layoutManager", layoutManager)
            val view = layoutManager?.findViewByPosition(position)
            assertNotNull("nothing was laid out at position $position", view)
            return view ?: View(recyclerView.context)
        }

        private fun selected(adapter: TransactionCursorAdapter): Set<Long> {
            val ids = HashSet<Long>()
            for (id in adapter.selectedIds) {
                ids.add(id)
            }
            return ids
        }

        private fun ids(vararg ids: Long): Set<Long> {
            return HashSet(ids.toList())
        }
    }
}
