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

package com.oriondev.moneywallet.ui.fragment.primary

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.broadcast.Message
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter
import com.oriondev.moneywallet.ui.adapter.recycler.CategoryCursorAdapter
import com.oriondev.moneywallet.ui.fragment.base.CursorListFragment
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView

/**
 * Created by andrea on 11/02/18.
 */
class CategoryListFragment : CursorListFragment(), CategoryCursorAdapter.CategoryActionListener {

    private var mController: Controller? = null
    private var mAdapter: CategoryCursorAdapter? = null

    override fun onPrepareRecyclerView(recyclerView: AdvancedRecyclerView) {
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.setEmptyText(R.string.message_no_category_found)
    }

    override fun onCreateAdapter(): AbstractCursorAdapter {
        val adapter = CategoryCursorAdapter(this)
        mAdapter = adapter
        // After a rotation the activity has its query back before this view is built, so the
        // adapter is asked for it here instead of waiting to be told.
        adapter.setQuery(mController?.getCategoryQuery() ?: "")
        return adapter
    }

    fun setQuery(query: String?) {
        val adapter = mAdapter
        if (adapter != null) {
            adapter.setQuery(query)
            showListState()
        }
    }

    /**
     * Which categories have their children hidden is stored once for the whole application, and
     * this list is not the only one reading it: the picker opens over the category screen and
     * both draw the same categories. Asking again here is what stops the one underneath drawing
     * a state the other has already changed, which would make its next arrow tap do nothing. The
     * rows themselves need no reloading, since the loader is watching the categories already.
     */
    override fun onResume() {
        super.onResume()
        mAdapter?.reloadCollapsedCategories()
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor>? {
        val act = activity ?: return null
        val arguments = arguments ?: return null

        // unpack the argument bundle
        val type = arguments.getSerializable(ARG_CATEGORY_TYPE) as? Contract.CategoryType
            ?: throw IllegalArgumentException("Unknown category type for this fragment")
        val showChildren = arguments.getBoolean(ARG_SHOW_CHILDREN, true)
        // query the content provider
        val uri: Uri = DataContentProvider.CONTENT_CATEGORIES
        val projection = arrayOf(
            Contract.Category.ID,
            Contract.Category.ICON,
            Contract.Category.NAME,
            Contract.Category.PARENT
        )
        var selection = "${Contract.Category.TYPE} = ?"
        if (!showChildren) {
            selection += " AND ${Contract.Category.PARENT} IS NULL"
        }
        val selectionArgs = arrayOf(type.value.toString())
        val sortOrder = "${Contract.Category.GROUP_INDEX} ASC, ${Contract.Category.GROUP_NAME} ASC, " +
            "${Contract.Category.GROUP_ID} ASC, ${Contract.Category.PARENT} IS NULL DESC, ${Contract.Category.NAME} ASC"
        return CursorLoader(act, uri, projection, selection, selectionArgs, sortOrder)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Controller) {
            mController = context
        }
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
    }

    override fun onCategoryClick(id: Long) {
        val controller = mController
        if (controller != null) {
            controller.onCategoryClick(id)
        } else {
            val act = activity ?: return
            val intent = Intent(LocalAction.ACTION_ITEM_CLICK).apply {
                putExtra(Message.ITEM_ID, id)
                putExtra(Message.ITEM_TYPE, Message.TYPE_CATEGORY)
            }
            LocalBroadcastManager.getInstance(act).sendBroadcast(intent)
        }
    }

    interface Controller {
        fun onCategoryClick(id: Long)
        fun getCategoryQuery(): String?
    }

    companion object {
        private const val ARG_CATEGORY_TYPE = "CategoryListFragment::Arguments::CategoryType"
        private const val ARG_SHOW_CHILDREN = "CategoryListFragment::Arguments::ShowChildren"

        @JvmStatic
        fun newInstance(type: Contract.CategoryType, showChildren: Boolean): CategoryListFragment {
            val arguments = Bundle().apply {
                putSerializable(ARG_CATEGORY_TYPE, type)
                putBoolean(ARG_SHOW_CHILDREN, showChildren)
            }
            val fragment = CategoryListFragment()
            fragment.arguments = arguments
            return fragment
        }
    }
}
