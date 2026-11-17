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

import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri

// the database refuses no plain transaction on update, so a refusal test needs one refused on purpose
class RefusingDataContentProvider : DataContentProvider() {

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        if (ContentUris.parseId(uri) == sRefusedId) {
            throw SQLiteDataException(Contract.ErrorCode.TRANSACTION_USED_IN_TRANSFER, "refused on purpose")
        }
        return super.update(uri, values, selection, selectionArgs)
    }

    companion object {
        @JvmField
        var sRefusedId: Long = 0L

        @JvmStatic
        fun authority(): String = AUTHORITY
    }
}
