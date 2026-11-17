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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal
import kotlin.math.abs

/**
 * Pure JVM tests for canonical money scaling arithmetic.
 */
class MoneyScaleTest {

    @Test
    fun roundTrip_zeroDecimalCurrency() {
        assertRoundTrip(0L, 0)
        assertRoundTrip(64L, 0)
        assertRoundTrip(-64L, 0)
        assertRoundTrip(123456789L, 0)
    }

    @Test
    fun roundTrip_twoDecimalCurrency() {
        assertRoundTrip(0L, 2)
        assertRoundTrip(6499L, 2)
        assertRoundTrip(-6499L, 2)
        assertRoundTrip(100L, 2)
        assertRoundTrip(-1L, 2)
    }

    @Test
    fun roundTrip_threeDecimalCurrency() {
        assertRoundTrip(0L, 3)
        assertRoundTrip(64999L, 3)
        assertRoundTrip(-64999L, 3)
        assertRoundTrip(1L, 3)
    }

    @Test
    fun roundTrip_largeValueNearDoublePrecisionEdge() {
        val edge = 9007199254740993L
        assertRoundTrip(edge, 0)
        assertRoundTrip(edge, 2)
        assertRoundTrip(-edge, 3)
        assertRoundTrip(Long.MAX_VALUE, 0)
    }

    @Test
    fun toHumanAmount_producesExactDecimalString() {
        assertEquals("64.99", MoneyScale.toHumanAmount(6499L, 2).toPlainString())
        assertEquals("-64.99", MoneyScale.toHumanAmount(-6499L, 2).toPlainString())
        assertEquals("0.001", MoneyScale.toHumanAmount(1L, 3).toPlainString())
        assertEquals("64", MoneyScale.toHumanAmount(64L, 0).toPlainString())
        assertEquals("9007199254740993", MoneyScale.toHumanAmount(9007199254740993L, 0).toPlainString())
    }

    @Test
    fun toMinorUnits_truncatesExtraFractionTowardZero() {
        assertEquals(6499L, MoneyScale.toMinorUnits(BigDecimal("64.999"), 2))
        assertEquals(-6499L, MoneyScale.toMinorUnits(BigDecimal("-64.999"), 2))
        assertEquals(6490L, MoneyScale.toMinorUnits(BigDecimal("64.9"), 2))
        assertEquals(64L, MoneyScale.toMinorUnits(BigDecimal("64.9"), 0))
        assertEquals(-64L, MoneyScale.toMinorUnits(BigDecimal("-64.9"), 0))
    }

    @Test
    fun toMinorUnits_nullAmountIsZero() {
        assertEquals(0L, MoneyScale.toMinorUnits(null, 2))
    }

    @Test
    fun toMinorUnitsRounded_roundsToTheNearestMinorUnit() {
        assertEquals(1002L, MoneyScale.toMinorUnitsRounded(BigDecimal("10.019"), 2))
        assertEquals(1001L, MoneyScale.toMinorUnitsRounded(BigDecimal("10.011"), 2))
        assertEquals(-1002L, MoneyScale.toMinorUnitsRounded(BigDecimal("-10.019"), 2))
        assertEquals(65L, MoneyScale.toMinorUnitsRounded(BigDecimal("64.9"), 0))
    }

    @Test
    fun toMinorUnitsRounded_sendsAHalfToTheEvenNeighbour() {
        assertEquals(1002L, MoneyScale.toMinorUnitsRounded(BigDecimal("10.015"), 2))
        assertEquals(1002L, MoneyScale.toMinorUnitsRounded(BigDecimal("10.025"), 2))
        assertEquals(-1002L, MoneyScale.toMinorUnitsRounded(BigDecimal("-10.025"), 2))
    }

    @Test
    fun toMinorUnitsRounded_usesTheDecimalsItIsGiven() {
        assertEquals(10016L, MoneyScale.toMinorUnitsRounded(BigDecimal("10.0155"), 3))
        assertEquals(64L, MoneyScale.toMinorUnitsRounded(BigDecimal("64.5"), 0))
        assertEquals(66L, MoneyScale.toMinorUnitsRounded(BigDecimal("65.5"), 0))
    }

    @Test(expected = ArithmeticException::class)
    fun toMinorUnitsRounded_refusesAnAmountTooLargeToHold() {
        MoneyScale.toMinorUnitsRounded(BigDecimal("92233720368547758.075"), 2)
    }

    @Test
    fun toMinorUnitsRounded_leavesAnAmountTheCurrencyHoldsAlone() {
        assertEquals(1001L, MoneyScale.toMinorUnitsRounded(BigDecimal("10.01"), 2))
        assertEquals(0L, MoneyScale.toMinorUnitsRounded(BigDecimal.ZERO, 2))
        assertEquals(1234567L, MoneyScale.toMinorUnitsRounded(BigDecimal("12345.67"), 2))
    }

    @Test
    fun toMinorUnitsRounded_nullAmountIsZero() {
        assertEquals(0L, MoneyScale.toMinorUnitsRounded(null, 2))
    }

    @Test
    fun convert_zeroDecimalToTwoDecimal() {
        assertEquals(68000L, MoneyScale.convert(100000L, 0, 2, 0.0068))
    }

    @Test
    fun convert_twoDecimalToZeroDecimal() {
        assertEquals(99960L, MoneyScale.convert(68000L, 2, 0, 147.0))
    }

    @Test
    fun convert_appliesRateExactlyOnce() {
        assertEquals(1000L, MoneyScale.convert(500L, 2, 2, 2.0))
    }

