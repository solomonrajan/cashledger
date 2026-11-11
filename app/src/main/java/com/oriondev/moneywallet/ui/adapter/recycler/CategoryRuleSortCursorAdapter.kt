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

class CategoryRuleSortCursorAdapter(
    private val mListener: CategoryRuleSortListener?
) : AbstractSortCursorAdapter<CategoryRuleSortCursorAdapter.RuleViewHolder>(null, Contract.CategoryRule.ID) {

    private var mIndexRuleId = -1
    private var mIndexPattern = -1
    private var mIndexCategoryName = -1

    override fun onLoadColumnIndices(cursor: Cursor) {
        mIndexRuleId = cursor.getColumnIndex(Contract.CategoryRule.ID)
        mIndexPattern = cursor.getColumnIndex(Contract.CategoryRule.PATTERN)
        mIndexCategoryName = cursor.getColumnIndex(Contract.CategoryRule.CATEGORY_NAME)
    }

    override fun onBindViewHolder(holder: RuleViewHolder, cursor: Cursor) {
        holder.mPatternTextView.text = cursor.getString(mIndexPattern)
        holder.mCategoryTextView.text = cursor.getString(mIndexCategoryName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.adapter_category_rule_sort_item, parent, false)
        return RuleViewHolder(itemView)
    }

    fun getSortedCategoryRuleIds(): List<Long> = getSortedIds(mIndexRuleId)

    inner class RuleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        internal val mPatternTextView: TextView = itemView.findViewById(R.id.primary_text_view)
        internal val mCategoryTextView: TextView = itemView.findViewById(R.id.secondary_text_view)
        private val mActionImageView: ImageView = itemView.findViewById(R.id.action_image_view)

        init {
            attachDragHandle(mActionImageView) {
                mListener?.onCategoryRuleDragStarted(this)
            }
        }
    }

    interface CategoryRuleSortListener {

        fun onCategoryRuleDragStarted(viewHolder: RecyclerView.ViewHolder)
    }
}
