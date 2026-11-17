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

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.ChangeLog
import com.oriondev.moneywallet.storage.cache.TypefaceCache

/**
 * Created by andrea on 06/04/18.
 */
class ChangeLogAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var mChangeLogList: List<ChangeLog> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            ChangeLog.TYPE_HEADER ->
                HeaderViewHolder(inflater.inflate(R.layout.adapter_change_log_header, parent, false))
            ChangeLog.TYPE_ITEM ->
                ItemViewHolder(inflater.inflate(R.layout.adapter_change_log_item, parent, false))
            else -> throw IllegalArgumentException("Invalid item view type: $viewType")
        }
    }

    override fun getItemViewType(position: Int): Int = mChangeLogList[position].type

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = mChangeLogList[position]
        when (holder) {
            is HeaderViewHolder -> {
                val context = holder.mVersionTextView.context
                val versionName = String.format(
                    context.getString(R.string.relative_string_version_name),
                    item.versionName
                )
                holder.mVersionTextView.text = versionName
                holder.mDateTextView.text = item.versionDate
            }
            is ItemViewHolder -> {
                @Suppress("DEPRECATION")
                holder.mDescriptionTextView.text = Html.fromHtml(item.changeText.orEmpty())
                holder.mIndexTextView.text = getIndex(position)
            }
        }
    }

    private fun getIndex(position: Int): String {
        var index = 0
        var pos = position
        while (pos >= 0 && mChangeLogList[pos].type != ChangeLog.TYPE_HEADER) {
            index++
            pos--
        }
        return "$index - "
    }

    fun setChangeLogs(changeLogList: List<ChangeLog>?) {
        mChangeLogList = changeLogList ?: emptyList()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = mChangeLogList.size

    internal class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        internal val mVersionTextView: TextView = itemView.findViewById(R.id.version_name_text_view)
        internal val mDateTextView: TextView = itemView.findViewById(R.id.version_date_text_view)

        init {
            val typeface = TypefaceCache.get(itemView.context, TypefaceCache.ROBOTO_MEDIUM)
            mVersionTextView.typeface = typeface
            mDateTextView.typeface = typeface
        }
    }

    internal class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        internal val mDescriptionTextView: TextView = itemView.findViewById(R.id.description_text_view)
        internal val mIndexTextView: TextView = itemView.findViewById(R.id.index_text_view)

        init {
            val typeface = TypefaceCache.get(itemView.context, TypefaceCache.ROBOTO_REGULAR)
            mDescriptionTextView.typeface = typeface
            mIndexTextView.typeface = typeface
        }
    }
}