    @Test
    fun convert_sameCurrencyUnitRateIsIdentity() {
        assertEquals(1234L, MoneyScale.convert(1234L, 2, 2, 1.0))
        assertEquals(0L, MoneyScale.convert(0L, 3, 3, 1.0))
    }

    @Test
    fun rescale_matchesOldDoublePathWhereDoubleWasExact() {
        assertEquals(6490L, MoneyScale.rescale(649L, 1))
        assertEquals(64900L, MoneyScale.rescale(649L, 2))
        assertEquals(64L, MoneyScale.rescale(649L, -1))
        assertEquals(-64L, MoneyScale.rescale(-649L, -1))
        assertEquals(0L, MoneyScale.rescale(1234L, 0) - 1234L)
    }

    @Test
    fun rescale_isExactWhereOldDoublePathCorrupted() {
        val money = 9007199254740993L
        val oldDoublePath = (money.toDouble() * Math.pow(10.0, 0.0)).toLong()
        assertEquals(9007199254740992L, oldDoublePath)
        assertEquals(money, MoneyScale.rescale(money, 0))
        assertNotEquals(oldDoublePath, MoneyScale.rescale(money, 0))
    }

    @Test
    fun csvImporter_canonicalMatchesOldPathForSupportedDecimals() {
        val amounts = arrayOf("0", "12345.67", "-0.99", "1000000.5", "70.07")
        for (d in 0..3) {
            for (amount in amounts) {
                val money = BigDecimal(amount)
                val oldPath = money.multiply(BigDecimal(Math.pow(10.0, d.toDouble()))).toLong()
                val canonical = MoneyScale.toMinorUnits(money, d)
                assertEquals("amount=$amount decimals=$d", oldPath, canonical)
            }
        }
        assertEquals(1234567L, MoneyScale.toMinorUnits(BigDecimal("12345.67"), 2))
    }

    @Test
    fun csvImporter_doubleConstructorMultiplierIsCorruptWhereCanonicalIsExact() {
        val amount = BigDecimal("1")
        val buggyMultiplier = BigDecimal(Math.pow(10.0, 23.0))
        val buggyResult = amount.multiply(buggyMultiplier)
        val canonicalResult = amount.movePointRight(23)
        val exact = BigDecimal.TEN.pow(23)

        assertNotEquals(exact, buggyResult)
        assertTrue(buggyResult.compareTo(BigDecimal("99999999999999991611392")) == 0)
        assertTrue(canonicalResult.compareTo(exact) == 0)
    }

    @Test
    fun deriveRate_thenConvert_leavesTheStoredAmountWhereItWas() {
        val storedPairs = arrayOf(
            longArrayOf(10000L, 15000L),
            longArrayOf(15000L, 10000L),
            longArrayOf(6499L, 5832L),
            longArrayOf(100000L, 92L),
            longArrayOf(7L, 900000L)
        )
        for (fromDecimals in 0..3) {
            for (toDecimals in 0..3) {
                for (pair in storedPairs) {
                    val from = pair[0]
                    val to = pair[1]
                    val where = "from=$from to=$to fromDecimals=$fromDecimals toDecimals=$toDecimals"

                    val rate = MoneyScale.deriveRate(from, to, fromDecimals, toDecimals)
                    val resaved = MoneyScale.convert(from, fromDecimals, toDecimals, rate)
                    assertTrue(where + " moved to " + resaved, abs(resaved - to) <= 1L)

                    if (fromDecimals != toDecimals) {
                        val oldRate = to.toDouble() / from.toDouble()
                        val oldResaved = MoneyScale.convert(from, fromDecimals, toDecimals, oldRate)
                        assertTrue(where + " old path happened to be right", abs(oldResaved - to) > 1L)
                    }
                }
            }
        }
    }

    @Test
    fun deriveRate_matchesTheOldRatioWhenBothScalesAgree() {
        for (decimals in 0..3) {
            assertEquals(5832.0 / 6499.0, MoneyScale.deriveRate(6499L, 5832L, decimals, decimals), 0.0)
        }
    }

    @Test
    fun deriveRate_appliesTheScaleShiftInTheDirectionConvertUndoes() {
        assertEquals(150.0, MoneyScale.deriveRate(10000L, 15000L, 2, 0), 1e-9)
        assertEquals(1.5, MoneyScale.deriveRate(10000L, 15000L, 2, 2), 1e-9)
        assertEquals(0.015, MoneyScale.deriveRate(10000L, 15000L, 0, 2), 1e-9)
    }

    @Test
    fun deriveRate_zeroSourceAmountYieldsNoRateRatherThanANonFiniteOne() {
        assertEquals(0.0, MoneyScale.deriveRate(0L, 15000L, 2, 0), 0.0)
        assertEquals(0.0, MoneyScale.deriveRate(0L, 0L, 2, 2), 0.0)
    }

    @Test
    fun deriveRate_guardIsLoadBearingBecauseBigDecimalRejectsNonFiniteRates() {
        assertTrue((15000.0 / 0.0).isInfinite())
        assertTrue((0.0 / 0.0).isNaN())
        for (nonFinite in doubleArrayOf(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN)) {
            try {
                BigDecimal.valueOf(nonFinite)
                fail("BigDecimal.valueOf accepted $nonFinite")
            } catch (expected: NumberFormatException) {
                // this is why deriveRate must not return it
            }
        }
    }

    companion object {
        private fun assertRoundTrip(minorUnits: Long, decimals: Int) {
            val human = MoneyScale.toHumanAmount(minorUnits, decimals)
            assertEquals(
                "round trip minorUnits=$minorUnits decimals=$decimals",
                minorUnits,
                MoneyScale.toMinorUnits(human, decimals)
            )
        }
    }
}
