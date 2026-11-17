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
import com.oriondev.moneywallet.utils.IconLoader

/**
 * Created by andrea on 10/03/18.
 */
class PlaceCursorAdapter(
    private val mActionListener: ActionListener?
) : AbstractCursorAdapter<PlaceCursorAdapter.ViewHolder>(null, Contract.Place.ID) {

    private var mIndexId: Int = -1
    private var mIndexName: Int = -1
    private var mIndexIcon: Int = -1
    private var mIndexAddress: Int = -1

    override fun onLoadColumnIndices(cursor: Cursor) {
        mIndexId = cursor.getColumnIndex(Contract.Place.ID)
        mIndexName = cursor.getColumnIndex(Contract.Place.NAME)
        mIndexIcon = cursor.getColumnIndex(Contract.Place.ICON)
        mIndexAddress = cursor.getColumnIndex(Contract.Place.ADDRESS)
    }

    override fun onBindViewHolder(holder: ViewHolder, cursor: Cursor) {
        val icon = IconLoader.parse(cursor.getString(mIndexIcon))
        IconLoader.loadInto(icon, holder.mAvatarImageView)
        holder.mNameTextView.text = cursor.getString(mIndexName)
        val address = cursor.getString(mIndexAddress)
        if (address.isNullOrEmpty()) {
            holder.mAddressTextView.setText(R.string.hint_address_unknown)
        } else {
            holder.mAddressTextView.text = address
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.adapter_place_item, parent, false)
        return ViewHolder(itemView)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        val mAvatarImageView: ImageView = itemView.findViewById(R.id.avatar_image_view)
        val mNameTextView: TextView = itemView.findViewById(R.id.name_text_view)
        val mAddressTextView: TextView = itemView.findViewById(R.id.address_text_view)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            val listener = mActionListener ?: return
            val cursor = getSafeCursor(adapterPosition) ?: return
            listener.onPlaceClick(cursor.getLong(mIndexId))
        }
    }

    interface ActionListener {
        fun onPlaceClick(id: Long)
    }
}
