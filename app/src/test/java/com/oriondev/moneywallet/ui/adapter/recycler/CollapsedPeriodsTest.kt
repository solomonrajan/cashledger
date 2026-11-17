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
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Group
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.storage.wrapper.TransactionHeaderCursor
import com.oriondev.moneywallet.utils.DateUtils
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
 * A header the user has folded keeps its transactions off the list.
 */
@RunWith(RobolectricTestRunner::class)
class CollapsedPeriodsTest {

    @Before
    fun clearTheStoredPeriods() {
        PreferenceManager.setCollapsedPeriods(emptySet())
    }

    private fun bareCursor(): MatrixCursor {
        val cursor = MatrixCursor(COLUMNS)
        for (row in DATES.indices) {
            cursor.addRow(
                arrayOf(
                    (row + 1).toLong(), DATES[row], Contract.Direction.EXPENSE, 1000L, "EUR",
                    1, 1, "Category", null, "Description"
                )
            )
        }
        return cursor
    }

    private fun cursor(): TransactionHeaderCursor {
        return TransactionHeaderCursor(bareCursor(), Group.DAILY, null, null)
    }

    private fun adapter(): TransactionCursorAdapter {
        val adapter = TransactionCursorAdapter(RecordsNothing(), false)
        adapter.changeCursor(cursor())
        return adapter
    }

    private fun fold(adapter: TransactionCursorAdapter, vararg keys: String) {
        PreferenceManager.setCollapsedPeriods(HashSet(listOf(*keys)))
        adapter.reloadCollapsedPeriods()
    }

    private fun assertTypes(adapter: TransactionCursorAdapter, vararg types: Int) {
        assertEquals(types.size, adapter.itemCount)
        for (position in types.indices) {
            assertEquals(
                "wrong type at position $position",
                types[position],
                adapter.getItemViewType(position)
            )
        }
    }

    @Test
    fun withNothingStoredEveryTransactionIsUnderItsHeader() {
        val adapter = adapter()
        assertTypes(adapter, HEADER, ITEM, ITEM, ITEM, HEADER, ITEM, ITEM)
    }

    @Test
    fun foldingADayLeavesItsHeaderAndTakesItsTransactions() {
        val adapter = adapter()
        fold(adapter, FIRST_DAY)
        assertTypes(adapter, HEADER, HEADER, ITEM, ITEM)
        assertEquals(4L, adapter.getItemId(2))
        assertEquals(5L, adapter.getItemId(3))
    }

    @Test
    fun foldingTheSameDayAgainBringsItsTransactionsBack() {
        val adapter = adapter()
        fold(adapter, FIRST_DAY)
        fold(adapter)
        assertTypes(adapter, HEADER, ITEM, ITEM, ITEM, HEADER, ITEM, ITEM)
        assertTrue(PreferenceManager.getCollapsedPeriods().isEmpty())
    }

    @Test
    fun aFoldSurvivesTheListBeingLoadedAgain() {
        val adapter = adapter()
        fold(adapter, FIRST_DAY)
        adapter.changeCursor(cursor())
        assertTypes(adapter, HEADER, HEADER, ITEM, ITEM)
    }

    @Test
    fun aDayAndTheMonthItOpensAreNotTheSameFold() {
        val adapter = adapter()
        fold(adapter, FIRST_DAY_AS_A_MONTH)
        assertTypes(adapter, HEADER, ITEM, ITEM, ITEM, HEADER, ITEM, ITEM)
    }

    @Test
    fun aFoldWrittenByAnotherScreenIsSeenOnlyOnceThisListReadsAgain() {
        val adapter = adapter()
        PreferenceManager.setCollapsedPeriods(HashSet(listOf(FIRST_DAY)))
        assertEquals(7, adapter.itemCount)
        adapter.reloadCollapsedPeriods()
        assertEquals(4, adapter.itemCount)
    }

    @Test
    fun foldingTheSecondDayLeavesTheFirstDayWhereItWas() {
        val adapter = adapter()
        fold(adapter, SECOND_DAY)
        assertTypes(adapter, HEADER, ITEM, ITEM, ITEM, HEADER)
        assertEquals(1L, adapter.getItemId(1))
        assertEquals(2L, adapter.getItemId(2))
        assertEquals(3L, adapter.getItemId(3))
    }

