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
 * Created by andrea on 27/10/18.
 */
class RecurrentTransaction : BaseItem() {
    @JvmField
    var mId: Long? = null

    @JvmField
    var mMoney: Long = 0L

    @JvmField
    var mDescription: String? = null

    @JvmField
    var mCategory: Long? = null

    @JvmField
    var mDirection: Int = 0

    @JvmField
    var mWallet: Long? = null

    @JvmField
    var mPlace: Long? = null

    @JvmField
    var mNote: String? = null

    @JvmField
    var mEvent: Long? = null

    @JvmField
    var mConfirmed: Boolean = false

    @JvmField
    var mCountInTotal: Boolean = false

    @JvmField
    var mStartDate: String? = null

    @JvmField
    var mLastOccurrence: String? = null

    @JvmField
    var mNextOccurrence: String? = null

    @JvmField
    var mRule: String? = null

    @JvmField
    var mTag: String? = null
}
