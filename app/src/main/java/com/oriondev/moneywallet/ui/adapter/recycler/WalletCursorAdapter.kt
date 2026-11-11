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
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.MoneyFormatter

/**
 * Created by andrea on 26/01/18.
 */
class WalletCursorAdapter(
    private val mListener: WalletActionListener?
) : AbstractCursorAdapter<WalletCursorAdapter.WalletViewHolder>(null, Contract.Wallet.ID) {

    private var mIndexId: Int = -1
    private var mIndexName: Int = -1
    private var mIndexIcon: Int = -1
    private var mIndexCurrency: Int = -1
    private var mIndexStartMoney: Int = -1
    private var mIndexTotalMoney: Int = -1

    private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()

    override fun onLoadColumnIndices(cursor: Cursor) {
        mIndexId = cursor.getColumnIndex(Contract.Wallet.ID)
        mIndexName = cursor.getColumnIndex(Contract.Wallet.NAME)
        mIndexIcon = cursor.getColumnIndex(Contract.Wallet.ICON)
        mIndexCurrency = cursor.getColumnIndex(Contract.Wallet.CURRENCY)
        mIndexStartMoney = cursor.getColumnIndex(Contract.Wallet.START_MONEY)
        mIndexTotalMoney = cursor.getColumnIndex(Contract.Wallet.TOTAL_MONEY)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WalletViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.adapter_wallet_item, parent, false)
        return WalletViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: WalletViewHolder, cursor: Cursor) {
        IconLoader.parseAndLoad(cursor.getString(mIndexIcon), holder.mIconView)
        holder.mNameView.text = cursor.getString(mIndexName)
        val currency = CurrencyManager.getCurrency(cursor.getString(mIndexCurrency))
        val money = cursor.getLong(mIndexStartMoney) + cursor.getLong(mIndexTotalMoney)
        mMoneyFormatter.applyNotTinted(holder.mMoneyView, currency, money)
    }

    inner class WalletViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        val mIconView: ImageView = itemView.findViewById(R.id.icon_image_view)
        val mNameView: TextView = itemView.findViewById(R.id.name_text_view)
        val mMoneyView: TextView = itemView.findViewById(R.id.money_text_view)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(view: View) {
            val listener = mListener ?: return
            val cursor = getSafeCursor(adapterPosition) ?: return
            listener.onWalletClick(cursor.getLong(mIndexId))
        }
    }

    interface WalletActionListener {
        fun onWalletClick(id: Long)
    }
}
