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
import com.oriondev.moneywallet.model.Money
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.MoneyFormatter
import java.util.Date

/**
 * Created by andrea on 03/03/18.
 */
class EventCursorAdapter(
    private val mActionListener: ActionListener?
) : AbstractCursorAdapter<EventCursorAdapter.ViewHolder>(null, Contract.Event.ID) {

    private var mIndexId = -1
    private var mIndexIcon = -1
    private var mIndexName = -1
    private var mIndexEndDate = -1
    private var mIndexProgress = -1

    private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()

    override fun onLoadColumnIndices(cursor: Cursor) {
        mIndexId = cursor.getColumnIndex(Contract.Event.ID)
        mIndexIcon = cursor.getColumnIndex(Contract.Event.ICON)
        mIndexName = cursor.getColumnIndex(Contract.Event.NAME)
        mIndexEndDate = cursor.getColumnIndex(Contract.Event.END_DATE)
        mIndexProgress = cursor.getColumnIndex(Contract.Event.PROGRESS)
    }

    override fun onBindViewHolder(holder: ViewHolder, cursor: Cursor) {
        val icon = IconLoader.parse(cursor.getString(mIndexIcon))
        IconLoader.loadInto(icon, holder.mAvatarImageView)
        holder.mPrimaryTextView.text = cursor.getString(mIndexName)
        val money = Money.parse(cursor.getString(mIndexProgress))
        mMoneyFormatter.applyTinted(holder.mMoneyTextView, money)
        val date = DateUtils.getDateFromSQLDateString(cursor.getString(mIndexEndDate).orEmpty())
        applyRelativeDate(date, holder.mSecondaryTextView)
    }

    private fun applyRelativeDate(date: Date, textView: TextView) {
        val labelRes = if (date.time <= System.currentTimeMillis()) {
            R.string.relative_string_ended_on
        } else {
            R.string.relative_string_will_end_on
        }
        DateFormatter.applyDateFromToday(textView, date, labelRes)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.adapter_event_item, parent, false)
        return ViewHolder(itemView)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        internal val mAvatarImageView: ImageView = itemView.findViewById(R.id.avatar_image_view)
        internal val mPrimaryTextView: TextView = itemView.findViewById(R.id.primary_text_view)
        internal val mMoneyTextView: TextView = itemView.findViewById(R.id.money_text_view)
        internal val mSecondaryTextView: TextView = itemView.findViewById(R.id.secondary_text_view)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            val listener = mActionListener ?: return
            @Suppress("DEPRECATION")
            val cursor = getSafeCursor(adapterPosition)
            if (cursor != null) {
                listener.onEventClick(cursor.getLong(mIndexId))
            }
        }
    }

    interface ActionListener {

        fun onEventClick(id: Long)
    }
}
