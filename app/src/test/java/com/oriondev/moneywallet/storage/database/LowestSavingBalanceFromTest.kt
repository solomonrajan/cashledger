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

import org.junit.Assert.assertEquals
import org.junit.Test

class LowestSavingBalanceFromTest {

    @Test
    fun aSavingWithNoRowsIsWorthItsStartMoney() {
        assertEquals(150000L, Contract.lowestSavingBalanceFrom(150000L, NO_DATES, NO_MONEY, SEPTEMBER_10))
    }

    @Test
    fun aDepositDatedAheadDoesNotPayForAWithdrawalDatedBeforeIt() {
        assertEquals(0L, Contract.lowestSavingBalanceFrom(0L, arrayOf(SEPTEMBER_30), longArrayOf(300000L), SEPTEMBER_10))
    }

    @Test
    fun theSameDepositPaysForAWithdrawalDatedAfterIt() {
        assertEquals(300000L, Contract.lowestSavingBalanceFrom(0L, arrayOf(SEPTEMBER_30), longArrayOf(300000L), OCTOBER_01))
    }

    @Test
    fun aSavingWithMoneyInItOffersOnlyWhatItHoldsOnTheDayAsked() {
        assertEquals(150000L, Contract.lowestSavingBalanceFrom(150000L, arrayOf(SEPTEMBER_30), longArrayOf(300000L), SEPTEMBER_10))
    }

    @Test
    fun aRowDatedExactlyTheMomentAskedAboutIsAlreadyCounted() {
        assertEquals(300000L, Contract.lowestSavingBalanceFrom(0L, arrayOf(SEPTEMBER_30), longArrayOf(300000L), SEPTEMBER_30))
    }

    @Test
    fun aDipAfterTheMomentAskedAboutIsTheAnswer() {
        assertEquals(50000L, Contract.lowestSavingBalanceFrom(0L, arrayOf(SEPTEMBER_10, SEPTEMBER_30), longArrayOf(300000L, -250000L), SEPTEMBER_10))
    }

    @Test
    fun aDipBeforeTheMomentAskedAboutIsNotTheAnswer() {
        assertEquals(150000L, Contract.lowestSavingBalanceFrom(300000L, arrayOf(SEPTEMBER_10, SEPTEMBER_30), longArrayOf(-250000L, 100000L), OCTOBER_01))
    }

    @Test
    fun aDipBetweenTwoLaterRowsIsTheAnswer() {
        assertEquals(100000L, Contract.lowestSavingBalanceFrom(300000L, arrayOf(SEPTEMBER_30, OCTOBER_01), longArrayOf(-200000L, 500000L), SEPTEMBER_10))
    }

    @Test
    fun rowsSharingADateAreCountedTogether() {
        assertEquals(100000L, Contract.lowestSavingBalanceFrom(100000L, arrayOf(SEPTEMBER_30, SEPTEMBER_30), longArrayOf(-150000L, 200000L), SEPTEMBER_10))
        assertEquals(100000L, Contract.lowestSavingBalanceFrom(100000L, arrayOf(SEPTEMBER_30, SEPTEMBER_30), longArrayOf(200000L, -150000L), SEPTEMBER_10))
        assertEquals(-150000L, Contract.lowestSavingBalanceFrom(100000L, arrayOf(SEPTEMBER_30, SEPTEMBER_30), longArrayOf(-150000L, -100000L), SEPTEMBER_10))
    }

    @Test
    fun aSavingAlreadyUnderZeroAnswersBelowZero() {
        assertEquals(-100000L, Contract.lowestSavingBalanceFrom(0L, arrayOf(SEPTEMBER_10), longArrayOf(-100000L), SEPTEMBER_10))
    }

    companion object {
        private val NO_DATES = emptyArray<String>()
        private val NO_MONEY = LongArray(0)

        private const val SEPTEMBER_10 = "2026-09-10 12:00:00"
        private const val SEPTEMBER_30 = "2026-09-30 12:00:00"
        private const val OCTOBER_01 = "2026-10-01 12:00:00"
    }
}
