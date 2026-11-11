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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.CategoryMoney
import com.oriondev.moneywallet.model.PeriodDetailFlowData
import com.oriondev.moneywallet.ui.view.CategoryChildIndicator
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.MoneyFormatter

/**
 * Created by andrea on 13/08/18.
 */
class PeriodDetailFlowAdapter(
    private val mController: Controller?,
    private val mIncomes: Boolean
) : RecyclerView.Adapter<PeriodDetailFlowAdapter.ViewHolder>() {

    private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()
    private val mItems: MutableList<ItemWrapper> = ArrayList()
    private val mExpandedCategories: MutableSet<Long> = HashSet()

    private var mData: PeriodDetailFlowData? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val layoutRes = if (viewType == VIEW_TYPE_PARENT) {
            R.layout.adapter_category_money_item
        } else {
            R.layout.adapter_sub_category_money_item
        }
        return ViewHolder(inflater.inflate(layoutRes, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = getCategoryAt(position) ?: return
        IconLoader.loadInto(category.icon, holder.mIconImageView)
        holder.mNameTextView.text = category.name
        if (mIncomes) {
            mMoneyFormatter.applyTintedIncome(holder.mMoneyTextView, category.money)
        } else {
            mMoneyFormatter.applyTintedExpense(holder.mMoneyTextView, category.money)
        }
        if (holder.itemViewType == VIEW_TYPE_CHILD) {
            holder.mChildIndicator?.setLast(isLastChild(position))
        } else {
            bindChildrenToggle(holder, category)
        }
    }

    private fun bindChildrenToggle(holder: ViewHolder, category: CategoryMoney) {
        val toggle = holder.mChildrenToggle ?: return
        val hasChildren = category.children.isNotEmpty()
        // Invisible and not gone, so every amount keeps the same right edge. A child's amount is
        // read against the parent total above it, so the two have to line up.
        toggle.visibility = if (hasChildren) View.VISIBLE else View.INVISIBLE
        if (hasChildren) {
            val expanded = mExpandedCategories.contains(category.id)
            toggle.rotation = if (expanded) 180f else 0f
            // Named, because a screen reader reaches the arrow on its own and every one of them
            // would otherwise read the same words.
            val descriptionRes = if (expanded) {
                R.string.description_hide_subcategories
            } else {
                R.string.description_show_subcategories
            }
            toggle.contentDescription = holder.itemView.context.getString(descriptionRes, category.name)
        }
    }

    /** The last child under its category, which tells the indicator to stop its line half way. */
    private fun isLastChild(position: Int): Boolean {
        val data = mData ?: return false
        val item = mItems[position]
        val parent = data.getCategory(item.mParentIndex)
        return item.mChildIndex == parent.children.size - 1
    }

    override fun getItemCount(): Int = mItems.size

    override fun getItemViewType(position: Int): Int {
        return if (mItems[position].mChildIndex == INDEX_PARENT) VIEW_TYPE_PARENT else VIEW_TYPE_CHILD
    }

    /**
     * Expansion is deliberately kept. The loader redelivers its cached result whenever this screen
     * starts again, so clearing here collapsed the list on the way back from a category.
     */
    fun setData(data: PeriodDetailFlowData?) {
        mData = data
        rebuildItems()
    }

    /** Categories start collapsed, so opening a period shows the list of totals it always did. */
    private fun rebuildItems() {
        mItems.clear()
        val data = mData
        if (data != null) {
            for (i in 0 until data.getCategoryCount()) {
                val category = data.getCategory(i)
                mItems.add(ItemWrapper(i, INDEX_PARENT))
                if (mExpandedCategories.contains(category.id)) {
                    for (j in category.children.indices) {
                        mItems.add(ItemWrapper(i, j))
                    }
                }
            }
        }
        notifyDataSetChanged()
    }

    fun getExpandedCategories(): LongArray {
        val expanded = LongArray(mExpandedCategories.size)
        var index = 0
        for (categoryId in mExpandedCategories) {
            expanded[index++] = categoryId
        }
        return expanded
    }

    fun setExpandedCategories(expanded: LongArray?) {
        mExpandedCategories.clear()
        if (expanded != null) {
            for (categoryId in expanded) {
                mExpandedCategories.add(categoryId)
            }
        }
        rebuildItems()
    }

    private fun toggleChildren(categoryId: Long) {
        if (!mExpandedCategories.remove(categoryId)) {
            mExpandedCategories.add(categoryId)
        }
        rebuildItems()
    }

    private fun getCategoryAt(position: Int): CategoryMoney? {
        val data = mData ?: return null
        val item = mItems[position]
        val parent = data.getCategory(item.mParentIndex)
        return if (item.mChildIndex == INDEX_PARENT) parent else parent.children[item.mChildIndex]
    }

    private class ItemWrapper(
        val mParentIndex: Int,
        val mChildIndex: Int
    )

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        val mChildIndicator: CategoryChildIndicator? = itemView.findViewById(R.id.category_child_indicator)
        val mIconImageView: ImageView = itemView.findViewById(R.id.icon_image_view)
        val mChildrenToggle: ImageView? = itemView.findViewById(R.id.children_toggle_image_view)
        val mNameTextView: TextView = itemView.findViewById(R.id.name_text_view)
        val mMoneyTextView: TextView = itemView.findViewById(R.id.money_text_view)

        init {
            itemView.setOnClickListener(this)
            // Its own listener, because the row already has a click of its own.
            mChildrenToggle?.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val category = getCategoryAt(position)
                    if (category != null) {
                        toggleChildren(category.id)
                    }
                }
            }
        }

        override fun onClick(v: View) {
            val controller = mController ?: return
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                val category = getCategoryAt(position)
                if (category != null) {
                    controller.onCategoryClick(category.id)
                }
            }
        }
    }

    interface Controller {
        fun onCategoryClick(id: Long)
    }

    companion object {
        private const val INDEX_PARENT = -1
        private const val VIEW_TYPE_PARENT = 0
        private const val VIEW_TYPE_CHILD = 1
    }
}
