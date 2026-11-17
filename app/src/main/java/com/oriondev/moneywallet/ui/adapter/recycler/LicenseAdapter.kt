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
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.License

/**
 * Created by andrea on 31/03/18.
 */
class LicenseAdapter(
    private val mController: Controller?
) : RecyclerView.Adapter<LicenseAdapter.ViewHolder>() {

    private var mLicenses: List<License>? = null

    fun setLicenses(licenses: List<License>?) {
        mLicenses = licenses
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.adapter_license_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val license = mLicenses?.getOrNull(position) ?: return
        holder.mPrimaryTextView.text = license.name
        holder.mSecondaryTextView.text = license.typeName
    }

    override fun getItemCount(): Int = mLicenses?.size ?: 0

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        internal val mPrimaryTextView: TextView = itemView.findViewById(R.id.primary_text_view)
        internal val mSecondaryTextView: TextView = itemView.findViewById(R.id.secondary_text_view)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            val controller = mController ?: return
            @Suppress("DEPRECATION")
            val license = mLicenses?.getOrNull(adapterPosition) ?: return
            controller.onLicenseClick(license)
        }
    }

    interface Controller {

        fun onLicenseClick(license: License)
    }
}
