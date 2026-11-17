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
import com.oriondev.moneywallet.model.Money
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.wrapper.DebtHeaderCursor
import com.oriondev.moneywallet.ui.view.CardButton
import com.oriondev.moneywallet.ui.view.MaterialProgressBar
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.MoneyFormatter
import kotlin.math.abs

/**
 * Created by andrea on 05/03/18.
 */
class DebtCursorAdapter(
    private val mActionListener: ActionListener?
) : AbstractCursorAdapter<RecyclerView.ViewHolder>(null, Contract.Debt.ID) {

    private var mIndexType = -1
    private var mIndexHeaderType = -1
    private var mIndexHeaderMoney = -1
    private var mIndexId = -1
    private var mIndexDebtType = -1
    private var mIndexIcon = -1
    private var mIndexDescription = -1
    private var mIndexMoney = -1
    private var mIndexProgress = -1
    private var mIndexCurrency = -1
    private var mIndexExpirationDate = -1
    private var mIndexArchived = -1
    private var mIndexPlaceId = -1
    private var mIndexPlaceName = -1

    private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()

    override fun onLoadColumnIndices(cursor: Cursor) {
        mIndexType = cursor.getColumnIndex(DebtHeaderCursor.COLUMN_ITEM_TYPE)
        mIndexHeaderType = cursor.getColumnIndex(DebtHeaderCursor.COLUMN_HEADER_TYPE)
        mIndexHeaderMoney = cursor.getColumnIndex(DebtHeaderCursor.COLUMN_HEADER_MONEY)
        mIndexId = cursor.getColumnIndex(Contract.Debt.ID)
        mIndexDebtType = cursor.getColumnIndex(Contract.Debt.TYPE)
        mIndexIcon = cursor.getColumnIndex(Contract.Debt.ICON)
        mIndexDescription = cursor.getColumnIndex(Contract.Debt.DESCRIPTION)
        mIndexMoney = cursor.getColumnIndex(Contract.Debt.MONEY)
        mIndexProgress = cursor.getColumnIndex(Contract.Debt.PROGRESS)
        mIndexCurrency = cursor.getColumnIndex(Contract.Debt.WALLET_CURRENCY)
        mIndexExpirationDate = cursor.getColumnIndex(Contract.Debt.EXPIRATION_DATE)
        mIndexArchived = cursor.getColumnIndex(Contract.Debt.ARCHIVED)
        mIndexPlaceId = cursor.getColumnIndex(Contract.Debt.PLACE_ID)
        mIndexPlaceName = cursor.getColumnIndex(Contract.Debt.PLACE_NAME)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, cursor: Cursor) {
        when (holder) {
            is HeaderViewHolder -> onBindHeaderViewHolder(holder, cursor)
            is DebtViewHolder -> onBindItemViewHolder(holder, cursor)
        }
    }

    private fun onBindHeaderViewHolder(holder: HeaderViewHolder, cursor: Cursor) {
        when (cursor.getInt(mIndexHeaderType)) {
            DebtHeaderCursor.HEADER_CURRENT -> {
                val debtType = Contract.DebtType.fromValue(cursor.getInt(mIndexDebtType))
                val money = Money.parse(cursor.getString(mIndexHeaderMoney))
                when (debtType) {
                    Contract.DebtType.DEBT -> {
                        holder.mLeftTextView.setText(R.string.header_debts_to_pay)
                        mMoneyFormatter.applyTintedExpense(holder.mRightTextView, money)
                    }
                    Contract.DebtType.CREDIT -> {
                        holder.mLeftTextView.setText(R.string.header_debts_to_receive)
                        mMoneyFormatter.applyTintedIncome(holder.mRightTextView, money)
                    }
                    null -> Unit
                }
            }
            DebtHeaderCursor.HEADER_ARCHIVED -> {
                // the group holds debts archived by hand, which may still have money outstanding,
                // and debts simply paid off in full. Neither Archived nor Completed is true of
                // both, so it is named for what they share: no longer needing attention.
                holder.mLeftTextView.setText(R.string.header_debts_finished)
                holder.mRightTextView.text = null
            }
        }
    }

    private fun onBindItemViewHolder(holder: DebtViewHolder, cursor: Cursor) {
        val icon = IconLoader.parse(cursor.getString(mIndexIcon))
        IconLoader.loadInto(icon, holder.mAvatarImageView)
        holder.mDescriptionTextView.text = cursor.getString(mIndexDescription)
        val currency = CurrencyManager.getCurrency(cursor.getString(mIndexCurrency))
        val target = cursor.getLong(mIndexMoney)
        val progress = abs(cursor.getLong(mIndexProgress))
        val debtType = Contract.DebtType.fromValue(cursor.getInt(mIndexDebtType))
        // the same test that decides between the figure left to settle and completed below, so
        // settling in full is offered exactly when the card shows an amount to settle
        val inFullVisibility = if (progress < target) View.VISIBLE else View.GONE
        when (debtType) {
            Contract.DebtType.DEBT -> {
                mMoneyFormatter.applyTintedExpense(holder.mMoneyTextView, currency, target)
                holder.mPayButton.visibility = View.VISIBLE
                holder.mReceiveButton.visibility = View.GONE
                holder.mPayInFullButton.visibility = inFullVisibility
                holder.mReceiveInFullButton.visibility = View.GONE
            }
            Contract.DebtType.CREDIT -> {
                mMoneyFormatter.applyTintedIncome(holder.mMoneyTextView, currency, target)
                holder.mPayButton.visibility = View.GONE
                holder.mReceiveButton.visibility = View.VISIBLE
                holder.mPayInFullButton.visibility = View.GONE
                holder.mReceiveInFullButton.visibility = inFullVisibility
            }
            null -> Unit
        }
        holder.mProgressBar.setMaxValue(target)
        holder.mProgressBar.setProgressValue(progress)
        if (progress < target) {
            val context = holder.mProgressTextView.context
            val remaining = abs(target - progress)
            val differenceText = mMoneyFormatter.getNotTintedString(currency, remaining)
            val progressText = context.getString(R.string.relative_string_money_to_settle_debt, differenceText)
            holder.mProgressTextView.text = progressText
        } else {
            holder.mProgressTextView.setText(R.string.relative_string_debt_completed)
        }
        if (cursor.isNull(mIndexExpirationDate)) {
            holder.mRemainingDaysTextView.visibility = View.GONE
        } else {
            holder.mRemainingDaysTextView.visibility = View.VISIBLE
            val context = holder.mRemainingDaysTextView.context
            val date = DateUtils.getDateFromSQLDateString(cursor.getString(mIndexExpirationDate).orEmpty())
            val formatted = DateFormatter.getFormattedDate(date)
            val expiryTextRes = if (date.time <= System.currentTimeMillis()) {
                R.string.relative_string_ended_on
            } else {
                R.string.relative_string_will_expire_on
            }
            holder.mRemainingDaysTextView.text = context.getString(expiryTextRes, formatted)
        }
        if (cursor.isNull(mIndexPlaceId)) {
            holder.mPlaceTextView.visibility = View.GONE
        } else {
            holder.mPlaceTextView.visibility = View.VISIBLE
            holder.mPlaceTextView.text = cursor.getString(mIndexPlaceName)
        }
        if (cursor.getInt(mIndexArchived) == 1) {
            holder.mButtonLayoutFix.visibility = View.VISIBLE
            holder.mButtonLayout.visibility = View.GONE
        } else {
            holder.mButtonLayoutFix.visibility = View.GONE
            holder.mButtonLayout.visibility = View.VISIBLE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            DebtHeaderCursor.TYPE_HEADER ->
                HeaderViewHolder(inflater.inflate(R.layout.adapter_header_item, parent, false))
            DebtHeaderCursor.TYPE_ITEM ->
                DebtViewHolder(inflater.inflate(R.layout.adapter_debt_item, parent, false))
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    override fun getItemViewType(position: Int): Int {
        val cursor = getSafeCursor(position)
            ?: throw IllegalStateException("couldn't move cursor to position: $position")
        return cursor.getInt(mIndexType)
    }

    internal class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        internal val mLeftTextView: TextView = itemView.findViewById(R.id.left_text_view)
        internal val mRightTextView: TextView = itemView.findViewById(R.id.right_text_view)
    }

    inner class DebtViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        internal val mAvatarImageView: ImageView = itemView.findViewById(R.id.avatar_image_view)
        internal val mDescriptionTextView: TextView = itemView.findViewById(R.id.primary_text_view)
        internal val mMoneyTextView: TextView = itemView.findViewById(R.id.money_text_view)
        internal val mProgressBar: MaterialProgressBar = itemView.findViewById(R.id.progress_bar)
        internal val mProgressTextView: TextView = itemView.findViewById(R.id.money_progress_text_view)
        internal val mRemainingDaysTextView: TextView = itemView.findViewById(R.id.remaining_days_text_view)
        internal val mPlaceTextView: TextView = itemView.findViewById(R.id.place_text_view)
        internal val mButtonLayoutFix: View = itemView.findViewById(R.id.button_bar_empty_fix)
        internal val mButtonLayout: ViewGroup = itemView.findViewById(R.id.button_bar_layout)
        internal val mPayButton: CardButton = itemView.findViewById(R.id.pay_button)
        internal val mReceiveButton: CardButton = itemView.findViewById(R.id.receive_button)
        internal val mPayInFullButton: CardButton = itemView.findViewById(R.id.pay_in_full_button)
        internal val mReceiveInFullButton: CardButton = itemView.findViewById(R.id.receive_in_full_button)

        init {
            itemView.setOnClickListener(this)
            mPayButton.setOnClickListener(this)
            mReceiveButton.setOnClickListener(this)
            mPayInFullButton.setOnClickListener(this)
            mReceiveInFullButton.setOnClickListener(this)
        }

        override fun onClick(view: View) {
            val listener = mActionListener ?: return
            @Suppress("DEPRECATION")
            val cursor = getSafeCursor(adapterPosition) ?: return
            val id = cursor.getLong(mIndexId)
            when (view) {
                mPayButton -> listener.onPayClick(id)
                mReceiveButton -> listener.onReceiveClick(id)
                mPayInFullButton -> listener.onPayInFullClick(id)
                mReceiveInFullButton -> listener.onReceiveInFullClick(id)
                else -> listener.onDebtClick(id)
            }
        }
    }

    interface ActionListener {

        fun onDebtClick(id: Long)

        fun onPayClick(id: Long)

        fun onReceiveClick(id: Long)

        fun onPayInFullClick(id: Long)

        fun onReceiveInFullClick(id: Long)
    }
}
