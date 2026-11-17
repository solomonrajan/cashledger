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
import com.oriondev.moneywallet.model.DataFormat

/**
 * Created by andrea on 20/12/18.
 */
class DataFormatSelectorAdapter(
    private val mDataFormats: Array<DataFormat>?,
    private val mController: Controller?
) : RecyclerView.Adapter<DataFormatSelectorAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.adapter_data_format_selector_item, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val dataFormat = mDataFormats?.getOrNull(position) ?: return
        when (dataFormat) {
            DataFormat.CSV -> {
                holder.mAvatarImageView.setImageResource(R.drawable.ic_file_csv_24dp)
                holder.mPrimaryTextView.setText(R.string.hint_data_format_csv)
            }
            DataFormat.XLS -> {
                holder.mAvatarImageView.setImageResource(R.drawable.ic_file_xls_24dp)
                holder.mPrimaryTextView.setText(R.string.hint_data_format_xls)
            }
            DataFormat.PDF -> {
                holder.mAvatarImageView.setImageResource(R.drawable.ic_file_pdf_24dp)
                holder.mPrimaryTextView.setText(R.string.hint_data_format_pdf)
            }
        }
        val selected = mController?.isDataFormatSelected(dataFormat) == true
        holder.mSelectorImageView.visibility = if (selected) View.VISIBLE else View.GONE
    }

    override fun getItemCount(): Int = mDataFormats?.size ?: 0

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        internal val mAvatarImageView: ImageView = itemView.findViewById(R.id.avatar_image_view)
        internal val mPrimaryTextView: TextView = itemView.findViewById(R.id.primary_text_view)
        internal val mSelectorImageView: ImageView = itemView.findViewById(R.id.selector_image_view)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            val controller = mController ?: return
            @Suppress("DEPRECATION")
            val position = adapterPosition
            val dataFormat = mDataFormats?.getOrNull(position) ?: return
            controller.onDataFormatSelected(dataFormat)
        }
    }

    interface Controller {

        fun onDataFormatSelected(dataFormat: DataFormat)

        fun isDataFormatSelected(dataFormat: DataFormat): Boolean
    }
}
