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
import com.oriondev.moneywallet.model.OverviewData
import com.oriondev.moneywallet.model.PeriodMoney
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.MoneyFormatter

/**
 * Created by andrea on 17/08/18.
 */
class OverviewItemAdapter(
    private val mController: Controller?
) : RecyclerView.Adapter<OverviewItemAdapter.ViewHolder>() {

    private var mData: OverviewData? = null
    private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(inflater.inflate(R.layout.adapter_overview_item, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val data = mData ?: return
        val periodMoney = data.getPeriodMoney(position)
        DateFormatter.applyDateRange(holder.mNameTextView, periodMoney.startDate, periodMoney.endDate)
        // TODO: maybe can be useful to display also incomes and expenses
        mMoneyFormatter.applyTinted(holder.mMoneyTextView, periodMoney.netIncomes)
    }

    override fun getItemCount(): Int = mData?.getPeriodCount() ?: 0

    fun setData(data: OverviewData?) {
        mData = data
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        internal val mNameTextView: TextView = itemView.findViewById(R.id.name_text_view)
        internal val mMoneyTextView: TextView = itemView.findViewById(R.id.money_text_view)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            val controller = mController ?: return
            @Suppress("DEPRECATION")
            val index = adapterPosition
            val data = mData
            if (data != null && index in 0 until data.getPeriodCount()) {
                controller.onPeriodClick(data.getPeriodMoney(index))
            }
        }
    }

    interface Controller {

        fun onPeriodClick(periodMoney: PeriodMoney)
    }
}
