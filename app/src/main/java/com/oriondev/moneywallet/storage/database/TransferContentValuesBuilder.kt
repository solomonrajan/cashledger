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
 * Single home, outside [SQLDatabase], for the set of `Contract.Transfer`
 * columns that make up a transfer row on the write path (inserts and updates through the
 * content provider). Each column name is written here rather than repeated at every call
 * site.
 *
 * Every setter is optional: a caller sets only the columns it actually provides, so the
 * per-caller column set is preserved. A nullable column keeps its caller's null convention,
 * call the setter (even with a null argument) to write the column, or omit the call to
 * leave it unset.
 */
class TransferContentValuesBuilder {

    private val mValues = ContentValues()

    fun description(value: String?): TransferContentValuesBuilder {
        mValues.put(Contract.Transfer.DESCRIPTION, value)
        return this
    }

    fun date(value: String?): TransferContentValuesBuilder {
        mValues.put(Contract.Transfer.DATE, value)
        return this
    }

    fun fromWalletId(value: Long): TransferContentValuesBuilder {
        mValues.put(Contract.Transfer.TRANSACTION_FROM_WALLET_ID, value)
        return this
    }

    fun toWalletId(value: Long): TransferContentValuesBuilder {
        mValues.put(Contract.Transfer.TRANSACTION_TO_WALLET_ID, value)
        return this
    }

    fun taxWalletId(value: Long): TransferContentValuesBuilder {
        mValues.put(Contract.Transfer.TRANSACTION_TAX_WALLET_ID, value)
        return this
    }

    fun fromMoney(value: Long): TransferContentValuesBuilder {
        mValues.put(Contract.Transfer.TRANSACTION_FROM_MONEY, value)
        return this
    }

    fun toMoney(value: Long): TransferContentValuesBuilder {
        mValues.put(Contract.Transfer.TRANSACTION_TO_MONEY, value)
        return this
    }

    fun taxMoney(value: Long): TransferContentValuesBuilder {
        mValues.put(Contract.Transfer.TRANSACTION_TAX_MONEY, value)
        return this
    }

    fun note(value: String?): TransferContentValuesBuilder {
        mValues.put(Contract.Transfer.NOTE, value)
        return this
    }

    fun placeId(value: Long?): TransferContentValuesBuilder {
        mValues.put(Contract.Transfer.PLACE_ID, value)
        return this
    }

    fun eventId(value: Long?): TransferContentValuesBuilder {
        mValues.put(Contract.Transfer.EVENT_ID, value)
        return this
    }

    fun recurrenceId(value: Long): TransferContentValuesBuilder {
        mValues.put(Contract.Transfer.RECURRENCE_ID, value)
        return this
    }

    // Two confirmed()/countInTotal() overloads exist on purpose: most call sites store a
    // boolean, but the model-to-transfer mapping stores the raw cursor int. Keeping both
    // signatures preserves each site's exact ContentValues value type with no conversion.

    fun confirmed(value: Boolean): TransferContentValuesBuilder {
        mValues.put(Contract.Transfer.CONFIRMED, value)
        return this
    }

    fun confirmed(value: Int): TransferContentValuesBuilder {
        mValues.put(Contract.Transfer.CONFIRMED, value)
        return this
    }

    fun countInTotal(value: Boolean): TransferContentValuesBuilder {
        mValues.put(Contract.Transfer.COUNT_IN_TOTAL, value)
        return this
    }

    fun countInTotal(value: Int): TransferContentValuesBuilder {
        mValues.put(Contract.Transfer.COUNT_IN_TOTAL, value)
        return this
    }

    fun peopleIds(value: String?): TransferContentValuesBuilder {
        mValues.put(Contract.Transfer.PEOPLE_IDS, value)
        return this
    }

    fun attachmentIds(value: String?): TransferContentValuesBuilder {
        mValues.put(Contract.Transfer.ATTACHMENT_IDS, value)
        return this
    }

    fun build(): ContentValues {
        return mValues
    }
}
