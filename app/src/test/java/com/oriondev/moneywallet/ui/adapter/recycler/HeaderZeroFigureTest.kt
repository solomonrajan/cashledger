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

package com.oriondev.moneywallet.ui.adapter.recycler

import com.oriondev.moneywallet.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A stretch that only spent counts no income at all.
 */
class HeaderZeroFigureTest {

    @Test
    fun aFigureWithRowsOfItsOwnIsHandedBackUnchanged() {
        val income = Money(USD, 4500)
        val counted = Money(USD, 4500)
        val result = TransactionCursorAdapter.orZero(income, counted)
        assertEquals(income.currencyMoneys, result.currencyMoneys)
        assertEquals(4500, result.getMoney(USD))
    }

    @Test
    fun anEmptyFigureBecomesAZeroInTheCurrencyThatWasCounted() {
        val counted = Money(USD, -4500)
        val zero = TransactionCursorAdapter.orZero(Money(), counted)
        assertEquals(1, zero.numberOfCurrencies)
        assertTrue(zero.currencies.contains(USD))
        assertEquals(0, zero.getMoney(USD))
    }

    @Test
    fun anEmptyFigureTakesEveryCurrencyTheDifferenceWasCountedIn() {
        val counted = Money(USD, -4500).apply {
            addMoney(EUR, -700)
        }
        val zero = TransactionCursorAdapter.orZero(Money(), counted)
        assertEquals(2, zero.numberOfCurrencies)
        assertTrue(zero.currencies.contains(USD))
        assertTrue(zero.currencies.contains(EUR))
        assertEquals(0, zero.getMoney(USD))
        assertEquals(0, zero.getMoney(EUR))
    }

    @Test
    fun aFigureHoldingOneOfTwoCurrenciesDoesNotGainTheOther() {
        val income = Money(EUR, 70000)
        val counted = Money(USD, -4500).apply {
            addMoney(EUR, 70000)
        }
        val result = TransactionCursorAdapter.orZero(income, counted)
        assertEquals(1, result.numberOfCurrencies)
        assertTrue(result.currencies.contains(EUR))
    }

    @Test
    fun nothingCountedAtAllStaysUnknown() {
        val zero = TransactionCursorAdapter.orZero(Money(), Money())
        assertEquals(0, zero.numberOfCurrencies)
    }

    companion object {
        private const val USD = "USD"
        private const val EUR = "EUR"
    }
}
