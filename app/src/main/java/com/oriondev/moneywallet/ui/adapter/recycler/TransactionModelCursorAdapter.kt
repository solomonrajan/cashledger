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
class TransactionModelCursorAdapter(
    private val mActionListener: ActionListener?
) : AbstractCursorAdapter<TransactionModelCursorAdapter.TransactionModelViewHolder>(
    null,
    Contract.TransactionModel.ID
) {

    private var mIndexId: Int = -1
    private var mIndexCategoryName: Int = -1
    private var mIndexCategoryIcon: Int = -1
    private var mIndexWalletCurrency: Int = -1
    private var mIndexDirection: Int = -1
    private var mIndexMoney: Int = -1
    private var mIndexDescription: Int = -1

    private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()

    override fun onLoadColumnIndices(cursor: Cursor) {
        mIndexId = cursor.getColumnIndex(Contract.TransactionModel.ID)
        mIndexCategoryName = cursor.getColumnIndex(Contract.TransactionModel.CATEGORY_NAME)
        mIndexCategoryIcon = cursor.getColumnIndex(Contract.TransactionModel.CATEGORY_ICON)
        mIndexWalletCurrency = cursor.getColumnIndex(Contract.TransactionModel.WALLET_CURRENCY)
        mIndexDirection = cursor.getColumnIndex(Contract.TransactionModel.DIRECTION)
        mIndexMoney = cursor.getColumnIndex(Contract.TransactionModel.MONEY)
        mIndexDescription = cursor.getColumnIndex(Contract.TransactionModel.DESCRIPTION)
    }

    override fun onBindViewHolder(holder: TransactionModelViewHolder, cursor: Cursor) {
        val icon = IconLoader.parse(cursor.getString(mIndexCategoryIcon))
        IconLoader.loadInto(icon, holder.mAvatarImageView)
        val currency = CurrencyManager.getCurrency(cursor.getString(mIndexWalletCurrency))
        val money = cursor.getLong(mIndexMoney)
        if (cursor.getInt(mIndexDirection) == Contract.Direction.INCOME) {
            mMoneyFormatter.applyTintedIncome(holder.mMoneyTextView, currency, money)
        } else {
            mMoneyFormatter.applyTintedExpense(holder.mMoneyTextView, currency, money)
        }
        val description = cursor.getString(mIndexDescription)
        if (description.isNullOrEmpty()) {
            holder.mPrimaryTextView.text = cursor.getString(mIndexCategoryName)
            holder.mSecondaryTextView.text = description
        } else {
            holder.mPrimaryTextView.text = description.trim()
            holder.mSecondaryTextView.text = cursor.getString(mIndexCategoryName)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionModelViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.adapter_model_transaction_item, parent, false)
        return TransactionModelViewHolder(itemView)
    }

    inner class TransactionModelViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        val mAvatarImageView: ImageView = itemView.findViewById(R.id.avatar_image_view)
        val mPrimaryTextView: TextView = itemView.findViewById(R.id.primary_text_view)
        val mMoneyTextView: TextView = itemView.findViewById(R.id.money_text_view)
        val mSecondaryTextView: TextView = itemView.findViewById(R.id.secondary_text_view)
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
