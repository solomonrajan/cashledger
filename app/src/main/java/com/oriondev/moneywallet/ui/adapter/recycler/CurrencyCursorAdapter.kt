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
import com.bumptech.glide.Glide
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.storage.database.Contract
import java.util.Locale

/**
 * Created by andrea on 09/02/18.
 */
class CurrencyCursorAdapter(
    private val mListener: CurrencyActionListener?
) : AbstractCursorAdapter<CurrencyCursorAdapter.CurrencyViewHolder>(null, Contract.Currency.ISO) {

    private var mIndexIso = -1
    private var mIndexName = -1
    private var mIndexSymbol = -1
    private var mIndexFavourite = -1

    override fun onLoadColumnIndices(cursor: Cursor) {
        mIndexIso = cursor.getColumnIndex(Contract.Currency.ISO)
        mIndexName = cursor.getColumnIndex(Contract.Currency.NAME)
        mIndexSymbol = cursor.getColumnIndex(Contract.Currency.SYMBOL)
        mIndexFavourite = cursor.getColumnIndex(Contract.Currency.FAVOURITE)
    }

    override fun onBindViewHolder(holder: CurrencyViewHolder, cursor: Cursor) {
        val iso = cursor.getString(mIndexIso).orEmpty()
        val name = cursor.getString(mIndexName)
        val symbol = cursor.getString(mIndexSymbol)
        val context = holder.mIconView.context
        val currencyFlag = CurrencyUnit.getCurrencyFlag(context, iso)
        if (currencyFlag > 0) {
            holder.mIconView.setImageDrawable(null)
            Glide.with(holder.mIconView)
                .load(currencyFlag)
                .into(holder.mIconView)
        } else {
            holder.mIconView.setImageDrawable(CurrencyUnit.getCurrencyDrawable(iso))
        }
        holder.mNameView.text = name
        holder.mSymbolView.text = if (!symbol.isNullOrEmpty()) {
            String.format(Locale.ENGLISH, "%s (%s)", iso, symbol)
        } else {
            iso
        }
        val favorite = cursor.getInt(mIndexFavourite) == 1
        holder.mFavoriteView.setImageResource(
            if (favorite) R.drawable.ic_star_black_24dp else R.drawable.ic_star_border_black_24dp
        )
        holder.mFavoriteView.contentDescription = holder.itemView.context.getString(
            if (favorite) R.string.description_currency_unpin
            else R.string.description_currency_favorite,
            name
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CurrencyViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.adapter_currency_item, parent, false)
        return CurrencyViewHolder(itemView)
    }

    inner class CurrencyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        internal val mIconView: ImageView = itemView.findViewById(R.id.icon_image_view)
        internal val mNameView: TextView = itemView.findViewById(R.id.name_text_view)
        internal val mSymbolView: TextView = itemView.findViewById(R.id.secondary_text_view)
        internal val mFavoriteView: ImageView = itemView.findViewById(R.id.favorite_image_view)

        init {
            itemView.setOnClickListener(this)
            // Its own listener, because a tap on the row still means what it always did:
            // pick this currency, or open it for editing.
            mFavoriteView.setOnClickListener {
                @Suppress("DEPRECATION")
                val cursor = getSafeCursor(adapterPosition)
                val listener = mListener
                if (cursor != null && listener != null) {
                    val iso = cursor.getString(mIndexIso).orEmpty()
                    val favourite = cursor.getInt(mIndexFavourite) == 1
                    listener.onCurrencyFavourite(iso, !favourite)
                }
            }
        }

        override fun onClick(v: View) {
            val listener = mListener ?: return
            @Suppress("DEPRECATION")
            val cursor = getSafeCursor(adapterPosition)
            if (cursor != null) {
                listener.onCurrencyClick(cursor.getString(mIndexIso).orEmpty())
            }
        }
    }

    interface CurrencyActionListener {

        fun onCurrencyClick(iso: String)

        fun onCurrencyFavourite(iso: String, newValue: Boolean)
    }
}
