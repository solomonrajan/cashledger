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
class RecurrentTransactionCursorAdapter(
    private val mActionListener: ActionListener?
) : AbstractCursorAdapter<RecurrentTransactionCursorAdapter.RecurrentTransactionViewHolder>(
    null,
    Contract.RecurrentTransaction.ID
) {

    private var mIndexId: Int = -1
    private var mIndexCategoryName: Int = -1
    private var mIndexCategoryIcon: Int = -1
    private var mIndexWalletCurrency: Int = -1
    private var mIndexDirection: Int = -1
    private var mIndexMoney: Int = -1
    private var mIndexDescription: Int = -1
    private var mIndexNextOccurrence: Int = -1

    private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()

    override fun onLoadColumnIndices(cursor: Cursor) {
        mIndexId = cursor.getColumnIndex(Contract.RecurrentTransaction.ID)
        mIndexCategoryName = cursor.getColumnIndex(Contract.RecurrentTransaction.CATEGORY_NAME)
        mIndexCategoryIcon = cursor.getColumnIndex(Contract.RecurrentTransaction.CATEGORY_ICON)
        mIndexWalletCurrency = cursor.getColumnIndex(Contract.RecurrentTransaction.WALLET_CURRENCY)
        mIndexDirection = cursor.getColumnIndex(Contract.RecurrentTransaction.DIRECTION)
        mIndexMoney = cursor.getColumnIndex(Contract.RecurrentTransaction.MONEY)
        mIndexDescription = cursor.getColumnIndex(Contract.RecurrentTransaction.DESCRIPTION)
        mIndexNextOccurrence = cursor.getColumnIndex(Contract.RecurrentTransaction.NEXT_OCCURRENCE)
    }

    override fun onBindViewHolder(holder: RecurrentTransactionViewHolder, cursor: Cursor) {
        val icon = IconLoader.parse(cursor.getString(mIndexCategoryIcon))
        IconLoader.loadInto(icon, holder.mAvatarImageView)
        holder.mPrimaryTextView.text = cursor.getString(mIndexCategoryName)
        val currency = CurrencyManager.getCurrency(cursor.getString(mIndexWalletCurrency))
        val money = cursor.getLong(mIndexMoney)
        if (cursor.getInt(mIndexDirection) == Contract.Direction.INCOME) {
            mMoneyFormatter.applyTintedIncome(holder.mMoneyTextView, currency, money)
        } else {
            mMoneyFormatter.applyTintedExpense(holder.mMoneyTextView, currency, money)
        }
        holder.mSecondaryTextView.text = cursor.getString(mIndexDescription)
        if (cursor.isNull(mIndexNextOccurrence)) {
            holder.mDateTextView.setText(R.string.hint_recurrence_finished)
        } else {
            val nextOccurrence = DateUtils.getDateFromSQLDateString(cursor.getString(mIndexNextOccurrence))
            DateFormatter.applyDate(holder.mDateTextView, nextOccurrence)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecurrentTransactionViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.adapter_transaction_item, parent, false)
        return RecurrentTransactionViewHolder(itemView)
    }

    inner class RecurrentTransactionViewHolder(
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
            listener.onRecurrentTransactionClick(cursor.getLong(mIndexId))
        }
    }

    interface ActionListener {
        fun onRecurrentTransactionClick(id: Long)
    }
}
