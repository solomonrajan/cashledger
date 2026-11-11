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

import android.database.Cursor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.view.CategoryChildIndicator
import com.oriondev.moneywallet.utils.IconLoader
import java.util.Locale

/**
 * Created by andrea on 12/02/18.
 */
class CategoryCursorAdapter(
    private val mListener: CategoryActionListener?
) : AbstractCursorAdapter<CategoryCursorAdapter.CategoryViewHolder>(null, Contract.Category.ID) {

    private var mIndexCategoryId = -1
    private var mIndexCategoryIcon = -1
    private var mIndexCategoryName = -1
    private var mIndexCategoryParentId = -1

    /**
     * Cursor row of each row on screen, in order. Hiding a category's children leaves their
     * cursor rows out of this list, so every position this adapter is asked about is read
     * through it.
     */
    private val mVisibleRows: MutableList<Int> = ArrayList()

    /**
     * Ids of the categories whose children are hidden, as of the last rebuild. Read from the
     * preference every time rather than kept, because the pager holds one adapter per tab and
     * the picker opens over the category screen, so several of these are alive at once over one
     * stored set. An adapter that trusted its own copy would write the others' hiding away.
     */
    private val mCollapsedCategories: MutableSet<Long> = HashSet()

    /**
     * Ids of the categories that have at least one child in the current cursor. Only these get
     * an arrow drawn, which is what says a category has children at all.
     */
    private val mCategoriesWithChildren: MutableSet<Long> = HashSet()

    private var mQuery: String = ""

    override fun onLoadColumnIndices(cursor: Cursor) {
        mIndexCategoryId = cursor.getColumnIndex(Contract.Category.ID)
        mIndexCategoryIcon = cursor.getColumnIndex(Contract.Category.ICON)
        mIndexCategoryName = cursor.getColumnIndex(Contract.Category.NAME)
        mIndexCategoryParentId = cursor.getColumnIndex(Contract.Category.PARENT)
        // The superclass calls this from swapCursor once the cursor is in place and before it
        // tells the list anything changed, after construction has already initialized the collections
        // used by rebuildVisibleRows.
        rebuildVisibleRows()
    }

    fun setQuery(query: String?) {
        mQuery = query?.lowercase(Locale.getDefault()) ?: ""
        rebuildVisibleRows()
        notifyDataSetChanged()
    }

    private fun matches(name: String?): Boolean {
        return name != null && name.lowercase(Locale.getDefault()).contains(mQuery)
    }

    private fun rebuildVisibleRows() {
        mVisibleRows.clear()
        mCategoriesWithChildren.clear()
        mCollapsedCategories.clear()
        for (categoryId in PreferenceManager.getCollapsedCategories()) {
            val id = categoryId.toLongOrNull()
            if (id != null) {
                mCollapsedCategories.add(id)
            }
        }
        val cursor = getCursor() ?: return
        // The filter keeps a category whose own name matches and a category that has a matching
        // child, and under a matching category it keeps all of its children. A child the user
        // has hidden is shown again while a filter is on.
        val parentsOfMatchingChildren: MutableSet<Long> = HashSet()
        if (mQuery.isNotEmpty()) {
            for (position in 0 until cursor.count) {
                cursor.moveToPosition(position)
                if (!cursor.isNull(mIndexCategoryParentId) && matches(cursor.getString(mIndexCategoryName))) {
                    parentsOfMatchingChildren.add(cursor.getLong(mIndexCategoryParentId))
                }
            }
        }
        val matchingParents: MutableSet<Long> = HashSet()
        val categoriesAbove: MutableSet<Long> = HashSet()
        for (position in 0 until cursor.count) {
            cursor.moveToPosition(position)
            if (cursor.isNull(mIndexCategoryParentId)) {
                val categoryId = cursor.getLong(mIndexCategoryId)
                categoriesAbove.add(categoryId)
                val nameMatches = matches(cursor.getString(mIndexCategoryName))
                if (nameMatches) {
                    matchingParents.add(categoryId)
                }
                if (mQuery.isEmpty() || nameMatches || categoryId in parentsOfMatchingChildren) {
                    mVisibleRows.add(position)
                }
                continue
            }
            val parentId = cursor.getLong(mIndexCategoryParentId)
            mCategoriesWithChildren.add(parentId)
            if (mQuery.isEmpty()) {
                // A child whose category is not above it in this cursor has no arrow that could
                // bring it back, so it is shown rather than hidden. Two things leave one: the
                // query drops a category marked deleted out of its join while keeping the
                // children that name it, and SyncContentProvider inserts rows straight onto the
                // table, past the check that refuses a child of a category of another type.
                if (parentId !in mCollapsedCategories || parentId !in categoriesAbove) {
                    mVisibleRows.add(position)
                }
            } else if (parentId in matchingParents || matches(cursor.getString(mIndexCategoryName))) {
                mVisibleRows.add(position)
            }
        }
    }

    /**
     * Draws the rows again against what is stored now. The list this adapter is in is not the
     * only one reading that store, so a list coming back to the front has to ask again rather
     * than draw what it last built.
     */
    fun reloadCollapsedCategories() {
        rebuildVisibleRows()
        notifyDataSetChanged()
    }

    private fun toggleChildren(categoryId: Long) {
        // Read, change, write, so that the tabs and screens this adapter shares the stored set
        // with keep their own hiding.
        val stored: MutableSet<String> = HashSet(PreferenceManager.getCollapsedCategories())
        if (!stored.remove(categoryId.toString())) {
            stored.add(categoryId.toString())
        }
        PreferenceManager.setCollapsedCategories(stored)
        rebuildVisibleRows()
        notifyDataSetChanged()
    }

    /**
     * The cursor row an on screen position stands for, or -1 when there is none. Everything that
     * takes an on screen position goes through this, and so does every read of the cursor from a
     * click.
     */
    private fun cursorPosition(position: Int): Int {
        return if (position in mVisibleRows.indices) mVisibleRows[position] else -1
    }

    override fun getItemCount(): Int {
        // Guarded rather than returned outright: a cursor swapped away for null leaves the rows
        // built for it behind, since the superclass only walks a cursor it actually has.
        return if (isDataValid()) mVisibleRows.size else 0
    }

    override fun getItemId(position: Int): Long {
        return super.getItemId(cursorPosition(position))
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        super.onBindViewHolder(holder, cursorPosition(position))
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, cursor: Cursor) {
        val icon = IconLoader.parse(cursor.getString(mIndexCategoryIcon))
        IconLoader.loadInto(icon, holder.mIconImageView)
        val name = cursor.getString(mIndexCategoryName)
        holder.mNameTextView.text = name
        if (holder.itemViewType == TYPE_CHILD) {
            holder.mChildIndicator?.setLast(isLastChild(cursor.position))
        } else {
            bindChildrenToggle(holder, cursor.getLong(mIndexCategoryId), name)
        }
    }

    private fun bindChildrenToggle(holder: CategoryViewHolder, categoryId: Long, name: String?) {
        // While a filter is on, every child that matches is already shown, so the arrow would
        // have nothing to do.
        val toggle = holder.mChildrenToggle ?: return
        val hasChildren = categoryId in mCategoriesWithChildren && mQuery.isEmpty()
        toggle.visibility = if (hasChildren) View.VISIBLE else View.GONE
        if (hasChildren) {
            val collapsed = categoryId in mCollapsedCategories
            toggle.rotation = if (collapsed) 0f else 180f
            // Named, because a screen reader reaches the arrow on its own and every one of them
            // would otherwise read the same words.
            toggle.contentDescription = holder.itemView.context.getString(
                if (collapsed) R.string.description_show_subcategories
                else R.string.description_hide_subcategories,
                name
            )
        }
    }

    /**
     * Whether the child at this cursor row is the last one drawn under its category, which is
     * what tells the indicator to stop its line half way. It reads the next row on screen and
     * not the next row in the cursor, because a filter can hide a sibling that follows in the
     * cursor.
     */
    private fun isLastChild(cursorPosition: Int): Boolean {
        val cursor = getCursor() ?: return true
        val index = mVisibleRows.binarySearch(cursorPosition)
        if (index >= 0 && index + 1 < mVisibleRows.size) {
            cursor.moveToPosition(mVisibleRows[index + 1])
            val nextIsChild = !cursor.isNull(mIndexCategoryParentId)
            cursor.moveToPosition(cursorPosition)
            return !nextIsChild
        }
        return true
    }

    override fun getItemViewType(position: Int): Int {
        val cursor = getSafeCursor(cursorPosition(position))
        return if (cursor == null || cursor.isNull(mIndexCategoryParentId)) TYPE_PARENT else TYPE_CHILD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val layoutRes = if (viewType == TYPE_PARENT) {
            R.layout.adapter_category_item
        } else {
            R.layout.adapter_sub_category_item
        }
        return CategoryViewHolder(inflater.inflate(layoutRes, parent, false))
    }

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        internal val mChildIndicator: CategoryChildIndicator? = itemView.findViewById(R.id.category_child_indicator)
        internal val mIconImageView: ImageView = itemView.findViewById(R.id.icon_image_view)
        internal val mChildrenToggle: ImageView? = itemView.findViewById(R.id.children_toggle_image_view)
        internal val mNameTextView: TextView = itemView.findViewById(R.id.name_text_view)

        init {
            itemView.setOnClickListener(this)
            // Its own listener, because a tap on the row still means what it always did:
            // pick this category, or open it for editing.
            mChildrenToggle?.setOnClickListener {
                @Suppress("DEPRECATION")
                val cursor = getSafeCursor(cursorPosition(adapterPosition))
                if (cursor != null) {
                    toggleChildren(cursor.getLong(mIndexCategoryId))
                }
            }
        }

        override fun onClick(v: View) {
            val listener = mListener ?: return
            @Suppress("DEPRECATION")
            val cursor = getSafeCursor(cursorPosition(adapterPosition))
            if (cursor != null) {
                listener.onCategoryClick(cursor.getLong(mIndexCategoryId))
            }
        }
    }

    interface CategoryActionListener {

        fun onCategoryClick(id: Long)
    }

    companion object {
        private const val TYPE_PARENT = 0
        private const val TYPE_CHILD = 1
    }
}
