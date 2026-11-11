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
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.MoneyFormatter

/**
 * Created by andrea on 03/03/18.
 */
class RecurrentTransferCursorAdapter(
    private val mActionListener: ActionListener?
) : AbstractCursorAdapter<RecurrentTransferCursorAdapter.RecurrentTransferViewHolder>(
    null,
    Contract.RecurrentTransfer.ID
) {

    private var mIndexId: Int = -1
    private var mIndexWalletFromName: Int = -1
    private var mIndexWalletFromCurrency: Int = -1
    private var mIndexWalletToName: Int = -1
    private var mIndexWalletToIcon: Int = -1
    private var mIndexMoneyFrom: Int = -1
    private var mIndexNextOccurrence: Int = -1

    private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()

    override fun onLoadColumnIndices(cursor: Cursor) {
        mIndexId = cursor.getColumnIndex(Contract.RecurrentTransfer.ID)
        mIndexWalletFromName = cursor.getColumnIndex(Contract.RecurrentTransfer.WALLET_FROM_NAME)
        mIndexWalletFromCurrency = cursor.getColumnIndex(Contract.RecurrentTransfer.WALLET_FROM_CURRENCY)
        mIndexWalletToName = cursor.getColumnIndex(Contract.RecurrentTransfer.WALLET_TO_NAME)
        mIndexWalletToIcon = cursor.getColumnIndex(Contract.RecurrentTransfer.WALLET_TO_ICON)
        mIndexMoneyFrom = cursor.getColumnIndex(Contract.RecurrentTransfer.MONEY_FROM)
        mIndexNextOccurrence = cursor.getColumnIndex(Contract.RecurrentTransfer.NEXT_OCCURRENCE)
    }

    override fun onBindViewHolder(holder: RecurrentTransferViewHolder, cursor: Cursor) {
        val icon = IconLoader.parse(cursor.getString(mIndexWalletToIcon))
        IconLoader.loadInto(icon, holder.mAvatarImageView)
        holder.mPrimaryTextView.setText(R.string.system_category_transfer)
        val moneyFrom = cursor.getLong(mIndexMoneyFrom)
        val currencyFrom = CurrencyManager.getCurrency(cursor.getString(mIndexWalletFromCurrency))
        mMoneyFormatter.applyNotTinted(holder.mMoneyTextView, currencyFrom, moneyFrom)
        val walletFrom = cursor.getString(mIndexWalletFromName)
        val walletTo = cursor.getString(mIndexWalletToName)
        holder.mSecondaryTextView.text = formatTransferRoute(walletFrom, walletTo)
        if (cursor.isNull(mIndexNextOccurrence)) {
            holder.mDateTextView.setText(R.string.hint_recurrence_finished)
        } else {
            val nextOccurrence = DateUtils.getDateFromSQLDateString(cursor.getString(mIndexNextOccurrence))
            DateFormatter.applyDate(holder.mDateTextView, nextOccurrence)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecurrentTransferViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.adapter_transfer_item, parent, false)
        return RecurrentTransferViewHolder(itemView)
    }

    inner class RecurrentTransferViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        val mAvatarImageView: ImageView = itemView.findViewById(R.id.avatar_image_view)
        val mPrimaryTextView: TextView = itemView.findViewById(R.id.primary_text_view)
        val mMoneyTextView: TextView = itemView.findViewById(R.id.money_text_view)
        val mSecondaryTextView: TextView = itemView.findViewById(R.id.secondary_text_view)
        val mDateTextView: TextView = itemView.findViewById(R.id.date_text_view)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            val listener = mActionListener ?: return
            val cursor = getSafeCursor(adapterPosition) ?: return
            listener.onRecurrentTransferClick(cursor.getLong(mIndexId))
        }
    }

    interface ActionListener {
        fun onRecurrentTransferClick(id: Long)
    }
}
