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
import com.oriondev.moneywallet.model.PeriodDetailSummaryData
import com.oriondev.moneywallet.model.PeriodMoney
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.MoneyFormatter

/**
 * Created by andrea on 14/08/18.
 */
class PeriodDetailSummaryAdapter(
    private val mController: Controller?
) : RecyclerView.Adapter<PeriodDetailSummaryAdapter.ViewHolder>() {

    private var mData: PeriodDetailSummaryData? = null
    private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.adapter_period_summary_item, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val data = mData ?: return
        val periodMoney = data.getPeriodMoney(position)
        if (!periodMoney.isTimeRange()) {
            DateFormatter.applyDateRange(holder.mNameTextView, periodMoney.startDate, periodMoney.endDate)
        } else {
            DateFormatter.applyTimeRange(holder.mNameTextView, periodMoney.startDate, periodMoney.endDate)
        }
        // TODO: maybe can be useful to display also incomes and expenses
        mMoneyFormatter.applyTinted(holder.mMoneyTextView, periodMoney.netIncomes)
    }

    override fun getItemCount(): Int {
        val data = mData
        return data?.getPeriodCount() ?: 0
    }

    fun setData(data: PeriodDetailSummaryData?) {
        mData = data
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        val mNameTextView: TextView = itemView.findViewById(R.id.name_text_view)
        val mMoneyTextView: TextView = itemView.findViewById(R.id.money_text_view)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            val controller = mController ?: return
            val data = mData ?: return
            val index = adapterPosition
            if (index != RecyclerView.NO_POSITION) {
                val periodMoney = data.getPeriodMoney(index)
                controller.onPeriodClick(periodMoney)
            }
        }
    }

    interface Controller {
        fun onPeriodClick(periodMoney: PeriodMoney)
    }
}
