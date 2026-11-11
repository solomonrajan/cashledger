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
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R

/**
 * Created by andrea on 06/03/18.
 */
class SettingCategoryAdapter(
    private val mActionListener: ActionListener?
) : RecyclerView.Adapter<SettingCategoryAdapter.ViewHolder>() {

    private val mItems: MutableList<Item> = ArrayList()

    fun addCategory(
        id: Int,
        @DrawableRes icon: Int,
        @StringRes primaryText: Int,
        @StringRes secondaryText: Int
    ) {
        mItems.add(Item(id, icon, primaryText, secondaryText))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.adapter_setting_item, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = mItems[position]
        holder.mIconImageView.setImageResource(item.mIconRes)
        holder.mPrimaryTextView.setText(item.mPrimaryTextRes)
        holder.mSecondaryTextView.setText(item.mSecondaryTextRes)
    }

    override fun getItemCount(): Int = mItems.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        val mIconImageView: ImageView = itemView.findViewById(R.id.icon_image_view)
        val mPrimaryTextView: TextView = itemView.findViewById(R.id.primary_text_view)
        val mSecondaryTextView: TextView = itemView.findViewById(R.id.secondary_text_view)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(view: View) {
            val listener = mActionListener ?: return
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onSettingCategoryClick(mItems[position].mId)
            }
        }
    }

    private class Item(
        val mId: Int,
        @DrawableRes val mIconRes: Int,
        @StringRes val mPrimaryTextRes: Int,
        @StringRes val mSecondaryTextRes: Int
    )

    interface ActionListener {
        fun onSettingCategoryClick(id: Int)
    }
}
