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
import com.oriondev.moneywallet.model.ColorIcon
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.model.IconGroup
import com.oriondev.moneywallet.model.VectorIcon
import com.oriondev.moneywallet.utils.IconLoader
import java.util.Locale

/**
 * Created by andrea on 12/08/18.
 */
class IconAdapter(
    private val mController: Controller?
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val mOriginalIconGroups: MutableList<IconGroup> = ArrayList()
    private val mIconGroups: MutableList<IconGroup> = ArrayList()
    private val mItems: MutableList<ItemWrapper> = ArrayList()
    private var mOverrideColor: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER ->
                HeaderViewHolder(inflater.inflate(R.layout.adapter_header_item, parent, false))
            VIEW_TYPE_ITEM ->
                ItemViewHolder(inflater.inflate(R.layout.adapter_icon_item, parent, false))
            else -> throw IllegalArgumentException("Invalid view holder type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> holder.mHeaderTextView.text = getHeaderTextAt(position)
            is ItemViewHolder -> IconLoader.loadInto(getIconAt(position), holder.mIconImageView)
        }
    }

    override fun getItemCount(): Int = mItems.size

    override fun getItemViewType(position: Int): Int {
        return if (isHeader(position)) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM
    }

    fun setIconGroups(iconGroups: List<IconGroup>?) {
        mOriginalIconGroups.clear()
        if (iconGroups != null) {
            mOriginalIconGroups.addAll(iconGroups)
        }
        applyFilter("")
    }

    fun setFilter(query: String?) {
        applyFilter(query?.lowercase(Locale.getDefault()) ?: "")
    }

    private fun applyFilter(query: String) {
        mIconGroups.clear()
        mItems.clear()
        for (i in mOriginalIconGroups.indices) {
            val group = mOriginalIconGroups[i]
            val matchingIndices = group.groupIcons.indices.filter { j ->
                query.isEmpty() || matchesQuery(group.groupIcons[j], query)
            }
            if (matchingIndices.isNotEmpty()) {
                mItems.add(ItemWrapper(i, INDEX_HEADER))
                mIconGroups.add(group)
                for (j in matchingIndices) {
                    mItems.add(ItemWrapper(i, j))
                }
            }
        }
        notifyDataSetChanged()
    }

    private fun matchesQuery(icon: Icon, query: String): Boolean {
        return when (icon) {
            is VectorIcon -> icon.getResourceName().lowercase(Locale.getDefault()).contains(query)
            is ColorIcon -> icon.name.lowercase(Locale.getDefault()).contains(query)
            else -> false
        }
    }

    fun isHeader(position: Int): Boolean = mItems[position].mItemIndex == INDEX_HEADER

    private fun getHeaderTextAt(position: Int): String {
        val itemWrapper = mItems[position]
        return mOriginalIconGroups[itemWrapper.mGroupIndex].groupName
    }

    fun setOverrideColor(color: String?) {
        mOverrideColor = color
        notifyDataSetChanged()
    }

    private fun getIconAt(position: Int): Icon? {
        if (position !in mItems.indices) {
            return null
        }
        val itemWrapper = mItems[position]
        val icon = mOriginalIconGroups[itemWrapper.mGroupIndex].groupIcons[itemWrapper.mItemIndex]
        val overrideColor = mOverrideColor
        return if (overrideColor != null && icon is VectorIcon) {
            VectorIcon(icon, overrideColor)
        } else {
            icon
        }
    }

    private class ItemWrapper(
        val mGroupIndex: Int,
        val mItemIndex: Int
    )

    private class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val mHeaderTextView: TextView = itemView.findViewById(R.id.left_text_view)
    }

    private inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        val mIconImageView: ImageView = itemView.findViewById(R.id.icon_image_view)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            val controller = mController ?: return
            @Suppress("DEPRECATION")
            val icon = getIconAt(adapterPosition)
            if (icon != null) {
                controller.onIconClick(icon)
            }
        }
    }

    interface Controller {

        fun onIconClick(icon: Icon)
    }

    companion object {
        private const val INDEX_HEADER = -1
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }
}
