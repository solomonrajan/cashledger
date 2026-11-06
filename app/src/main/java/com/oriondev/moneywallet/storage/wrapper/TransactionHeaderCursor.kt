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

package com.oriondev.moneywallet.storage.wrapper

import android.database.Cursor
import com.oriondev.moneywallet.model.Group
import com.oriondev.moneywallet.model.Money
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.utils.DateUtils
import java.util.Date

/**
 * Created by andrea on 03/03/18.
 */
class TransactionHeaderCursor(
    cursor: Cursor,
    private val mGroup: Group,
    private val mLowerBound: Date?,
    private val mUpperBound: Date?
) : AbstractHeaderCursor<TransactionHeaderCursor.Header>(cursor) {

    init {
        generateHeaders(cursor)
    }

    override fun generateHeaders(cursor: Cursor) {
        val indexTransactionDirection = cursor.getColumnIndex(Contract.Transaction.DIRECTION)
        val indexTransactionDate = cursor.getColumnIndex(Contract.Transaction.DATE)
        val indexTransactionMoney = cursor.getColumnIndex(Contract.Transaction.MONEY)
        val indexCurrency = cursor.getColumnIndex(Contract.Transaction.WALLET_CURRENCY)
        val indexTransactionConfirmed = cursor.getColumnIndex(Contract.Transaction.CONFIRMED)
        val indexTransactionCountInTotal = cursor.getColumnIndex(Contract.Transaction.COUNT_IN_TOTAL)
        if (cursor.moveToFirst()) {
            var header: Header? = null
            do {
                val dateTime = cursor.getString(indexTransactionDate)
                val date = if (dateTime != null) {
                    DateUtils.getDateFromSQLDateTimeString(dateTime)
                } else {
                    Date()
                }
                if (header == null) {
                    header = Header(mGroup, mLowerBound, mUpperBound, date)
                    addHeader(header)
                } else {
                    if (!header.isInBounds(date)) {
                        header = Header(mGroup, mLowerBound, mUpperBound, date)
                        addHeader(header)
                    }
                }
                addItem(cursor.position)
                if (cursor.getInt(indexTransactionConfirmed) == 1 && cursor.getInt(indexTransactionCountInTotal) == 1) {
                    val currency = cursor.getString(indexCurrency)
                    if (currency != null) {
                        val money = cursor.getLong(indexTransactionMoney)
                        val direction = cursor.getInt(indexTransactionDirection)
                        header.add(currency, money, direction)
                    }
                }
            } while (cursor.moveToNext())
        }
    }

    override fun getHeaderColumnNames(): Array<String> {
        return arrayOf(
            COLUMN_ITEM_TYPE,
            COLUMN_HEADER_START_DATE,
            COLUMN_HEADER_END_DATE,
            COLUMN_HEADER_MONEY,
            COLUMN_HEADER_INCOME,
            COLUMN_HEADER_EXPENSE,
            COLUMN_HEADER_GROUP_TYPE
        )
    }

    override fun getHeaderString(index: Int): String? {
        if (isHeader()) {
            val header = getHeader() ?: return null
            return when (index) {
                INDEX_HEADER_START_DATE -> DateUtils.getSQLDateTimeString(header.startDate)
                INDEX_HEADER_END_DATE -> DateUtils.getSQLDateTimeString(header.endDate)
                INDEX_HEADER_MONEY -> header.money.toString()
                INDEX_HEADER_INCOME -> header.income.toString()
                INDEX_HEADER_EXPENSE -> header.expense.toString()
                else -> null
            }
        }
        return null
    }

    override fun getHeaderShort(index: Int): Short = 0

    override fun getHeaderInt(index: Int): Int {
        return when (index) {
            INDEX_ITEM_TYPE -> if (isHeader()) TYPE_HEADER else TYPE_ITEM
            INDEX_HEADER_GROUP_TYPE -> mGroup.type
            else -> 0
        }
    }

    override fun getHeaderLong(index: Int): Long = 0L

    override fun getHeaderFloat(index: Int): Float = 0f

    override fun getHeaderDouble(index: Int): Double = 0.0

    override fun isHeaderNull(index: Int): Boolean = false

    class Header(
        group: Group,
        lowerBound: Date?,
        upperBound: Date?,
        date: Date
    ) : DateRangeHeader(group, lowerBound, upperBound, date) {

        val money: Money = Money()
        val income: Money = Money()
        val expense: Money = Money()

        /**
         * Count one row into this header. The total is the difference and can come out either
         * sign. The other two are the same rows split by their direction and both only ever
         * grow, so a row adds to one of them and leaves the other alone, while the total takes
         * that same amount as a plus or a minus.
         *
         * Direction is all that is read here, so the two halves of a transfer land in both, and a
         * debt taken on lands in the incoming one. PeriodDetailSummaryLoader, the report a reader
         * reaches from this header, splits a row the same way, so a row lands on the same side in
         * both. It counts fewer rows than this does, since it drops a category kept out of the
         * reports and a header counts every row on the list under it.
         */
        fun add(currency: String, money: Long, direction: Int) {
            if (direction == Contract.Direction.INCOME) {
                income.addMoney(currency, money)
                this.money.addMoney(currency, money)
            } else {
                expense.addMoney(currency, money)
                this.money.addMoney(currency, -money)
            }
        }
    }

    companion object {
        const val COLUMN_ITEM_TYPE: String = "item_type"
        const val COLUMN_HEADER_START_DATE: String = "header_start_date"
        const val COLUMN_HEADER_END_DATE: String = "header_end_date"
        const val COLUMN_HEADER_MONEY: String = "header_money"
        const val COLUMN_HEADER_INCOME: String = "header_income"
        const val COLUMN_HEADER_EXPENSE: String = "header_expense"
        const val COLUMN_HEADER_GROUP_TYPE: String = "header_group_type"

        const val TYPE_HEADER: Int = 0
        const val TYPE_ITEM: Int = 1

        private const val INDEX_ITEM_TYPE = 0
        private const val INDEX_HEADER_START_DATE = 1
        private const val INDEX_HEADER_END_DATE = 2
        private const val INDEX_HEADER_MONEY = 3
        private const val INDEX_HEADER_INCOME = 4
        private const val INDEX_HEADER_EXPENSE = 5
        private const val INDEX_HEADER_GROUP_TYPE = 6
    }
}
