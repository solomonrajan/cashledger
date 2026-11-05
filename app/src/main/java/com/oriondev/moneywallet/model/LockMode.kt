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

package com.oriondev.moneywallet.model

import com.oriondev.moneywallet.storage.preference.PreferenceManager

/**
 * Created by andrea on 24/07/18.
 */
enum class LockMode(val value: Int) {
    NONE(PreferenceManager.LOCK_MODE_NONE),
    PIN(PreferenceManager.LOCK_MODE_PIN),
    SEQUENCE(PreferenceManager.LOCK_MODE_SEQUENCE),
    FINGERPRINT(PreferenceManager.LOCK_MODE_FINGERPRINT);

    val valueAsString: String
        get() = value.toString()

    companion object {
        @JvmStatic
        fun get(value: Int): LockMode {
            return when (value) {
                PreferenceManager.LOCK_MODE_PIN -> PIN
                PreferenceManager.LOCK_MODE_SEQUENCE -> SEQUENCE
                PreferenceManager.LOCK_MODE_FINGERPRINT -> FINGERPRINT
                else -> NONE
            }
        }
    }
}