    @Test
    fun aFoldedHeaderBindsTheHeaderThatIsOnScreen() {
        val adapter = adapter()
        fold(adapter, FIRST_DAY)
        val parent = FrameLayout(
            ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(),
                R.style.MoneyWalletAppTheme
            )
        )
        val onScreen = adapter.onCreateViewHolder(parent, HEADER)
        adapter.onBindViewHolder(onScreen, 1)
        val leftTextView = onScreen.itemView.findViewById<TextView>(R.id.left_text_view)
        val dateRange = leftTextView.text.toString()
        assertTrue("the header drawn at position 1 was $dateRange", dateRange.contains("14"))
        val folded = adapter.onCreateViewHolder(parent, HEADER)
        adapter.onBindViewHolder(folded, 0)
    }

    @Test
    fun theArrowFoldsAndUnfoldsThroughTheAdapter() {
        val adapter = adapter()
        adapter.togglePeriod(FIRST_DAY)
        assertEquals(4, adapter.itemCount)
        assertEquals(Collections.singleton(FIRST_DAY), PreferenceManager.getCollapsedPeriods())
        adapter.togglePeriod(FIRST_DAY)
        assertEquals(7, adapter.itemCount)
        assertTrue(PreferenceManager.getCollapsedPeriods().isEmpty())
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

    private fun adapter(listener: RecordsClicks): TransactionCursorAdapter {
        val adapter = TransactionCursorAdapter(listener, true)
        adapter.changeCursor(cursor())
        return adapter
    }

    @Test
    fun aTapOnARowUnderAFoldedHeaderOpensThatRow() {
        val listener = RecordsClicks()
        val adapter = adapter(listener)
        fold(adapter, FIRST_DAY)
        val recyclerView = listWith(adapter)
        childAt(recyclerView, 2).performClick()
        assertEquals(4L, listener.transactionId)
        childAt(recyclerView, 1).performClick()
        assertNotNull("the header click recorded no date", listener.headerStart)
        val headerStart = listener.headerStart ?: return
        val headerEnd = listener.headerEnd ?: return
        val expectedStart = DateUtils.getDateFromSQLDateTimeString("2019-03-14 00:00:00")
        val expectedEnd = DateUtils.getDateFromSQLDateTimeString("2019-03-14 23:59:59")
        assertNotNull(expectedStart)
        assertNotNull(expectedEnd)
        assertEquals(expectedStart?.time, headerStart.time)
        assertEquals(expectedEnd?.time, headerEnd.time)
    }

    @Test
    fun aCursorWithoutHeadersShowsEveryRowAsAnItem() {
        PreferenceManager.setCollapsedPeriods(HashSet(listOf(FIRST_DAY)))
        val adapter = TransactionCursorAdapter(RecordsNothing(), false)
        adapter.changeCursor(bareCursor())
        assertTypes(adapter, ITEM, ITEM, ITEM, ITEM, ITEM)
        assertEquals(1L, adapter.getItemId(0))
        assertEquals(5L, adapter.getItemId(4))
        adapter.reloadCollapsedPeriods()
        assertTypes(adapter, ITEM, ITEM, ITEM, ITEM, ITEM)
        assertEquals(1L, adapter.getItemId(0))
        assertEquals(5L, adapter.getItemId(4))
    }

    @Test
    fun theHeaderCursorKnowsWhichPositionsAreHeadersWithoutMoving() {
        val cursor = cursor()
        assertTrue(cursor.isHeaderAt(0))
        assertTrue(cursor.isHeaderAt(4))
        assertFalse(cursor.isHeaderAt(1))
        assertFalse(cursor.isHeaderAt(2))
        assertFalse(cursor.isHeaderAt(3))
        assertFalse(cursor.isHeaderAt(5))
        assertFalse(cursor.isHeaderAt(6))
        assertFalse(cursor.isHeaderAt(7))
        assertFalse(cursor.isHeaderAt(-1))
    }

    @Test
    fun aCursorTakenAwayLeavesNothingOnScreen() {
        val adapter = adapter()
        adapter.changeCursor(null)
        assertEquals(0, adapter.itemCount)
        val bare = TransactionCursorAdapter(RecordsNothing(), false)
        bare.changeCursor(bareCursor())
        bare.changeCursor(null)
        assertEquals(0, bare.itemCount)
    }

    private class RecordsNothing : TransactionCursorAdapter.ActionListener {
        override fun onHeaderClick(startDate: Date, endDate: Date) {}
        override fun onTransactionClick(id: Long) {}
        override fun onSelectionChanged(count: Int) {}
    }

    private class RecordsClicks : TransactionCursorAdapter.ActionListener {
        var transactionId: Long = -1L
        var headerStart: Date? = null
        var headerEnd: Date? = null

        override fun onHeaderClick(startDate: Date, endDate: Date) {
            headerStart = startDate
            headerEnd = endDate
        }

        override fun onTransactionClick(id: Long) {
            transactionId = id
        }

        override fun onSelectionChanged(count: Int) {}
    }

    companion object {
        private const val FIRST_DAY = "0:2019-03-15 00:00:00"
        private const val SECOND_DAY = "0:2019-03-14 00:00:00"
        private const val FIRST_DAY_AS_A_MONTH = "2:2019-03-15 00:00:00"

        private const val HEADER = TransactionHeaderCursor.TYPE_HEADER
        private const val ITEM = TransactionHeaderCursor.TYPE_ITEM

        private val COLUMNS = arrayOf(
            Contract.Transaction.ID,
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

        private val DATES = arrayOf(
            "2019-03-15 10:00:00",
            "2019-03-15 10:00:00",
            "2019-03-15 10:00:00",
            "2019-03-14 10:00:00",
            "2019-03-14 10:00:00"
        )

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
    }
}
