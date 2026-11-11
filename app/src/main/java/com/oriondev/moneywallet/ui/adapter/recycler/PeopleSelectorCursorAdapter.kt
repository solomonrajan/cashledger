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
import com.oriondev.moneywallet.model.Person
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.utils.IconLoader

/**
 * Created by andre on 18/03/2018.
 */
class PeopleSelectorCursorAdapter(
    private val mController: Controller?
) : AbstractCursorAdapter<PeopleSelectorCursorAdapter.ViewHolder>(null, Contract.Person.ID) {

    private var mIndexId: Int = -1
    private var mIndexName: Int = -1
    private var mIndexIcon: Int = -1

    override fun onLoadColumnIndices(cursor: Cursor) {
        mIndexId = cursor.getColumnIndex(Contract.Person.ID)
        mIndexName = cursor.getColumnIndex(Contract.Person.NAME)
        mIndexIcon = cursor.getColumnIndex(Contract.Person.ICON)
    }

    override fun onBindViewHolder(holder: ViewHolder, cursor: Cursor) {
        val icon = IconLoader.parse(cursor.getString(mIndexIcon))
        IconLoader.loadInto(icon, holder.mAvatarImageView)
        holder.mPrimaryTextView.text = cursor.getString(mIndexName)
        val selected = mController?.isPersonSelected(cursor.getLong(mIndexId)) == true
        holder.mSelectorImageView.visibility = if (selected) View.VISIBLE else View.GONE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.adapter_person_selector_item, parent, false)
        return ViewHolder(itemView)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        val mAvatarImageView: ImageView = itemView.findViewById(R.id.avatar_image_view)
        val mPrimaryTextView: TextView = itemView.findViewById(R.id.primary_text_view)
        val mSelectorImageView: ImageView = itemView.findViewById(R.id.selector_image_view)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            val controller = mController ?: return
            val cursor = getSafeCursor(adapterPosition) ?: return
            val person = Person(
                cursor.getLong(mIndexId),
                cursor.getString(mIndexName),
                IconLoader.parse(cursor.getString(mIndexIcon))
            )
            controller.onPersonSelected(person)
        }
    }

    interface Controller {
        fun onPersonSelected(person: Person)

        fun isPersonSelected(id: Long): Boolean
    }
}
