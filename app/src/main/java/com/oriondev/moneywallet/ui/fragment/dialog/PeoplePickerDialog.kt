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
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.collection.LongSparseArray
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Person
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.activity.NewEditPersonActivity
import com.oriondev.moneywallet.ui.adapter.recycler.PeopleSelectorCursorAdapter
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog

/**
 * Created by andre on 20/03/2018.
 */
class PeoplePickerDialog : DialogFragment(), PeopleSelectorCursorAdapter.Controller, LoaderManager.LoaderCallbacks<Cursor> {

    private var mCallback: Callback? = null
    private var mSelectedPeople = LongSparseArray<Person>()

    private var mRecyclerView: RecyclerView? = null
    private var mMessageTextView: TextView? = null

    private var mCursorAdapter: PeopleSelectorCursorAdapter? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val currentActivity = activity ?: return super.onCreateDialog(savedInstanceState)
        if (savedInstanceState != null) {
            mSelectedPeople = LongSparseArray()
            val parcelables = savedInstanceState.getParcelableArray(SS_SELECTED_PEOPLE)
            if (parcelables != null) {
                for (parcelable in parcelables) {
                    if (parcelable is Person) {
                        mSelectedPeople.append(parcelable.id, parcelable)
                    }
                }
            }
        }
        val view = layoutInflater.inflate(R.layout.dialog_advanced_list, null)
        val dialog = ThemedDialog.buildMaterialDialog(currentActivity)
            .setTitle(R.string.dialog_people_picker_title)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                mCallback?.onPeopleSelected(selectedPeople())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.action_new) { _, _ ->
                startActivity(Intent(activity, NewEditPersonActivity::class.java))
            }
            .setView(view)
            .create()

        val adapter = PeopleSelectorCursorAdapter(this)
        mCursorAdapter = adapter
        mRecyclerView = view.findViewById(R.id.recycler_view)
        mMessageTextView = view.findViewById(R.id.message_text_view)
        mRecyclerView?.layoutManager = LinearLayoutManager(currentActivity)
        mRecyclerView?.adapter = adapter
        mMessageTextView?.setText(R.string.message_no_person_found)
        mRecyclerView?.visibility = View.GONE
        mMessageTextView?.visibility = View.GONE
        LoaderManager.getInstance(this).restartLoader(DEFAULT_LOADER_ID, null, this)
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArray(SS_SELECTED_PEOPLE, selectedPeople())
    }

    private fun selectedPeople(): Array<Person> {
        return Array(mSelectedPeople.size()) { i ->
            mSelectedPeople.valueAt(i)
        }
    }

    fun setCallback(callback: Callback?) {
        mCallback = callback
    }

    fun showPicker(fragmentManager: FragmentManager, tag: String?, people: Array<Person>?) {
        mSelectedPeople = LongSparseArray()
        if (people != null && people.isNotEmpty()) {
            for (person in people) {
                mSelectedPeople.append(person.id, person)
            }
        }
        show(fragmentManager, tag)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        val uri: Uri = DataContentProvider.CONTENT_PEOPLE
        val projection = arrayOf(
            Contract.Person.ID,
            Contract.Person.NAME,
            Contract.Person.ICON
        )
        val sortOrder = Contract.Person.NAME
        return CursorLoader(requireContext(), uri, projection, null, null, sortOrder)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        mCursorAdapter?.swapCursor(cursor)
        if (cursor != null && cursor.count > 0) {
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

    override fun onPersonSelected(person: Person) {
        if (mSelectedPeople.indexOfKey(person.id) >= 0) {
            mSelectedPeople.remove(person.id)
        } else {
            mSelectedPeople.append(person.id, person)
        }
        mCursorAdapter?.notifyDataSetChanged()
    }

    override fun isPersonSelected(id: Long): Boolean {
        return mSelectedPeople.indexOfKey(id) >= 0
    }

    interface Callback {
        fun onPeopleSelected(people: Array<Person>)
    }

    companion object {
        private const val SS_SELECTED_PEOPLE = "PeoplePickerDialog::SavedState::SelectedPeople"
        private const val DEFAULT_LOADER_ID = 1

        @JvmStatic
        fun newInstance(): PeoplePickerDialog {
            return PeoplePickerDialog()
        }
    }
}
