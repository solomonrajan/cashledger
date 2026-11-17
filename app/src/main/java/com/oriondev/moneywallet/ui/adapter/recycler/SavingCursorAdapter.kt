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
import com.oriondev.moneywallet.ui.view.MaterialProgressBar
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.MoneyFormatter

/**
 * Created by andrea on 05/03/18.
 */
class SavingCursorAdapter(
    private val mActionListener: ActionListener?
) : AbstractCursorAdapter<SavingCursorAdapter.ViewHolder>(null, Contract.Saving.ID) {

    private var mIndexId: Int = -1
    private var mIndexIcon: Int = -1
    private var mIndexDescription: Int = -1
    private var mIndexComplete: Int = -1
    private var mIndexStartMoney: Int = -1
    private var mIndexEndMoney: Int = -1
    private var mIndexCurrency: Int = -1
    private var mIndexProgress: Int = -1

    private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()

    override fun onLoadColumnIndices(cursor: Cursor) {
        mIndexId = cursor.getColumnIndex(Contract.Saving.ID)
        mIndexIcon = cursor.getColumnIndex(Contract.Saving.ICON)
        mIndexDescription = cursor.getColumnIndex(Contract.Saving.DESCRIPTION)
        mIndexComplete = cursor.getColumnIndex(Contract.Saving.COMPLETE)
        mIndexStartMoney = cursor.getColumnIndex(Contract.Saving.START_MONEY)
        mIndexEndMoney = cursor.getColumnIndex(Contract.Saving.END_MONEY)
        mIndexCurrency = cursor.getColumnIndex(Contract.Saving.WALLET_CURRENCY)
        mIndexProgress = cursor.getColumnIndex(Contract.Saving.PROGRESS)
    }

    override fun onBindViewHolder(holder: ViewHolder, cursor: Cursor) {
        val icon = IconLoader.parse(cursor.getString(mIndexIcon))
        IconLoader.loadInto(icon, holder.mAvatarImageView)
        holder.mDescriptionTextView.text = cursor.getString(mIndexDescription)
        val currency = CurrencyManager.getCurrency(cursor.getString(mIndexCurrency))
        val target = cursor.getLong(mIndexEndMoney)
        mMoneyFormatter.applyNotTinted(holder.mMoneyTextView, currency, target)
        if (cursor.getInt(mIndexComplete) == 0) {
            holder.mProgressLayout.visibility = View.VISIBLE
            holder.mButtonLayoutFix.visibility = View.GONE
            holder.mButtonLayout.visibility = View.VISIBLE
            val startMoney = cursor.getLong(mIndexStartMoney)
            val progress = cursor.getLong(mIndexProgress)
            val current = startMoney + progress
            val needed = maxOf(target - current, 0L)
            holder.mProgressBar.setMaxValue(target)
            holder.mProgressBar.setProgressValue(current)
            mMoneyFormatter.applyNotTinted(holder.mCurrentMoneyTextView, currency, current)
            mMoneyFormatter.applyNotTinted(holder.mNeededMoneyTextView, currency, needed)
            holder.mWithdrawEverythingButton.visibility = if (needed == 0L) View.VISIBLE else View.GONE
            holder.mWithdrawButton.visibility = if (needed == 0L) View.GONE else View.VISIBLE
            holder.mDepositButton.visibility = if (needed == 0L) View.GONE else View.VISIBLE
        } else {
            holder.mProgressLayout.visibility = View.GONE
            holder.mButtonLayoutFix.visibility = View.VISIBLE
            holder.mButtonLayout.visibility = View.GONE
            holder.mProgressBar.setMaxValue(target)
            holder.mProgressBar.setProgressValue(target)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.adapter_saving_item, parent, false)
        return ViewHolder(itemView)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        val mAvatarImageView: ImageView = itemView.findViewById(R.id.avatar_image_view)
        val mDescriptionTextView: TextView = itemView.findViewById(R.id.primary_text_view)
        val mMoneyTextView: TextView = itemView.findViewById(R.id.money_text_view)
        val mProgressBar: MaterialProgressBar = itemView.findViewById(R.id.progress_bar)
        val mProgressLayout: View = itemView.findViewById(R.id.progress_layout)
        val mCurrentMoneyTextView: TextView = itemView.findViewById(R.id.current_body_text_view)
        val mNeededMoneyTextView: TextView = itemView.findViewById(R.id.needed_body_text_view)
        val mButtonLayoutFix: View = itemView.findViewById(R.id.button_bar_empty_fix)
        val mButtonLayout: ViewGroup = itemView.findViewById(R.id.button_bar_layout)
        val mWithdrawEverythingButton: CardButton = itemView.findViewById(R.id.withdraw_everything_button)
        val mWithdrawButton: CardButton = itemView.findViewById(R.id.withdraw_button)
        val mDepositButton: CardButton = itemView.findViewById(R.id.deposit_button)

        init {
            itemView.setOnClickListener(this)
            mWithdrawEverythingButton.setOnClickListener(this)
            mWithdrawButton.setOnClickListener(this)
            mDepositButton.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            val listener = mActionListener ?: return
            val cursor = getSafeCursor(adapterPosition) ?: return
            val savingId = cursor.getLong(mIndexId)
            when (v) {
                itemView -> listener.onSavingClick(savingId)
                mWithdrawEverythingButton -> listener.onWithdrawEverything(savingId)
                mWithdrawButton -> listener.onWithdraw(savingId)
                mDepositButton -> listener.onDeposit(savingId)
            }
        }
    }

    interface ActionListener {
        fun onSavingClick(id: Long)

        fun onWithdrawEverything(id: Long)

        fun onWithdraw(id: Long)

        fun onDeposit(id: Long)
    }
}
