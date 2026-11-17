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

package com.oriondev.moneywallet.ui.fragment.dialog

import android.app.Dialog
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Event
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.activity.NewEditEventActivity
import com.oriondev.moneywallet.ui.adapter.recycler.EventSelectorCursorAdapter
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.DateUtils
import java.util.Date

/**
 * Created by andre on 19/03/2018.
 */
class EventPickerDialog : DialogFragment(), EventSelectorCursorAdapter.Controller, LoaderManager.LoaderCallbacks<Cursor> {

    private var mEvent: Event? = null
    private var mFilterDate: Date? = null

    private var mCallback: Callback? = null

    private var mRecyclerView: RecyclerView? = null
    private var mMessageTextView: TextView? = null

    private var mCursorAdapter: EventSelectorCursorAdapter? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val currentActivity = activity ?: return super.onCreateDialog(savedInstanceState)
        if (savedInstanceState != null) {
            mEvent = savedInstanceState.getParcelable(SS_SELECTED_EVENT)
            mFilterDate = savedInstanceState.getSerializable(SS_FILTER_DATE) as? Date
        }
        val view = layoutInflater.inflate(R.layout.dialog_advanced_list, null)
        val dialog = ThemedDialog.buildMaterialDialog(currentActivity)
            .setTitle(R.string.dialog_event_picker_title)
            .setPositiveButton(R.string.action_new) { _, _ ->
                startActivity(Intent(activity, NewEditEventActivity::class.java))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setView(view)
            .create()

        val adapter = EventSelectorCursorAdapter(this)
        mCursorAdapter = adapter
        mRecyclerView = view.findViewById(R.id.recycler_view)
        mMessageTextView = view.findViewById(R.id.message_text_view)
        mRecyclerView?.layoutManager = LinearLayoutManager(currentActivity)
        mRecyclerView?.adapter = adapter
        mMessageTextView?.setText(R.string.message_no_event_found)
        mRecyclerView?.visibility = View.GONE
        mMessageTextView?.visibility = View.GONE
        LoaderManager.getInstance(this).restartLoader(DEFAULT_LOADER_ID, null, this)
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(SS_SELECTED_EVENT, mEvent)
        outState.putSerializable(SS_FILTER_DATE, mFilterDate)
    }

    fun setCallback(callback: Callback?) {
        mCallback = callback
    }

    fun showPicker(fragmentManager: FragmentManager, tag: String?, event: Event?, filterDate: Date?) {
        mEvent = event
        mFilterDate = filterDate
        show(fragmentManager, tag)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        val uri = DataContentProvider.CONTENT_EVENTS
        val projection = arrayOf(
            Contract.Event.ID,
            Contract.Event.NAME,
            Contract.Event.ICON,
            Contract.Event.START_DATE,
            Contract.Event.END_DATE
        )
        var where: String? = null
        var whereArgs: Array<String>? = null
        val filterDate = mFilterDate
        if (filterDate != null) {
            where = "DATE(?) BETWEEN DATE(" + Contract.Event.START_DATE + ") AND (" +
                Contract.Event.END_DATE + ")"
            whereArgs = arrayOf(DateUtils.getSQLDateString(filterDate))
        }
        val sortOrder = Contract.Event.NAME
        return CursorLoader(requireContext(), uri, projection, where, whereArgs, sortOrder)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor?) {
        mCursorAdapter?.swapCursor(data)
        if (data != null && data.count > 0) {
            mRecyclerView?.visibility = View.VISIBLE
            mMessageTextView?.visibility = View.GONE
        } else {
            mRecyclerView?.visibility = View.GONE
            mMessageTextView?.visibility = View.VISIBLE
        }
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        mCursorAdapter?.swapCursor(null)
    }

    override fun onEventClick(event: Event) {
        mCallback?.onEventSelected(event)
        dismiss()
    }

    override fun isEventSelected(id: Long): Boolean {
        return mEvent?.id == id
    }

    interface Callback {
        fun onEventSelected(event: Event?)
    }

    companion object {
        private const val SS_SELECTED_EVENT = "EventPickerDialog::SavedState::SelectedEvent"
        private const val SS_FILTER_DATE = "EventPickerDialog::SavedState::FilterDate"

        private const val DEFAULT_LOADER_ID = 1

        @JvmStatic
        fun newInstance(): EventPickerDialog {
            return EventPickerDialog()
        }
    }
}
