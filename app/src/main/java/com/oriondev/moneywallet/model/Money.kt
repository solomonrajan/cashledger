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

import android.text.TextUtils
import java.util.HashMap
import java.util.Locale

/**
 * Created by andrea on 23/01/18.
 */
class Money {

    private val mCurrencies: MutableMap<String, Long>

    constructor() {
        mCurrencies = HashMap()
    }

    constructor(currency: String, money: Long) {
        mCurrencies = HashMap()
        mCurrencies[currency] = money
    }

    fun addMoney(currency: String, money: Long) {
        var total = money
        val current = mCurrencies[currency]
        if (current != null) {
            total += current
        }
        mCurrencies[currency] = total
    }

    fun addMoney(money: Money?) {
        if (money != null) {
            for ((key, value) in money.mCurrencies) {
                addMoney(key, value)
            }
        }
    }

    fun removeMoney(currency: String, money: Long) {
        var total = -money
        val current = mCurrencies[currency]
        if (current != null) {
            total += current
        }
        mCurrencies[currency] = total
    }

    fun removeMoney(money: Money?) {
        if (money != null) {
            for ((key, value) in money.mCurrencies) {
                removeMoney(key, value)
            }
        }
    }

    fun getMoney(currency: String): Long {
        return mCurrencies[currency] ?: 0L
    }

    val currencyMoneys: Map<String, Long>
        get() = mCurrencies

    val currencies: Set<String>
        get() = mCurrencies.keys

    val numberOfCurrencies: Int
        get() = mCurrencies.size

    override fun toString(): String {
        val builder = StringBuilder()
        for ((key, value) in mCurrencies) {
            if (builder.isNotEmpty()) {
                builder.append(",")
            }
            builder.append(String.format(Locale.ENGLISH, "%s %d", key, value))
        }
        return builder.toString()
    }

    companion object {
        @JvmStatic
        fun empty(): Money = Money()

        @JvmStatic
        fun parse(string: String?): Money {
            val money = Money()
            if (!string.isNullOrEmpty()) {
                val currencies = string.split(",")
                for (currency in currencies) {
                    val parts = currency.split(" ")
                    if (parts.size >= 2) {
                        val amount = parts[1].toLongOrNull() ?: 0L
                        money.addMoney(parts[0], amount)
                    }
                }
            }
            return money
        }
    }
}
