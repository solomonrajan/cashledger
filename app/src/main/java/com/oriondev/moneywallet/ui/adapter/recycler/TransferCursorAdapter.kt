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
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.wrapper.TransferHeaderCursor
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.MoneyFormatter
import java.util.Locale

private const val HEADER_BACKGROUND_ALPHA = 26

internal fun formatTransferRoute(walletFrom: String?, walletTo: String?): String {
    return String.format(Locale.ENGLISH, "%s -> %s", walletFrom, walletTo)
}

internal fun applyHeaderTintBackground(itemView: View, includeRipple: Boolean) {
    val primary = ThemeEngine.getTheme().colorPrimary
    val tintColor = Color.argb(
        HEADER_BACKGROUND_ALPHA,
        Color.red(primary),
        Color.green(primary),
        Color.blue(primary)
    )
    val tintDrawable = ColorDrawable(tintColor)
    val ripple: Drawable? = if (includeRipple) itemView.background else null
    itemView.background = if (ripple != null) {
        LayerDrawable(arrayOf(tintDrawable, ripple))
    } else {
        tintDrawable
    }
}

/**
 * Created by andrea on 03/03/18.
 */
class TransferCursorAdapter(
    private val mActionListener: ActionListener?
) : AbstractCursorAdapter<RecyclerView.ViewHolder>(null, Contract.Transfer.ID) {

    private var mIndexType: Int = -1
    private var mIndexHeaderStartDate: Int = -1
    private var mIndexHeaderEndDate: Int = -1
    private var mIndexTransferId: Int = -1
    private var mIndexWalletFromName: Int = -1
    private var mIndexWalletFromCurrency: Int = -1
    private var mIndexWalletToName: Int = -1
    private var mIndexWalletToIcon: Int = -1
    private var mIndexMoneyFrom: Int = -1
    private var mIndexDate: Int = -1
    private var mIndexDescription: Int = -1

    private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()

    override fun onLoadColumnIndices(cursor: Cursor) {
        mIndexType = cursor.getColumnIndex(TransferHeaderCursor.COLUMN_ITEM_TYPE)
        mIndexHeaderStartDate = cursor.getColumnIndex(TransferHeaderCursor.COLUMN_HEADER_START_DATE)
        mIndexHeaderEndDate = cursor.getColumnIndex(TransferHeaderCursor.COLUMN_HEADER_END_DATE)
        mIndexTransferId = cursor.getColumnIndex(Contract.Transfer.ID)
        mIndexWalletFromName = cursor.getColumnIndex(Contract.Transfer.TRANSACTION_FROM_WALLET_NAME)
        mIndexWalletFromCurrency = cursor.getColumnIndex(Contract.Transfer.TRANSACTION_FROM_WALLET_CURRENCY)
        mIndexWalletToName = cursor.getColumnIndex(Contract.Transfer.TRANSACTION_TO_WALLET_NAME)
        mIndexWalletToIcon = cursor.getColumnIndex(Contract.Transfer.TRANSACTION_TO_WALLET_ICON)
        mIndexMoneyFrom = cursor.getColumnIndex(Contract.Transfer.TRANSACTION_FROM_MONEY)
        mIndexDate = cursor.getColumnIndex(Contract.Transfer.DATE)
        mIndexDescription = cursor.getColumnIndex(Contract.Transfer.DESCRIPTION)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, cursor: Cursor) {
        when (holder) {
            is HeaderViewHolder -> onBindHeaderViewHolder(holder, cursor)
            is TransferViewHolder -> onBindItemViewHolder(holder, cursor)
        }
    }

    private fun onBindItemViewHolder(holder: TransferViewHolder, cursor: Cursor) {
        val icon = IconLoader.parse(cursor.getString(mIndexWalletToIcon))
        IconLoader.loadInto(icon, holder.mAvatarImageView)
        val walletFrom = cursor.getString(mIndexWalletFromName)
        val walletTo = cursor.getString(mIndexWalletToName)
        holder.mPrimaryTextView.text = formatTransferRoute(walletFrom, walletTo)
        holder.mSecondaryTextView.text = cursor.getString(mIndexDescription)
        val currency = CurrencyManager.getCurrency(cursor.getString(mIndexWalletFromCurrency))
        val money = cursor.getLong(mIndexMoneyFrom)
        mMoneyFormatter.applyNotTinted(holder.mMoneyTextView, currency, money)
        val date = DateUtils.getDateFromSQLDateTimeString(cursor.getString(mIndexDate))
        DateFormatter.applyDate(holder.mDateTextView, date)
    }

    private fun onBindHeaderViewHolder(holder: HeaderViewHolder, cursor: Cursor) {
        val start = DateUtils.getDateFromSQLDateTimeString(cursor.getString(mIndexHeaderStartDate))
        val end = DateUtils.getDateFromSQLDateTimeString(cursor.getString(mIndexHeaderEndDate))
        DateFormatter.applyDateRange(holder.mLeftTextView, start, end)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TransferHeaderCursor.TYPE_HEADER -> {
                val itemView = inflater.inflate(R.layout.adapter_header_item, parent, false)
                HeaderViewHolder(itemView)
            }
            TransferHeaderCursor.TYPE_ITEM -> {
                val itemView = inflater.inflate(R.layout.adapter_transfer_item, parent, false)
                TransferViewHolder(itemView)
            }
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    override fun getItemViewType(position: Int): Int {
        val cursor = getSafeCursor(position) ?: return TransferHeaderCursor.TYPE_ITEM
        return cursor.getInt(mIndexType)
    }

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        val mLeftTextView: TextView = itemView.findViewById(R.id.left_text_view)

        init {
            applyHeaderTintBackground(itemView, includeRipple = true)
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            mActionListener?.onHeaderClick()
        }
    }

    inner class TransferViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

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
            listener.onTransferClick(cursor.getLong(mIndexTransferId))
        }
    }

    interface ActionListener {
        fun onHeaderClick()

        fun onTransferClick(id: Long)
    }
}
