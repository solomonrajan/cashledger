/*
 * Copyright (c) 2018.
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

package com.oriondev.moneywallet.ui.fragment.multipanel

import android.content.BroadcastReceiver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.picker.CategoryPicker
import com.oriondev.moneywallet.picker.WalletPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.preference.CurrentWalletController
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter
import com.oriondev.moneywallet.ui.adapter.recycler.TransactionCursorAdapter
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelAppBarItemFragment
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment
import com.oriondev.moneywallet.ui.fragment.base.TransactionSelectionMode
import com.oriondev.moneywallet.ui.fragment.secondary.TransactionItemFragment
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView
import com.oriondev.moneywallet.ui.view.calendar.MonthView
import com.oriondev.moneywallet.ui.view.calendar.OnDateSelectedListener
import com.oriondev.moneywallet.ui.view.calendar.TimelineView
import com.oriondev.moneywallet.utils.DateUtils
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.HashSet

/**
 * Created by andrea on 06/04/18.
 */
class CalendarMultiPanelFragment : MultiPanelAppBarItemFragment(),
    MonthView.OnMonthSelectedListener,
    OnDateSelectedListener,
    TimelineView.OnMonthScrolledListener,
    SwipeRefreshLayout.OnRefreshListener,
    TransactionCursorAdapter.ActionListener,
    LoaderManager.LoaderCallbacks<Cursor>,
    CurrentWalletController,
    WalletPicker.SingleWalletController,
    CategoryPicker.Controller {

    private var currentWalletObserver: BroadcastReceiver? = null

    private var monthView: MonthView? = null
    private var timelineView: TimelineView? = null
    private var advancedRecyclerView: AdvancedRecyclerView? = null
    private var abstractCursorAdapter: AbstractCursorAdapter<*>? = null

    override fun onInflateRootLayout(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_multi_panel_appbar_without_scroll, container, false)
    }

    override fun onCreatePrimaryAppBar(inflater: LayoutInflater, primaryAppBar: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_calendar_app_bar_layout, primaryAppBar, true)
        val mv: MonthView = view.findViewById(R.id.month_view)
        monthView = mv
        mv.setFirstDate(1900, Calendar.JANUARY)
        mv.setOnMonthSelectedListener(this)
    }

    override fun onCreatePrimaryPanel(inflater: LayoutInflater, primaryPanel: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_calendar_primary_panel, primaryPanel, true)
        val tv: TimelineView = view.findViewById(R.id.timeline_view)
        val rv: AdvancedRecyclerView = view.findViewById(R.id.advanced_recycler_view)
        timelineView = tv
        advancedRecyclerView = rv
        tv.setFirstDate(1900, Calendar.JANUARY, 1)
        tv.setLastDate(2100, Calendar.DECEMBER, 31)
        val adapter = TransactionCursorAdapter(this)
        selectionMode = TransactionSelectionMode(this, this, adapter)
        abstractCursorAdapter = adapter
        rv.layoutManager = LinearLayoutManager(activity)
        rv.setEmptyText(R.string.message_no_transaction_found)
        rv.adapter = adapter
        rv.setOnRefreshListener(this)

        val calendar = Calendar.getInstance()
        var year = calendar.get(Calendar.YEAR)
        var month = calendar.get(Calendar.MONTH)
        var day = calendar.get(Calendar.DAY_OF_MONTH)
        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_SELECTED_YEAR)) {
            year = savedInstanceState.getInt(STATE_SELECTED_YEAR)
            month = savedInstanceState.getInt(STATE_SELECTED_MONTH)
            day = savedInstanceState.getInt(STATE_SELECTED_DAY)
        }

        tv.setSelectedDate(year, month, day)
        tv.setOnDateSelectedListener(this)
        tv.setOnMonthScrolledListener(this)
        onDateSelected(year, month, day, tv.getSelectedPosition())
        LoaderManager.getInstance(this).initLoader(MARKED_DAYS_LOADER_ID, null, markedDaysCallbacks)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        timelineView?.let { tv ->
            outState.putInt(STATE_SELECTED_YEAR, tv.getSelectedYear())
            outState.putInt(STATE_SELECTED_MONTH, tv.getSelectedMonth())
            outState.putInt(STATE_SELECTED_DAY, tv.getSelectedDay())
        }
        selectionMode?.onSaveInstanceState(outState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectionMode?.onRestoreInstanceState(savedInstanceState)
    }

    override fun onCreateSecondaryPanel(): SecondaryPanelFragment = TransactionItemFragment()

    override fun getSecondaryFragmentTag(): String = SECONDARY_PANEL_FRAGMENT_TAG

    override fun getTitleRes(): Int = R.string.title_activity_calendar

    override fun showsCurrentWallet(): Boolean = true

    override fun isFloatingActionButtonEnabled(): Boolean = false

    override fun onMonthSelected(year: Int, month: Int, index: Int) {
        timelineView?.setSelectedDate(year, month, 1)
    }

    override fun onDateSelected(year: Int, month: Int, day: Int, index: Int) {
        monthView?.setSelectedMonth(year, month, false, true)
        loadTransactions(year, month, day)
        advancedRecyclerView?.setState(AdvancedRecyclerView.State.LOADING)
    }

    override fun onMonthScrolled(year: Int, month: Int) {
        val mv = monthView ?: return
        if (year == mv.getSelectedYear() && month == mv.getSelectedMonth()) {
            return
        }
        mv.setSelectedMonth(year, month, false, true)
    }

    override fun onHeaderClick(startDate: Date, endDate: Date) {
        // never used here
    }

    override fun onTransactionClick(id: Long) {
        showItemId(id)
        showSecondaryPanel()
    }

    override fun onSelectionChanged(count: Int) {
        selectionMode?.onSelectionChanged(count)
    }

    override fun onWalletChanged(tag: String?, wallet: Wallet?) {
        selectionMode?.onWalletPicked(wallet)
    }

    override fun onCategoryChanged(tag: String?, category: Category?) {
        selectionMode?.onCategoryPicked(category)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor>? {
        val act = activity
        if (act != null && args != null) {
            val date = DateUtils.getDate(
                args.getInt(ARG_SELECTED_YEAR),
                args.getInt(ARG_SELECTED_MONTH),
                args.getInt(ARG_SELECTED_DAY)
            )
            val uri: Uri = DataContentProvider.CONTENT_TRANSACTIONS
            val selection = "${walletSelection()} AND DATE(${Contract.Transaction.DATE}) == DATE('${DateUtils.getSQLDateString(date)}')"
            val sortOrder = "${Contract.Transaction.DATE} DESC"
            return CursorLoader(act, uri, null, selection, null, sortOrder)
        }
        return null
    }

    private val markedDaysCallbacks = object : LoaderManager.LoaderCallbacks<Cursor> {
        override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
            return CursorLoader(
                requireContext(),
                DataContentProvider.CONTENT_TRANSACTIONS,
                MARKED_DAYS_PROJECTION,
                walletSelection(),
                null,
                null
            )
        }

        override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
            timelineView?.setMarkedDays(readDays(cursor))
        }

        override fun onLoaderReset(loader: Loader<Cursor>) {
            timelineView?.setMarkedDays(Collections.emptySet())
        }
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        abstractCursorAdapter?.changeCursor(cursor)
        if (cursor != null && cursor.count > 0) {
            advancedRecyclerView?.setState(AdvancedRecyclerView.State.READY)
        } else {
            advancedRecyclerView?.setState(AdvancedRecyclerView.State.EMPTY)
        }
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        abstractCursorAdapter?.changeCursor(null)
    }

    override fun onRefresh() {
        val tv = timelineView
        if (tv != null) {
            loadTransactions(tv.getSelectedYear(), tv.getSelectedMonth(), tv.getSelectedDay())
        }
        loadMarkedDays()
        advancedRecyclerView?.setState(AdvancedRecyclerView.State.REFRESHING)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        currentWalletObserver = PreferenceManager.registerCurrentWalletObserver(context, this)
    }

    override fun onDetach() {
        currentWalletObserver?.let { PreferenceManager.unregisterCurrentWalletObserver(activity, it) }
        super.onDetach()
    }

    override fun onCurrentWalletChanged(walletId: Long) {
        val tv = timelineView ?: return
        selectionMode?.finish()
        loadTransactions(tv.getSelectedYear(), tv.getSelectedMonth(), tv.getSelectedDay())
        loadMarkedDays()
        advancedRecyclerView?.setState(AdvancedRecyclerView.State.LOADING)
    }

    private fun loadTransactions(year: Int, month: Int, day: Int) {
        val arguments = Bundle().apply {
            putInt(ARG_SELECTED_YEAR, year)
            putInt(ARG_SELECTED_MONTH, month)
            putInt(ARG_SELECTED_DAY, day)
        }
        LoaderManager.getInstance(this).restartLoader(DEFAULT_LOADER_ID, arguments, this)
    }

    private fun loadMarkedDays() {
        LoaderManager.getInstance(this).restartLoader(MARKED_DAYS_LOADER_ID, null, markedDaysCallbacks)
    }

    companion object {
        private const val SECONDARY_PANEL_FRAGMENT_TAG = "CalendarMultiPanelFragment::Tag::TransactionItemFragment"

        private const val ARG_SELECTED_YEAR = "CalendarMultiPanelFragment::Arguments::Year"
        private const val ARG_SELECTED_MONTH = "CalendarMultiPanelFragment::Arguments::Month"
        private const val ARG_SELECTED_DAY = "CalendarMultiPanelFragment::Arguments::Day"

        private const val STATE_SELECTED_YEAR = "CalendarMultiPanelFragment::State::Year"
        private const val STATE_SELECTED_MONTH = "CalendarMultiPanelFragment::State::Month"
        private const val STATE_SELECTED_DAY = "CalendarMultiPanelFragment::State::Day"

        private const val DEFAULT_LOADER_ID = 4834
        private const val MARKED_DAYS_LOADER_ID = 4835

        private const val COLUMN_DAY = "day"

        const val NO_DAY: Int = -1

        private val MARKED_DAYS_PROJECTION = arrayOf(
            "DISTINCT DATE(${Contract.Transaction.DATE}) AS $COLUMN_DAY"
        )

        @JvmStatic
        fun dayKey(date: String?): Int {
            if (date == null || date.length != 10) {
                return NO_DAY
            }
            return TimelineView.dayKey(
                date.substring(0, 4).toInt(),
                date.substring(5, 7).toInt() - 1,
                date.substring(8, 10).toInt()
            )
        }

        private fun walletSelection(): String {
            val currentWallet = PreferenceManager.getCurrentWallet()
            if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
                return "${Contract.Transaction.WALLET_COUNT_IN_TOTAL} = 1"
            }
            return "${Contract.Transaction.WALLET_ID} = $currentWallet"
        }

        private fun readDays(cursor: Cursor?): Set<Int> {
            if (cursor == null) {
                return Collections.emptySet()
            }
            val days: MutableSet<Int> = HashSet(cursor.count)
            val index = cursor.getColumnIndex(COLUMN_DAY)
            cursor.moveToPosition(-1)
            while (cursor.moveToNext()) {
                val day = dayKey(cursor.getString(index))
                if (day != NO_DAY) {
                    days.add(day)
                }
            }
            return days
        }
    }
}
