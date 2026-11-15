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
import com.oriondev.moneywallet.ui.activity.NewEditCategoryActivity
import com.oriondev.moneywallet.ui.adapter.recycler.ParentCategorySelectorCursorAdapter
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog

/**
 * Created by andre on 19/03/2018.
 */
class ParentCategoryPickerDialog : DialogFragment(), ParentCategorySelectorCursorAdapter.Controller, LoaderManager.LoaderCallbacks<Cursor> {

    private var mCategory: Category? = null
    private var mCallerId: Long = 0
    private var mCategoryType: Contract.CategoryType? = null

    private var mCallback: Callback? = null

    private var mRecyclerView: RecyclerView? = null
    private var mMessageTextView: TextView? = null

    private var mCursorAdapter: ParentCategorySelectorCursorAdapter? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val currentActivity = activity ?: return super.onCreateDialog(savedInstanceState)
        if (savedInstanceState != null) {
            mCategory = savedInstanceState.getParcelable(SS_SELECTED_CATEGORY)
            mCallerId = savedInstanceState.getLong(SS_CALLER_ID)
            mCategoryType = savedInstanceState.getSerializable(SS_CATEGORY_TYPE) as? Contract.CategoryType
        }
        val view = layoutInflater.inflate(R.layout.dialog_advanced_list, null)
        val dialog = ThemedDialog.buildMaterialDialog(currentActivity)
            .setTitle(R.string.dialog_category_picker_title)
            .setPositiveButton(R.string.action_new) { _, _ ->
                startActivity(Intent(activity, NewEditCategoryActivity::class.java))
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
        outState.putParcelable(SS_SELECTED_CATEGORY, mCategory)
        outState.putLong(SS_CALLER_ID, mCallerId)
        outState.putSerializable(SS_CATEGORY_TYPE, mCategoryType)
    }

    fun setCallback(callback: Callback?) {
        mCallback = callback
    }

    fun showPicker(
        fragmentManager: FragmentManager,
        tag: String?,
        category: Category?,
        callerId: Long,
        type: Contract.CategoryType?
    ) {
        mCategory = category
        mCallerId = callerId
        mCategoryType = type
        show(fragmentManager, tag)
    }

    override fun onCategorySelected(category: Category) {
        mCallback?.onCategorySelected(category)
        dismiss()
    }

    override fun isCategorySelected(id: Long): Boolean {
        return mCategory?.id == id
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        val uri: Uri = DataContentProvider.CONTENT_CATEGORIES
        val projection = arrayOf(
            Contract.Category.ID,
            Contract.Category.NAME,
            Contract.Category.ICON,
            Contract.Category.TYPE
        )
        val where = Contract.Category.TYPE + " = ? AND " + Contract.Category.PARENT +
            " IS NULL AND " + Contract.Category.ID + " != ?"
        val typeVal = mCategoryType?.value?.toString() ?: "0"
        val whereArgs = arrayOf(typeVal, mCallerId.toString())
        val sortOrder = Contract.Category.NAME
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

    interface Callback {
        fun onCategorySelected(category: Category?)
    }

    companion object {
        private const val SS_SELECTED_CATEGORY = "ParentCategoryPickerDialog::SavedState::SelectedCategory"
        private const val SS_CALLER_ID = "ParentCategoryPickerDialog::SavedState::CallerId"
        private const val SS_CATEGORY_TYPE = "ParentCategoryPickerDialog::SavedState::CategoryType"

        private const val DEFAULT_LOADER_ID = 1

        @JvmStatic
        fun newInstance(): ParentCategoryPickerDialog {
            return ParentCategoryPickerDialog()
        }
    }
}
