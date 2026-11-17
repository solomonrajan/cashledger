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

package com.oriondev.moneywallet.ui.view.text

import android.content.Context
import androidx.annotation.StringRes

class NonEmptyTextValidator : Validator {

    private val mMessage: String
    private val mAuto: Boolean

    @JvmOverloads
    constructor(message: String, auto: Boolean = false) {
        mMessage = message
        mAuto = auto
    }

    @JvmOverloads
    constructor(context: Context, @StringRes resId: Int, auto: Boolean = false) {
        mMessage = context.getString(resId)
        mAuto = auto
    }

    override fun getErrorMessage(): String = mMessage

    override fun isValid(charSequence: CharSequence): Boolean {
        return charSequence.toString().trim().isNotEmpty()
    }

    override fun autoValidate(): Boolean = mAuto
}
