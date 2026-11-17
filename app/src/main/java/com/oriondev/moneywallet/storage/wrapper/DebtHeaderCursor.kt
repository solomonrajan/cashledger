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
import com.oriondev.moneywallet.model.Money
import com.oriondev.moneywallet.storage.database.Contract
import kotlin.math.abs
import kotlin.math.max

/**
 * Created by andrea on 05/03/18.
 */
class DebtHeaderCursor(
    cursor: Cursor,
    private val mDebtType: Contract.DebtType?
) : AbstractHeaderCursor<DebtHeaderCursor.Header>(cursor) {

    private val mIndexDebtType: Int

    init {
        generateHeaders(cursor)
        mIndexDebtType = getHeaderColumnNames().size + cursor.getColumnIndex(Contract.Debt.TYPE)
    }

    override fun generateHeaders(cursor: Cursor) {
        val indexDebtCurrency = cursor.getColumnIndex(Contract.Debt.WALLET_CURRENCY)
        val indexDebtMoney = cursor.getColumnIndex(Contract.Debt.MONEY)
        val indexDebtProgress = cursor.getColumnIndex(Contract.Debt.PROGRESS)
        val indexIsSettled = cursor.getColumnIndex(COLUMN_IS_SETTLED)
        if (cursor.moveToFirst()) {
            var header: Header? = null
            do {
                val settled = cursor.getInt(indexIsSettled) == 1
                if (header != null) {
                    // check the header state
                    if (!settled && header.type == 1) {
                        throw IllegalStateException("SQL query has failed to sort the items.")
                    }
                    if (header.type == 0 && settled) {
                        // we can store the previous header and create a new one
                        header = Header(HEADER_ARCHIVED)
                        addHeader(header)
                    }
                } else {
                    // initialize the header based on current item
                    header = Header(if (!settled) HEADER_CURRENT else HEADER_ARCHIVED)
                    addHeader(header)
                }
                addItem(cursor.position)
                // if current header than sum the remaining money
                if (!settled) {
                    val currency = cursor.getString(indexDebtCurrency)
                    if (currency != null) {
                        val money = cursor.getLong(indexDebtMoney)
                        val progress = cursor.getLong(indexDebtProgress)
                        // clamped, not merely expected to be positive: a debt saved for no money is
                        // treated as unsettled and can still have payments against it, and paying more
                        // than is owed must not subtract from what is owed on everything else
                        val modulus = max(0L, abs(money) - abs(progress))
                        header.addMoney(currency, modulus)
                    }
                }
            } while (cursor.moveToNext())
        }
    }

    override fun getHeaderColumnNames(): Array<String> {
        return arrayOf(
            COLUMN_ITEM_TYPE,
            COLUMN_HEADER_TYPE,
            COLUMN_HEADER_MONEY
        )
    }

    override fun getHeaderString(index: Int): String? {
        if (isHeader()) {
            when (index) {
                INDEX_HEADER_MONEY -> {
                    val header = getHeader()
                    return header?.money?.toString()
                }
            }
        }
        return null
    }

    override fun getHeaderShort(index: Int): Short = 0

    override fun getInt(index: Int): Int {
        if (index == mIndexDebtType) {
            return mDebtType?.value ?: 0
        }
        return super.getInt(index)
    }

    override fun getHeaderInt(index: Int): Int {
        return when (index) {
            INDEX_ITEM_TYPE -> if (isHeader()) TYPE_HEADER else TYPE_ITEM
            INDEX_HEADER_TYPE -> getHeader()?.type ?: 0
            else -> 0
        }
    }

    override fun getHeaderLong(index: Int): Long = 0L

    override fun getHeaderFloat(index: Int): Float = 0f

    override fun getHeaderDouble(index: Int): Double = 0.0

    override fun isHeaderNull(index: Int): Boolean = false

    class Header(val type: Int) {
        val money: Money = Money()

        fun addMoney(currency: String, money: Long) {
            this.money.addMoney(currency, money)
        }
    }

    companion object {
        const val COLUMN_ITEM_TYPE: String = "item_type"
        const val COLUMN_HEADER_TYPE: String = "header_type"
        const val COLUMN_HEADER_MONEY: String = "header_money"

        const val INDEX_ITEM_TYPE: Int = 0
        const val INDEX_HEADER_TYPE: Int = 1
        const val INDEX_HEADER_MONEY: Int = 2

        const val TYPE_HEADER: Int = 0
        const val TYPE_ITEM: Int = 1

        const val HEADER_CURRENT: Int = 0
        const val HEADER_ARCHIVED: Int = 1

        /**
         * A debt counts as finished once it has been archived by hand or paid off in full. Archiving
         * was the only thing the app treated as finished, which left a debt you had finished paying
         * sitting in the group that still needs attention.
         *
         * The query selects this expression under [COLUMN_IS_SETTLED] and orders by it, and the
         * grouping below reads that same column back rather than recomputing the rule. Recomputing it
         * in Java would be a second copy that could drift, and this class throws when the grouping
         * disagrees with the order, so the drift would surface as a crash rather than a mis-sort.
         *
         * A debt for no money is not finished, it is unfinished data entry. The amount is not required
         * when saving, so one can be created by accident, and filing it away as finished is how it
         * would be lost.
         */
        const val SQL_IS_SETTLED: String =
            "(CASE WHEN " + Contract.Debt.ARCHIVED + " = 1 OR " +
                "(" + Contract.Debt.MONEY + " <> 0 AND " +
                "ABS(COALESCE(" + Contract.Debt.PROGRESS + ", 0)) >= ABS(" + Contract.Debt.MONEY + "))" +
                " THEN 1 ELSE 0 END)"

        const val COLUMN_IS_SETTLED: String = "debt_is_settled"
    }
}
