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
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.model.Money
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.storage.wrapper.AbstractHeaderCursor
import com.oriondev.moneywallet.storage.wrapper.TransactionHeaderCursor
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.MoneyFormatter
import java.util.Date

/**
 * Created by andrea on 03/03/18.
 */
class TransactionCursorAdapter @JvmOverloads constructor(
    private val mActionListener: ActionListener?,
    /**
     * Three of the four screens using this adapter answer a header click with an empty method, so
     * the arrow and the click both wait to be asked for. A screen that shows the arrow has to
     * open something from onHeaderClick, and one that does not must not show it.
     */
    private val mHeaderOpensReport: Boolean = false
) : AbstractCursorAdapter<RecyclerView.ViewHolder>(null, Contract.Transaction.ID) {

    private var mIndexType: Int = -1
    private var mIndexHeaderStartDate: Int = -1
    private var mIndexHeaderEndDate: Int = -1
    private var mIndexHeaderMoney: Int = -1
    private var mIndexHeaderIncome: Int = -1
    private var mIndexHeaderExpense: Int = -1
    private var mIndexHeaderGroupType: Int = -1
    private var mIndexCategoryName: Int = -1
    private var mIndexCategoryIcon: Int = -1
    private var mIndexTransactionId: Int = -1
    private var mIndexTransactionType: Int = -1
    private var mIndexTransactionDirection: Int = -1
    private var mIndexTransactionDescription: Int = -1
    private var mIndexTransactionDate: Int = -1
    private var mIndexTransactionMoney: Int = -1
    private var mIndexCurrency: Int = -1

    private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()

    /**
     * Cursor row of each row on screen, in order. Hiding a period's transactions leaves their
     * cursor rows out of this list, so every position this adapter is asked about is read
     * through it.
     */
    private val mVisibleRows: MutableList<Int> = ArrayList()

    /**
     * Keys of the periods whose transactions are hidden, as of the last rebuild. Read from the
     * preference every time instead of kept, because the two lists that draw headers with this
     * adapter, the transactions list and a filtered transactions list, share one stored set and
     * are alive at once, since the filtered list opens over the main one. An adapter that
     * trusted its own copy would write the others' hiding away.
     */
    private val mCollapsedPeriods: MutableSet<String> = HashSet()

    private val mSelectedIds: MutableSet<Long> = HashSet()

    override fun onLoadColumnIndices(cursor: Cursor) {
        mIndexType = cursor.getColumnIndex(TransactionHeaderCursor.COLUMN_ITEM_TYPE)
        mIndexHeaderStartDate = cursor.getColumnIndex(TransactionHeaderCursor.COLUMN_HEADER_START_DATE)
        mIndexHeaderEndDate = cursor.getColumnIndex(TransactionHeaderCursor.COLUMN_HEADER_END_DATE)
        mIndexHeaderMoney = cursor.getColumnIndex(TransactionHeaderCursor.COLUMN_HEADER_MONEY)
        mIndexHeaderIncome = cursor.getColumnIndex(TransactionHeaderCursor.COLUMN_HEADER_INCOME)
        mIndexHeaderExpense = cursor.getColumnIndex(TransactionHeaderCursor.COLUMN_HEADER_EXPENSE)
        mIndexHeaderGroupType = cursor.getColumnIndex(TransactionHeaderCursor.COLUMN_HEADER_GROUP_TYPE)
        mIndexCategoryName = cursor.getColumnIndex(Contract.Transaction.CATEGORY_NAME)
        mIndexCategoryIcon = cursor.getColumnIndex(Contract.Transaction.CATEGORY_ICON)
        mIndexTransactionId = cursor.getColumnIndex(Contract.Transaction.ID)
        mIndexTransactionType = cursor.getColumnIndex(Contract.Transaction.TYPE)
        mIndexTransactionDirection = cursor.getColumnIndex(Contract.Transaction.DIRECTION)
        mIndexTransactionDescription = cursor.getColumnIndex(Contract.Transaction.DESCRIPTION)
        mIndexTransactionDate = cursor.getColumnIndex(Contract.Transaction.DATE)
        mIndexTransactionMoney = cursor.getColumnIndex(Contract.Transaction.MONEY)
        mIndexCurrency = cursor.getColumnIndex(Contract.Transaction.WALLET_CURRENCY)
        rebuildVisibleRows()
        dropSelectedIdsNotOnScreen()
    }

    // ponytail: pages every row on screen on the main thread, but only while something is selected
    private fun dropSelectedIdsNotOnScreen() {
        // no data yet means a restored selection still waits for its first load
        if (mSelectedIds.isEmpty() || !isDataValid()) {
            return
        }
        val onScreen: MutableSet<Long> = HashSet()
        for (position in 0 until itemCount) {
            val cursorPosition = cursorPosition(position)
            if (!isItemAt(cursorPosition)) {
                continue
            }
            val cursor = getSafeCursor(cursorPosition)
            if (cursor != null) {
                onScreen.add(cursor.getLong(mIndexTransactionId))
            }
        }
        if (mSelectedIds.retainAll(onScreen) && mActionListener != null) {
            mActionListener.onSelectionChanged(mSelectedIds.size)
        }
    }

    private fun isItemAt(cursorPosition: Int): Boolean {
        if (mIndexType == -1) {
            return true
        }
        val headerCursor = getCursor() as? AbstractHeaderCursor<*> ?: return true
        return !headerCursor.isHeaderAt(cursorPosition)
    }

    private fun isSelectable(cursor: Cursor): Boolean {
        // a transfer leg is refused by the database, so it is refused here before it can be picked
        return cursor.getInt(mIndexTransactionType) != Contract.TransactionType.TRANSFER
    }

    fun getSelectedIds(): LongArray = mSelectedIds.toLongArray()

    fun setSelectedIds(ids: LongArray?) {
        mSelectedIds.clear()
        if (ids != null) {
            for (id in ids) {
                mSelectedIds.add(id)
            }
        }
        if (getCursor() != null) {
            dropSelectedIdsNotOnScreen()
        }
        notifySelectionChanged()
    }

    fun clearSelection() {
        if (mSelectedIds.isNotEmpty()) {
            mSelectedIds.clear()
            notifySelectionChanged()
        }
    }

    fun selectAll() {
        for (position in 0 until itemCount) {
            val cursorPosition = cursorPosition(position)
            if (!isItemAt(cursorPosition)) {
                continue
            }
            val cursor = getSafeCursor(cursorPosition)
            if (cursor != null && isSelectable(cursor)) {
                mSelectedIds.add(cursor.getLong(mIndexTransactionId))
            }
        }
        notifySelectionChanged()
    }

    private fun notifySelectionChanged() {
        notifyDataSetChanged()
        mActionListener?.onSelectionChanged(mSelectedIds.size)
    }

    private fun rebuildVisibleRows() {
        mVisibleRows.clear()
        mCollapsedPeriods.clear()
        mCollapsedPeriods.addAll(PreferenceManager.getCollapsedPeriods())
        val cursor = getCursor() ?: return
        if (mIndexType == -1) {
            return
        }
        // Moving the wrapper to an item row moves the SQLite cursor under it, so asking every row
        // what it is paged the whole result set on the main thread on every load. The header rows
        // are the only ones this walk reads, and the wrapper names them without being moved.
        val headerCursor = cursor as? AbstractHeaderCursor<*> ?: return
        var headerIsCollapsed = false
        for (position in 0 until cursor.count) {
            if (headerCursor.isHeaderAt(position)) {
                cursor.moveToPosition(position)
                headerIsCollapsed = mCollapsedPeriods.contains(
                    periodKey(
                        cursor.getInt(mIndexHeaderGroupType),
                        cursor.getString(mIndexHeaderStartDate)
                    )
                )
                mVisibleRows.add(position)
            } else if (!headerIsCollapsed) {
                mVisibleRows.add(position)
            }
        }
    }

    /**
     * Draws the rows again against what is stored now. The list this adapter is in is not the
     * only one reading that store, so a list coming back to the front has to ask again instead
     * of drawing what it last built. A set that has not changed is left alone, because the main
     * list calls this every time it resumes, and rebuilding walks the whole cursor and rebinds
     * every row on the main thread. Without that, every return from a transaction, the report or
     * the settings paid for a fold nobody had made.
     */
    fun reloadCollapsedPeriods() {
        if (PreferenceManager.getCollapsedPeriods() == mCollapsedPeriods) {
            return
        }
        rebuildVisibleRows()
        dropSelectedIdsNotOnScreen()
        notifyDataSetChanged()
    }

    /* package-local -> public for tests */
    fun togglePeriod(key: String) {
        // Read, change, write, so that the other list this adapter shares the stored set with
        // keeps its own hiding.
        val stored: MutableSet<String> = HashSet(PreferenceManager.getCollapsedPeriods())
        if (!stored.remove(key)) {
            stored.add(key)
        }
        PreferenceManager.setCollapsedPeriods(stored)
        rebuildVisibleRows()
        dropSelectedIdsNotOnScreen()
        notifyDataSetChanged()
    }

    /**
     * The cursor row an on screen position stands for, or -1 when there is none. Everything that
     * takes an on screen position goes through this, and so does every read of the cursor from a
     * click.
     */
    private fun cursorPosition(position: Int): Int {
        if (mIndexType == -1) {
            // The calendar and the search results hand this adapter a plain cursor with no header
            // rows, so nothing folds and positions map straight through. The search rebuilds its
            // cursor per keystroke, so a boxed copy of every row there would be paid per character.
            return position
        }
        return if (position in mVisibleRows.indices) mVisibleRows[position] else -1
    }

    override fun getItemCount(): Int {
        // Guarded, because a cursor swapped away for null leaves the rows built for it behind,
        // since the superclass only walks a cursor it actually has.
        val cursor = getCursor()
        if (!isDataValid() || cursor == null) {
            return 0
        }
        return if (mIndexType == -1) cursor.count else mVisibleRows.size
    }

    override fun getItemId(position: Int): Long {
        return super.getItemId(cursorPosition(position))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        super.onBindViewHolder(holder, cursorPosition(position))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, cursor: Cursor) {
        when (holder) {
            is HeaderViewHolder -> onBindHeaderViewHolder(holder, cursor)
            is TransactionViewHolder -> onBindItemViewHolder(holder, cursor)
        }
    }

    private fun onBindItemViewHolder(holder: TransactionViewHolder, cursor: Cursor) {
        val icon: Icon? = IconLoader.parse(cursor.getString(mIndexCategoryIcon))
        IconLoader.loadInto(icon, holder.mAvatarImageView)
        holder.mPrimaryTextView.text = cursor.getString(mIndexCategoryName)
        holder.mSecondaryTextView.text = cursor.getString(mIndexTransactionDescription)
        val currency: CurrencyUnit? = CurrencyManager.getCurrency(cursor.getString(mIndexCurrency))
        val money = cursor.getLong(mIndexTransactionMoney)
        if (cursor.getInt(mIndexTransactionDirection) == Contract.Direction.INCOME) {
            mMoneyFormatter.applyTintedIncome(holder.mMoneyTextView, currency, money)
        } else {
            mMoneyFormatter.applyTintedExpense(holder.mMoneyTextView, currency, money)
        }
        val date = DateUtils.getDateFromSQLDateTimeString(cursor.getString(mIndexTransactionDate))
        DateFormatter.applyDateTime(holder.mDateTextView, date)
        holder.itemView.isActivated = mSelectedIds.contains(cursor.getLong(mIndexTransactionId))
    }

    private fun onBindHeaderViewHolder(holder: HeaderViewHolder, cursor: Cursor) {
        val start = DateUtils.getDateFromSQLDateTimeString(cursor.getString(mIndexHeaderStartDate))
        val end = DateUtils.getDateFromSQLDateTimeString(cursor.getString(mIndexHeaderEndDate))
        val leftTextView = holder.mLeftTextView
        if (leftTextView != null) {
            DateFormatter.applyDateRange(leftTextView, start, end)
        }
        val money = Money.parse(cursor.getString(mIndexHeaderMoney))
        // untinted, because with the plus and minus setting off, which is the default, this
        // class shows a sign on an amount it does not color and leaves the sign off one it
        // does. A tinted difference printed 45.00 for a stretch that spent 45 and earned
        // nothing, and left the color to say which way it went, on a line whose other two
        // figures are colored whatever they hold
        mMoneyFormatter.applyNotTinted(holder.mRightTextView, money)
        val income = Money.parse(cursor.getString(mIndexHeaderIncome))
        val expense = Money.parse(cursor.getString(mIndexHeaderExpense))
        mMoneyFormatter.applyTintedIncome(holder.mIncomeTextView, orZero(income, money))
        mMoneyFormatter.applyTintedExpense(holder.mExpenseTextView, orZero(expense, money))
    }

    private fun applyCodeFont(context: Context, vararg textViews: TextView) {
        try {
            val typeface: Typeface? = ResourcesCompat.getFont(context, R.font.google_sans_code_family)
            if (typeface != null) {
                for (textView in textViews) {
                    textView.typeface = typeface
                }
            }
        } catch (ignore: Exception) {
            // Fallback to default typeface if custom font fails to load
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TransactionHeaderCursor.TYPE_HEADER -> {
                val itemView = inflater.inflate(R.layout.adapter_transaction_header_item, parent, false)
                HeaderViewHolder(itemView)
            }
            TransactionHeaderCursor.TYPE_ITEM -> {
                val itemView = inflater.inflate(R.layout.adapter_transaction_item, parent, false)
                TransactionViewHolder(itemView)
            }
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (mIndexType != -1) {
            val cursor = getSafeCursor(cursorPosition(position))
            if (cursor != null) {
                return cursor.getInt(mIndexType)
            }
        }
        return TransactionHeaderCursor.TYPE_ITEM
    }

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        val mLeftTextView: TextView? = itemView.findViewById(R.id.left_text_view)
        val mRightTextView: TextView = itemView.findViewById(R.id.right_text_view)
        val mIncomeTextView: TextView = itemView.findViewById(R.id.income_text_view)
        val mExpenseTextView: TextView = itemView.findViewById(R.id.expense_text_view)

        init {
            applyHeaderTintBackground(itemView, mHeaderOpensReport)
            applyCodeFont(itemView.context, mRightTextView, mIncomeTextView, mExpenseTextView)
            if (mHeaderOpensReport) {
                itemView.setOnClickListener(this)
            }
            // Allow collapsing/expanding by clicking the left text view (chip)
            mLeftTextView?.setOnClickListener {
                val cursor = getSafeCursor(cursorPosition(adapterPosition))
                if (cursor != null) {
                    val key = periodKey(
                        cursor.getInt(mIndexHeaderGroupType),
                        cursor.getString(mIndexHeaderStartDate)
                    )
                    togglePeriod(key)
                }
            }
        }

        override fun onClick(v: View) {
            val listener = mActionListener ?: return
            val cursor = getSafeCursor(cursorPosition(adapterPosition)) ?: return
            val start = DateUtils.getDateFromSQLDateTimeString(cursor.getString(mIndexHeaderStartDate))
            val end = DateUtils.getDateFromSQLDateTimeString(cursor.getString(mIndexHeaderEndDate))
            listener.onHeaderClick(start, end)
        }
    }

    inner class TransactionViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView), View.OnClickListener, View.OnLongClickListener {

        val mAvatarImageView: ImageView = itemView.findViewById(R.id.avatar_image_view)
        val mPrimaryTextView: TextView = itemView.findViewById(R.id.primary_text_view)
        val mMoneyTextView: TextView = itemView.findViewById(R.id.money_text_view)
        val mSecondaryTextView: TextView = itemView.findViewById(R.id.secondary_text_view)
        val mDateTextView: TextView = itemView.findViewById(R.id.date_text_view)

        init {
            // the ripple color, because it is translucent and so reads on every light and dark
            // background, while an xml attribute would resolve against the light theme only
            val selected = StateListDrawable()
            selected.addState(
                intArrayOf(android.R.attr.state_activated),
                ColorDrawable(ThemeEngine.getTheme().colorRipple)
            )
            val ripple: Drawable? = itemView.background
            itemView.background = if (ripple != null) {
                LayerDrawable(arrayOf(selected, ripple))
            } else {
                selected
            }
            itemView.setOnClickListener(this)
            itemView.setOnLongClickListener(this)
            applyCodeFont(itemView.context, mMoneyTextView)
        }

        override fun onClick(v: View) {
            val listener = mActionListener ?: return
            val cursor = getSafeCursor(cursorPosition(adapterPosition)) ?: return
            if (mSelectedIds.isEmpty()) {
                listener.onTransactionClick(cursor.getLong(mIndexTransactionId))
            } else if (isSelectable(cursor)) {
                val id = cursor.getLong(mIndexTransactionId)
                if (!mSelectedIds.remove(id)) {
                    mSelectedIds.add(id)
                }
                notifySelectionChanged()
            }
        }

        override fun onLongClick(v: View): Boolean {
            val cursor = getSafeCursor(cursorPosition(adapterPosition))
            if (cursor == null || !isSelectable(cursor)) {
                return false
            }
            if (mSelectedIds.add(cursor.getLong(mIndexTransactionId))) {
                notifySelectionChanged()
            }
            return true
        }
    }

    interface ActionListener {
        fun onHeaderClick(startDate: Date, endDate: Date)

        fun onTransactionClick(id: Long)

        fun onSelectionChanged(count: Int)
    }

    companion object {
        /**
         * The key a period is stored under: the grouping it was drawn with, then the date it starts
         * on. The grouping is part of it because a day and the month it opens start on the same date,
         * and folding one must not fold the other when the user changes the grouping and comes back.
         */
        private fun periodKey(groupType: Int, startDate: String?): String {
            return "$groupType:$startDate"
        }

        /**
         * The figure itself, or a zero in the currencies the difference was counted in when the
         * figure holds none of its own. An amount with no currency in it renders as the placeholder
         * for a value nobody knows, and a stretch that only spent has a known zero income.
         *
         * The zero is put here and not into the running totals because a figure that has rows of
         * its own must not gain a currency it has none in, which is what putting it in the totals
         * did. A figure with no rows at all still takes every currency the header counted, so on a
         * header counting two it reads as two zeros and is cut off the way any long figure is.
         */
        @JvmStatic
        fun orZero(money: Money, counted: Money): Money {
            if (money.numberOfCurrencies > 0) {
                return money
            }
            val zero = Money()
            for (currency in counted.currencies) {
                zero.addMoney(currency, 0)
            }
            return zero
        }
    }
}
