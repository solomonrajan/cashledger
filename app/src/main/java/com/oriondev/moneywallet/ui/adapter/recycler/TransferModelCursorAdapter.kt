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
import com.oriondev.moneywallet.ui.view.CardButton
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.MoneyFormatter

/**
 * Created by andrea on 14/03/18.
 */
class TransferModelCursorAdapter(
    private val mActionListener: ActionListener?
) : AbstractCursorAdapter<TransferModelCursorAdapter.TransferModelViewHolder>(
    null,
    Contract.TransferModel.ID
) {

    private var mIndexId: Int = -1
    private var mIndexWalletFromName: Int = -1
    private var mIndexWalletFromCurrency: Int = -1
    private var mIndexWalletToName: Int = -1
    private var mIndexWalletToIcon: Int = -1
    private var mIndexMoneyFrom: Int = -1
    private var mIndexMoneyTax: Int = -1

    private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()

    override fun onLoadColumnIndices(cursor: Cursor) {
        mIndexId = cursor.getColumnIndex(Contract.TransferModel.ID)
        mIndexWalletFromName = cursor.getColumnIndex(Contract.TransferModel.WALLET_FROM_NAME)
        mIndexWalletFromCurrency = cursor.getColumnIndex(Contract.TransferModel.WALLET_FROM_CURRENCY)
        mIndexWalletToName = cursor.getColumnIndex(Contract.TransferModel.WALLET_TO_NAME)
        mIndexWalletToIcon = cursor.getColumnIndex(Contract.TransferModel.WALLET_TO_ICON)
        mIndexMoneyFrom = cursor.getColumnIndex(Contract.TransferModel.MONEY_FROM)
        mIndexMoneyTax = cursor.getColumnIndex(Contract.TransferModel.MONEY_TAX)
    }

    override fun onBindViewHolder(holder: TransferModelViewHolder, cursor: Cursor) {
        val icon = IconLoader.parse(cursor.getString(mIndexWalletToIcon))
        IconLoader.loadInto(icon, holder.mAvatarImageView)
        holder.mPrimaryTextView.setText(R.string.system_category_transfer)
        val moneyFrom = cursor.getLong(mIndexMoneyFrom)
        val moneyTax = cursor.getLong(mIndexMoneyTax)
        val currencyFrom = CurrencyManager.getCurrency(cursor.getString(mIndexWalletFromCurrency))
        mMoneyFormatter.applyNotTinted(holder.mMoneyTextView, currencyFrom, moneyFrom)
        val walletFrom = cursor.getString(mIndexWalletFromName)
        val walletTo = cursor.getString(mIndexWalletToName)
        holder.mSecondaryTextView.text = formatTransferRoute(walletFrom, walletTo)
        if (moneyTax != 0L) {
            holder.mTaxLayout.visibility = View.VISIBLE
            mMoneyFormatter.applyNotTinted(holder.mTaxTextView, currencyFrom, moneyTax)
        } else {
            holder.mTaxLayout.visibility = View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransferModelViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.adapter_model_transfer_item, parent, false)
        return TransferModelViewHolder(itemView)
    }

    inner class TransferModelViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        val mAvatarImageView: ImageView = itemView.findViewById(R.id.avatar_image_view)
        val mPrimaryTextView: TextView = itemView.findViewById(R.id.primary_text_view)
        val mMoneyTextView: TextView = itemView.findViewById(R.id.money_text_view)
        val mSecondaryTextView: TextView = itemView.findViewById(R.id.secondary_text_view)
        val mTaxLayout: ViewGroup = itemView.findViewById(R.id.tax_layout)
        val mTaxTextView: TextView = itemView.findViewById(R.id.tax_body_text_view)
        val mAddButton: CardButton = itemView.findViewById(R.id.add_button)
        val mEditButton: CardButton = itemView.findViewById(R.id.edit_button)

        init {
            itemView.setOnClickListener(this)
            mAddButton.setOnClickListener(this)
            mEditButton.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            val listener = mActionListener ?: return
            val cursor = getSafeCursor(adapterPosition) ?: return
            val id = cursor.getLong(mIndexId)
            when (v) {
                mAddButton -> listener.onModelAddClick(id)
                mEditButton -> listener.onModelEditClick(id)
                else -> listener.onModelClick(id)
            }
        }
    }

    interface ActionListener {
        fun onModelClick(id: Long)

        fun onModelAddClick(id: Long)

        fun onModelEditClick(id: Long)
    }
}
