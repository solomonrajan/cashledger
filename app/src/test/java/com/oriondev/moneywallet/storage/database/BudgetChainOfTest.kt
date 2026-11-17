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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A roll names each period it opens after the chain, a colon, and the day that period begins on,
 * and Contract.budgetChainOf(String) is the one place that reads such a name back.
 */
class BudgetChainOfTest {

    @Test
    fun aBudgetThatStartedItsOwnChainIsItsOwnAnswer() {
        assertEquals(ROOT, Contract.budgetChainOf(ROOT))
    }

    @Test
    fun aPeriodGivesTheChainItWasOpenedFor() {
        assertEquals(ROOT, Contract.budgetChainOf("$ROOT:2026-08-15"))
    }

    @Test
    fun aRootHoldingAColonKeepsAllOfIt() {
        assertEquals("weird:root", Contract.budgetChainOf("weird:root"))
        assertEquals("weird:root", Contract.budgetChainOf("weird:root:2026-08-15"))
    }

    @Test
    fun aRootHoldingAColonStillSortsBeforeItsOwnPeriods() {
        val root = "weird:root"
        val august = Contract.budgetChainOf(root) + ":2026-08-15"
        val september = Contract.budgetChainOf(root) + ":2026-09-15"
        assertTrue(root < august)
        assertTrue(august < september)
    }

    @Test
    fun onlyARealDayIsTakenOff() {
        assertEquals("a:notaday", Contract.budgetChainOf("a:notaday"))
        assertEquals("a:2026-8-15", Contract.budgetChainOf("a:2026-8-15"))
        assertEquals("a:2026-08-15x", Contract.budgetChainOf("a:2026-08-15x"))
        assertEquals("a:", Contract.budgetChainOf("a:"))
    }

    @Test
    fun onlyOneDayIsTakenOff() {
        assertEquals("$ROOT:2026-07-15", Contract.budgetChainOf("$ROOT:2026-07-15:2026-08-15"))
    }

    companion object {
        private const val ROOT = "3f7a1c9e2b4d5a6f"
    }
}
