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

import android.content.Context
import android.database.Cursor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.ColorIcon
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.ui.view.MaterialProgressBar
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.MoneyFormatter
import kotlin.math.abs
import kotlin.math.max

/**
 * Created by andrea on 06/03/18.
 */
class BudgetCursorAdapter(
    private val mActionListener: ActionListener?
) : AbstractCursorAdapter<BudgetCursorAdapter.ViewHolder>(null, Contract.Budget.ID) {

    private var mIndexId = -1
    private var mIndexType = -1
    private var mIndexTarget = -1
    private var mIndexEndDate = -1
    private var mIndexCategoryIcon = -1
    private var mIndexCategoryName = -1
    private var mIndexCurrency = -1
    private var mIndexProgress = -1

    private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()

    override fun onLoadColumnIndices(cursor: Cursor) {
        mIndexId = cursor.getColumnIndex(Contract.Budget.ID)
        mIndexType = cursor.getColumnIndex(Contract.Budget.TYPE)
        mIndexTarget = cursor.getColumnIndex(Contract.Budget.MONEY)
        mIndexEndDate = cursor.getColumnIndex(Contract.Budget.END_DATE)
        mIndexCategoryIcon = cursor.getColumnIndex(Contract.Budget.CATEGORY_ICON)
        mIndexCategoryName = cursor.getColumnIndex(Contract.Budget.CATEGORY_NAME)
        mIndexCurrency = cursor.getColumnIndex(Contract.Budget.CURRENCY)
        mIndexProgress = cursor.getColumnIndex(Contract.Budget.PROGRESS)
    }

    override fun onBindViewHolder(holder: ViewHolder, cursor: Cursor) {
        when (Contract.BudgetType.fromValue(cursor.getInt(mIndexType))) {
            Contract.BudgetType.INCOMES -> {
                val incomes = holder.context.getString(R.string.hint_incomes)
                holder.mDescriptionTextView.text = incomes
                IconLoader.loadInto(ColorIcon(COLOR_INCOMES, incomes.take(1)), holder.mAvatarImageView)
            }
            Contract.BudgetType.EXPENSES -> {
                val expenses = holder.context.getString(R.string.hint_expenses)
                holder.mDescriptionTextView.text = expenses
                IconLoader.loadInto(ColorIcon(COLOR_EXPENSES, expenses.take(1)), holder.mAvatarImageView)
            }
            Contract.BudgetType.CATEGORY -> {
                val icon = IconLoader.parse(cursor.getString(mIndexCategoryIcon))
                IconLoader.loadInto(icon, holder.mAvatarImageView)
                holder.mDescriptionTextView.text = cursor.getString(mIndexCategoryName)
            }
            null -> Unit
        }
        val currency = CurrencyManager.getCurrency(cursor.getString(mIndexCurrency))
        val target = cursor.getLong(mIndexTarget)
        val progress = abs(cursor.getLong(mIndexProgress))
        mMoneyFormatter.applyNotTinted(holder.mMoneyTextView, currency, target)
        holder.mProgressBar.setMaxValue(target)
        holder.mProgressBar.setProgressValue(progress)
        val difference = max(target - progress, 0L)
        mMoneyFormatter.applyNotTinted(holder.mUsedTextView, currency, progress)
        mMoneyFormatter.applyNotTinted(holder.mAvailableTextView, currency, difference)
        val context = holder.mDateTextView.context
        val endDate = DateUtils.getDateFromSQLDateString(cursor.getString(mIndexEndDate).orEmpty())
        val formattedEndDate = DateFormatter.getFormattedDate(endDate)
        val dateTextRes = if (DateUtils.isBeforeToday(endDate)) {
            R.string.relative_string_ended_on
        } else {
            R.string.relative_string_will_end_on
        }
        holder.mDateTextView.text = context.getString(dateTextRes, formattedEndDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.adapter_budget_item, parent, false)
        return ViewHolder(itemView)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        internal val mAvatarImageView: ImageView = itemView.findViewById(R.id.avatar_image_view)
        internal val mDescriptionTextView: TextView = itemView.findViewById(R.id.primary_text_view)
        internal val mMoneyTextView: TextView = itemView.findViewById(R.id.money_text_view)
        internal val mProgressBar: MaterialProgressBar = itemView.findViewById(R.id.progress_bar)
        internal val mUsedTextView: TextView = itemView.findViewById(R.id.used_body_text_view)
        internal val mAvailableTextView: TextView = itemView.findViewById(R.id.available_body_text_view)
        internal val mDateTextView: TextView = itemView.findViewById(R.id.date_text_view)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            val listener = mActionListener ?: return
            @Suppress("DEPRECATION")
            val cursor = getSafeCursor(adapterPosition)
            if (cursor != null) {
                listener.onBudgetClick(cursor.getLong(mIndexId))
            }
        }

        internal val context: Context
            get() = itemView.context
    }

    interface ActionListener {

        fun onBudgetClick(id: Long)
    }

    companion object {
        private const val COLOR_INCOMES = "#43A047"
        private const val COLOR_EXPENSES = "#D32F2F"
    }
}
