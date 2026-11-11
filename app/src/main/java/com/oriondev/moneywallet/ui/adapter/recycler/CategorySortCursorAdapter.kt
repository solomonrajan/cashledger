/*
 * Copyright (c) 2018.
 * Copyright (c) 2026 solomonrajan.
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

import android.annotation.SuppressLint
import android.database.Cursor
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.utils.IconLoader
import java.util.Collections

/**
 * Shared base adapter for drag-and-drop sortable cursor lists.
 */
abstract class AbstractSortCursorAdapter<VH : RecyclerView.ViewHolder>(
    cursor: Cursor?,
    idColumn: String
) : AbstractCursorAdapter<VH>(cursor, idColumn) {

    private val mSortedIndices: MutableList<Int> = ArrayList()

    override fun onBindViewHolder(holder: VH, position: Int) {
        if (position in mSortedIndices.indices) {
            super.onBindViewHolder(holder, mSortedIndices[position])
        }
    }

    override fun getItemId(position: Int): Long {
        return if (position in mSortedIndices.indices) {
            super.getItemId(mSortedIndices[position])
        } else {
            0L
        }
    }

    override fun swapCursor(newCursor: Cursor?): Cursor? {
        val oldCursor = super.swapCursor(newCursor)
        mSortedIndices.clear()
        if (newCursor != null) {
            for (i in 0 until newCursor.count) {
                mSortedIndices.add(i)
            }
        }
        return oldCursor
    }

    fun moveItem(from: Int, to: Int): Boolean {
        if (minOf(from, to) >= 0 && maxOf(from, to) < mSortedIndices.size) {
            Collections.swap(mSortedIndices, from, to)
            notifyItemMoved(from, to)
            return true
        }
        return false
    }

    protected fun getSortedIds(idColumnIndex: Int): List<Long> {
        val ids: MutableList<Long> = ArrayList()
        for (cursorPos in mSortedIndices) {
            val cursor = getSafeCursor(cursorPos)
            if (cursor != null) {
                ids.add(cursor.getLong(idColumnIndex))
            }
        }
        return ids
    }

    @SuppressLint("ClickableViewAccessibility")
    protected fun attachDragHandle(actionView: View, onDragStarted: () -> Unit) {
        actionView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                onDragStarted()
            }
            false
        }
    }
}

/**
 * Created by andrea on 12/02/18.
 */
class CategorySortCursorAdapter(
    private val mListener: CategorySortListener?
) : AbstractSortCursorAdapter<CategorySortCursorAdapter.CategoryViewHolder>(null, Contract.Category.ID) {

    private var mIndexCategoryId = -1
    private var mIndexCategoryIcon = -1
    private var mIndexCategoryName = -1

    override fun onLoadColumnIndices(cursor: Cursor) {
        mIndexCategoryId = cursor.getColumnIndex(Contract.Category.ID)
        mIndexCategoryIcon = cursor.getColumnIndex(Contract.Category.ICON)
        mIndexCategoryName = cursor.getColumnIndex(Contract.Category.NAME)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, cursor: Cursor) {
        val icon = IconLoader.parse(cursor.getString(mIndexCategoryIcon))
        IconLoader.loadInto(icon, holder.mIconImageView)
        holder.mNameTextView.text = cursor.getString(mIndexCategoryName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.adapter_category_sort_item, parent, false)
        return CategoryViewHolder(itemView)
    }

    fun getSortedCategoryIds(): List<Long> = getSortedIds(mIndexCategoryId)

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        internal val mIconImageView: ImageView = itemView.findViewById(R.id.icon_image_view)
        internal val mNameTextView: TextView = itemView.findViewById(R.id.name_text_view)
        private val mActionImageView: ImageView = itemView.findViewById(R.id.action_image_view)

        init {
            attachDragHandle(mActionImageView) {
                mListener?.onCategoryDragStarted(this)
            }
        }
    }

    interface CategorySortListener {

        fun onCategoryDragStarted(viewHolder: RecyclerView.ViewHolder)
    }
}
