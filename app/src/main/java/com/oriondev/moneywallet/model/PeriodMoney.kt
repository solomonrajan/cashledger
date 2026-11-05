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

import com.oriondev.moneywallet.utils.DateUtils
import java.util.Date

/**
 * Created by andrea on 14/08/18.
 */
class PeriodMoney(
    val startDate: Date,
    val endDate: Date
) {

    val incomes: Money = Money()
    val expenses: Money = Money()
    val netIncomes: Money = Money()

    fun addIncome(currency: String, money: Long) {
        incomes.addMoney(currency, money)
        netIncomes.addMoney(currency, money)
    }

    fun addExpense(currency: String, money: Long) {
        expenses.addMoney(currency, money)
        netIncomes.removeMoney(currency, money)
    }

    fun isTimeRange(): Boolean {
        return DateUtils.isTheSameDayAndHour(startDate, endDate)
    }
}
