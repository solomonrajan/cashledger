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
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.utils.DateUtils
import java.util.Date

/**
 * Created by andrea on 03/03/18.
 */
class TransferHeaderCursor(
    cursor: Cursor,
    private val mGroup: Group
) : AbstractHeaderCursor<DateRangeHeader>(cursor) {

    init {
        generateHeaders(cursor)
    }

    override fun generateHeaders(cursor: Cursor) {
        val indexTransactionDate = cursor.getColumnIndex(Contract.Transfer.DATE)
        if (cursor.moveToFirst()) {
            var header: DateRangeHeader? = null
            do {
                val dateTime = cursor.getString(indexTransactionDate)
                val date = if (dateTime != null) {
                    DateUtils.getDateFromSQLDateTimeString(dateTime)
                } else {
                    Date()
                }
                if (header == null) {
                    header = DateRangeHeader(mGroup, null, null, date)
                    addHeader(header)
                } else {
                    if (!header.isInBounds(date)) {
                        header = DateRangeHeader(mGroup, null, null, date)
                        addHeader(header)
                    }
                }
                addItem(cursor.position)
            } while (cursor.moveToNext())
        }
    }

    override fun getHeaderColumnNames(): Array<String> {
        return arrayOf(
            COLUMN_ITEM_TYPE,
            COLUMN_HEADER_START_DATE,
            COLUMN_HEADER_END_DATE,
            COLUMN_HEADER_GROUP_TYPE
        )
    }

    override fun getHeaderString(index: Int): String? {
        if (isHeader()) {
            val header = getHeader() ?: return null
            return when (index) {
                INDEX_HEADER_START_DATE -> DateUtils.getSQLDateTimeString(header.startDate)
                INDEX_HEADER_END_DATE -> DateUtils.getSQLDateTimeString(header.endDate)
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

    companion object {
        const val COLUMN_ITEM_TYPE: String = "item_type"
        const val COLUMN_HEADER_START_DATE: String = "header_start_date"
        const val COLUMN_HEADER_END_DATE: String = "header_end_date"
        const val COLUMN_HEADER_GROUP_TYPE: String = "header_group_type"

        const val TYPE_HEADER: Int = 0
        const val TYPE_ITEM: Int = 1

        private const val INDEX_ITEM_TYPE = 0
        private const val INDEX_HEADER_START_DATE = 1
        private const val INDEX_HEADER_END_DATE = 2
        private const val INDEX_HEADER_GROUP_TYPE = 3
    }
}
