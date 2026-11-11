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
import com.oriondev.moneywallet.model.Event
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader

/**
 * Created by andrea on 03/03/18.
 */
class EventSelectorCursorAdapter(
    private val mController: Controller?
) : AbstractCursorAdapter<EventSelectorCursorAdapter.ViewHolder>(null, Contract.Event.ID) {

    private var mIndexId = -1
    private var mIndexIcon = -1
    private var mIndexName = -1
    private var mIndexStartDate = -1
    private var mIndexEndDate = -1

    override fun onLoadColumnIndices(cursor: Cursor) {
        mIndexId = cursor.getColumnIndex(Contract.Event.ID)
        mIndexIcon = cursor.getColumnIndex(Contract.Event.ICON)
        mIndexName = cursor.getColumnIndex(Contract.Event.NAME)
        mIndexStartDate = cursor.getColumnIndex(Contract.Event.START_DATE)
        mIndexEndDate = cursor.getColumnIndex(Contract.Event.END_DATE)
    }

    override fun onBindViewHolder(holder: ViewHolder, cursor: Cursor) {
        val icon = IconLoader.parse(cursor.getString(mIndexIcon))
        IconLoader.loadInto(icon, holder.mAvatarImageView)
        holder.mPrimaryTextView.text = cursor.getString(mIndexName)
        val endDate = DateUtils.getDateFromSQLDateString(cursor.getString(mIndexEndDate).orEmpty())
        DateFormatter.applyDateFromToday(holder.mSecondaryTextView, endDate, R.string.relative_string_will_end_on)
        val selected = mController?.isEventSelected(cursor.getLong(mIndexId)) == true
        holder.mSelectorImageView.visibility = if (selected) View.VISIBLE else View.GONE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.adapter_event_selector_item, parent, false)
        return ViewHolder(itemView)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        internal val mAvatarImageView: ImageView = itemView.findViewById(R.id.avatar_image_view)
        internal val mPrimaryTextView: TextView = itemView.findViewById(R.id.primary_text_view)
        internal val mSecondaryTextView: TextView = itemView.findViewById(R.id.secondary_text_view)
        internal val mSelectorImageView: ImageView = itemView.findViewById(R.id.selector_image_view)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            val controller = mController ?: return
            @Suppress("DEPRECATION")
            val cursor = getSafeCursor(adapterPosition) ?: return
            val event = Event(
                cursor.getLong(mIndexId),
                cursor.getString(mIndexName),
                IconLoader.parse(cursor.getString(mIndexIcon)),
                DateUtils.getDateFromSQLDateString(cursor.getString(mIndexStartDate).orEmpty()),
                DateUtils.getDateFromSQLDateString(cursor.getString(mIndexEndDate).orEmpty())
            )
            controller.onEventClick(event)
        }
    }

    interface Controller {

        fun onEventClick(event: Event)

        fun isEventSelected(id: Long): Boolean
    }
}
