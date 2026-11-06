/*
 * Copyright (c) 2018.
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

package com.oriondev.moneywallet.storage.database.model

/**
 * This class acts as a Contract between the Schema of the SQLDatabase and the structure
 * of the exported item.
 */
class Wallet : BaseItem() {
    @JvmField
    var mId: Long? = null

    @JvmField
    var mName: String? = null

    @JvmField
    var mIcon: String? = null

    @JvmField
    var mCurrency: String? = null

    @JvmField
    var mStartMoney: Long = 0L

    @JvmField
    var mCountInTotal: Boolean = false

    @JvmField
    var mArchived: Boolean = false

    @JvmField
    var mNote: String? = null

    @JvmField
    var mTag: String? = null

    @JvmField
    var mIndex: Int = 0
}
