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

package com.oriondev.moneywallet.storage.database

import android.content.ContentValues

/**
 * Single home, outside [SQLDatabase], for the set of `Contract.Transaction`
 * columns that make up a transaction row on the write path (inserts and updates through
 * the content provider). Each column name is written here rather than repeated at every
 * call site.
 *
 * Every setter is optional: a caller sets only the columns it actually provides, so the
 * per-caller column set is preserved. A nullable column keeps its caller's null convention,
 * call the setter (even with a null argument) to write the column, or omit the call to
 * leave it unset.
 */
class TransactionContentValuesBuilder {

    private val mValues = ContentValues()

    fun money(value: Long?): TransactionContentValuesBuilder {
        mValues.put(Contract.Transaction.MONEY, value)
        return this
    }

    fun date(value: String?): TransactionContentValuesBuilder {
        mValues.put(Contract.Transaction.DATE, value)
        return this
    }

    fun description(value: String?): TransactionContentValuesBuilder {
        mValues.put(Contract.Transaction.DESCRIPTION, value)
        return this
    }

    fun categoryId(value: Long): TransactionContentValuesBuilder {
        mValues.put(Contract.Transaction.CATEGORY_ID, value)
        return this
    }

    fun direction(value: Int): TransactionContentValuesBuilder {
        mValues.put(Contract.Transaction.DIRECTION, value)
        return this
    }

    fun type(value: Int): TransactionContentValuesBuilder {
        mValues.put(Contract.Transaction.TYPE, value)
        return this
    }

    fun walletId(value: Long): TransactionContentValuesBuilder {
        mValues.put(Contract.Transaction.WALLET_ID, value)
        return this
    }

    fun placeId(value: Long?): TransactionContentValuesBuilder {
        mValues.put(Contract.Transaction.PLACE_ID, value)
        return this
    }

    fun note(value: String?): TransactionContentValuesBuilder {
        mValues.put(Contract.Transaction.NOTE, value)
        return this
    }

    fun eventId(value: Long?): TransactionContentValuesBuilder {
        mValues.put(Contract.Transaction.EVENT_ID, value)
        return this
    }

    fun savingId(value: Long?): TransactionContentValuesBuilder {
        mValues.put(Contract.Transaction.SAVING_ID, value)
        return this
    }

    fun debtId(value: Long?): TransactionContentValuesBuilder {
        mValues.put(Contract.Transaction.DEBT_ID, value)
        return this
    }

    fun recurrenceId(value: Long): TransactionContentValuesBuilder {
        mValues.put(Contract.Transaction.RECURRENCE_ID, value)
        return this
    }

    // Two confirmed()/countInTotal() overloads exist on purpose: most call sites store a
    // boolean, but the model-to-transaction mapping stores the raw cursor int. Keeping both
    // signatures preserves each site's exact ContentValues value type with no conversion.

    fun confirmed(value: Boolean): TransactionContentValuesBuilder {
        mValues.put(Contract.Transaction.CONFIRMED, value)
        return this
    }

    fun confirmed(value: Int): TransactionContentValuesBuilder {
        mValues.put(Contract.Transaction.CONFIRMED, value)
        return this
    }

    fun countInTotal(value: Boolean): TransactionContentValuesBuilder {
        mValues.put(Contract.Transaction.COUNT_IN_TOTAL, value)
        return this
    }

    fun countInTotal(value: Int): TransactionContentValuesBuilder {
        mValues.put(Contract.Transaction.COUNT_IN_TOTAL, value)
        return this
    }

    fun peopleIds(value: String?): TransactionContentValuesBuilder {
        mValues.put(Contract.Transaction.PEOPLE_IDS, value)
        return this
    }

    fun attachmentIds(value: String?): TransactionContentValuesBuilder {
        mValues.put(Contract.Transaction.ATTACHMENT_IDS, value)
        return this
    }

    fun build(): ContentValues {
        return mValues
    }
}
