/*
 * Copyright (c) 2026.
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

import com.oriondev.moneywallet.model.Group
import com.oriondev.moneywallet.storage.database.Contract
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

/**
 * A group header on the transactions list carries three figures now: what came in, what went out,
 * and the difference.
 */
class TransactionHeaderMoneyTest {

    private fun header(): TransactionHeaderCursor.Header {
        val date = Date(0)
        return TransactionHeaderCursor.Header(Group.DAILY, null, null, date)
    }

    @Test
    fun incomeAndExpenseAreBothPositiveAndTheTotalIsTheDifference() {
        val header = header()
        header.add(USD, 120000, Contract.Direction.INCOME)
        header.add(USD, 45000, Contract.Direction.INCOME)
        header.add(USD, 60000, Contract.Direction.EXPENSE)
        header.add(USD, 4000, Contract.Direction.EXPENSE)
        assertEquals(165000, header.income.getMoney(USD))
        assertEquals(64000, header.expense.getMoney(USD))
        assertEquals(101000, header.money.getMoney(USD))
    }

    @Test
    fun spendingMoreThanCameInLeavesTheTotalNegativeAndTheOtherTwoUntouched() {
        val header = header()
        header.add(USD, 1000, Contract.Direction.INCOME)
        header.add(USD, 3000, Contract.Direction.EXPENSE)
        assertEquals(1000, header.income.getMoney(USD))
        assertEquals(3000, header.expense.getMoney(USD))
        assertEquals(-2000, header.money.getMoney(USD))
    }

    @Test
    fun aHeaderOfExpensesOnlyLeavesTheIncomeHoldingNothing() {
        val header = header()
        header.add(USD, 4500, Contract.Direction.EXPENSE)
        assertEquals(0, header.income.numberOfCurrencies)
        assertEquals(4500, header.expense.getMoney(USD))
        assertEquals(-4500, header.money.getMoney(USD))
    }

    @Test
    fun aHeaderOfIncomeOnlyLeavesTheExpenseHoldingNothing() {
        val header = header()
        header.add(USD, 4500, Contract.Direction.INCOME)
        assertEquals(0, header.expense.numberOfCurrencies)
        assertEquals(4500, header.income.getMoney(USD))
        assertEquals(4500, header.money.getMoney(USD))
    }

    @Test
    fun currenciesAreKeptApart() {
        val header = header()
        header.add(USD, 1000, Contract.Direction.INCOME)
        header.add("EUR", 700, Contract.Direction.EXPENSE)
        assertEquals(1000, header.income.getMoney(USD))
        assertEquals(0, header.income.getMoney("EUR"))
        assertEquals(700, header.expense.getMoney("EUR"))
        assertEquals(1000, header.money.getMoney(USD))
        assertEquals(-700, header.money.getMoney("EUR"))
    }

    companion object {
        private const val USD = "USD"
    }
}
