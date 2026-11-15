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
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.adapter.recycler.ParentCategorySelectorCursorAdapter
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog

/**
 * Picks any number of categories at once, for a budget that covers more than one.
 *
 * The list holds to one direction. With nothing chosen it offers every income and expense
 * category; from the first choice on it offers only the ones of that direction, so a budget
 * mixing the two cannot be built at all instead of being refused once it is saved. Clearing the
 * last choice opens it back up. System categories are never offered, which is what the single
 * picker does by default too.
 */
class MultiCategoryPickerDialog : DialogFragment(), ParentCategorySelectorCursorAdapter.Controller, LoaderManager.LoaderCallbacks<Cursor> {

    private var mCallback: Callback? = null
    private var mSelectedCategories = LongSparseArray<Category>()

    private var mRecyclerView: RecyclerView? = null
    private var mMessageTextView: TextView? = null

    private var mCursorAdapter: ParentCategorySelectorCursorAdapter? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val currentActivity = activity ?: return super.onCreateDialog(savedInstanceState)
        if (savedInstanceState != null) {
            mSelectedCategories = LongSparseArray()
            val parcelables = savedInstanceState.getParcelableArray(SS_SELECTED_CATEGORIES)
            if (parcelables != null) {
                for (parcelable in parcelables) {
                    if (parcelable is Category) {
                        mSelectedCategories.append(parcelable.id, parcelable)
                    }
                }
            }
        }
        val view = layoutInflater.inflate(R.layout.dialog_advanced_list, null)
        val dialog = ThemedDialog.buildMaterialDialog(currentActivity)
            .setTitle(R.string.dialog_category_picker_title)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                mCallback?.onCategoriesSelected(selectedCategories())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setView(view)
            .create()

        val adapter = ParentCategorySelectorCursorAdapter(this)
        mCursorAdapter = adapter
        mRecyclerView = view.findViewById(R.id.recycler_view)
        mMessageTextView = view.findViewById(R.id.message_text_view)
        mRecyclerView?.layoutManager = LinearLayoutManager(currentActivity)
        mRecyclerView?.adapter = adapter
        mMessageTextView?.setText(R.string.message_no_category_found)
        mRecyclerView?.visibility = View.GONE
        mMessageTextView?.visibility = View.GONE
        LoaderManager.getInstance(this).restartLoader(DEFAULT_LOADER_ID, null, this)
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArray(SS_SELECTED_CATEGORIES, selectedCategories())
    }

    private fun selectedCategories(): Array<Category> {
        return Array(mSelectedCategories.size()) { i ->
            mSelectedCategories.valueAt(i)
        }
    }

    private fun selectedType(): Contract.CategoryType? {
        return if (mSelectedCategories.size() > 0) mSelectedCategories.valueAt(0).type else null
    }

    fun setCallback(callback: Callback?) {
        mCallback = callback
    }

    fun showPicker(fragmentManager: FragmentManager, tag: String?, categories: Array<Category>?) {
        mSelectedCategories = LongSparseArray()
        if (categories != null) {
            for (category in categories) {
                mSelectedCategories.append(category.id, category)
            }
        }
        show(fragmentManager, tag)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        val uri: Uri = DataContentProvider.CONTENT_CATEGORIES
        val projection = arrayOf(
            Contract.Category.ID,
            Contract.Category.NAME,
            Contract.Category.ICON,
            Contract.Category.TYPE
        )
        val type = selectedType()
        val selection: String
        val selectionArgs: Array<String>
        if (type != null) {
            selection = Contract.Category.TYPE + " = ?"
            selectionArgs = arrayOf(type.value.toString())
        } else {
            selection = Contract.Category.TYPE + " != ?"
            selectionArgs = arrayOf(Contract.CategoryType.SYSTEM.value.toString())
        }
        val sortOrder = Contract.Category.TYPE + " ASC, " + Contract.Category.GROUP_INDEX +
            " ASC, " + Contract.Category.GROUP_NAME + " ASC, " + Contract.Category.GROUP_ID +
            " ASC, " + Contract.Category.PARENT + " IS NULL DESC, " + Contract.Category.NAME + " ASC"
        return CursorLoader(requireContext(), uri, projection, selection, selectionArgs, sortOrder)
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

    override fun onCategorySelected(category: Category) {
        val before = selectedType()
        if (before != null && category.type != before &&
            mSelectedCategories.indexOfKey(category.id) < 0
        ) {
            return
        }
        if (mSelectedCategories.indexOfKey(category.id) >= 0) {
            mSelectedCategories.remove(category.id)
        } else {
            mSelectedCategories.append(category.id, category)
        }
        if (before != selectedType()) {
            LoaderManager.getInstance(this).restartLoader(DEFAULT_LOADER_ID, null, this)
        } else {
            mCursorAdapter?.notifyDataSetChanged()
        }
    }

    override fun isCategorySelected(id: Long): Boolean {
        return mSelectedCategories.indexOfKey(id) >= 0
    }

    interface Callback {
        fun onCategoriesSelected(categories: Array<Category>?)
    }

    companion object {
        private const val SS_SELECTED_CATEGORIES = "MultiCategoryPickerDialog::SavedState::SelectedCategories"
        private const val DEFAULT_LOADER_ID = 1

        @JvmStatic
        fun newInstance(): MultiCategoryPickerDialog {
            return MultiCategoryPickerDialog()
        }
    }
}
